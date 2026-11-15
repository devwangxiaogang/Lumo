package com.yuguang.lumo.data

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.yuguang.lumo.R
import com.yuguang.lumo.data.local.AttachmentDao
import com.yuguang.lumo.data.local.AttachmentEntity
import com.yuguang.lumo.data.local.ChatMediaStore
import com.yuguang.lumo.data.local.ConversationDao
import com.yuguang.lumo.data.local.ConversationEntity
import com.yuguang.lumo.data.local.ConversationSummaryRow
import com.yuguang.lumo.data.local.MessageDao
import com.yuguang.lumo.data.local.MessageEntity
import com.yuguang.lumo.data.remote.DoubaoProvider
import com.yuguang.lumo.data.remote.SeedreamClient
import com.yuguang.lumo.di.AppScope
import com.yuguang.lumo.domain.ChatDraft
import com.yuguang.lumo.domain.ChatRepository
import com.yuguang.lumo.domain.LlmProvider
import com.yuguang.lumo.domain.ModelCatalogRepository
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.AttachmentSource
import com.yuguang.lumo.domain.model.BranchPath
import com.yuguang.lumo.domain.model.ChatMessage
import com.yuguang.lumo.domain.model.ChatRequest
import com.yuguang.lumo.domain.model.Conversation
import com.yuguang.lumo.domain.model.MessageAttachment
import com.yuguang.lumo.domain.model.MessageStatus
import com.yuguang.lumo.domain.model.ProviderConfig
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.domain.model.StreamEvent
import com.yuguang.lumo.domain.model.ToolCallRequest
import com.yuguang.lumo.domain.model.WireMessage
import com.yuguang.lumo.domain.model.freeFallbackChain
import com.yuguang.lumo.domain.model.historyStartIndex
import com.yuguang.lumo.domain.model.overlayStreaming
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao,
    private val mediaStore: ChatMediaStore,
    private val seedreamClient: SeedreamClient,
    private val modelCatalog: ModelCatalogRepository,
    private val providers: Map<ProviderId, @JvmSuppressWildcards LlmProvider>,
    private val settingsRepository: SettingsRepository,
    private val memoryStore: MemoryStore,
    private val autoTitler: AutoTitler,
    private val toolRegistry: ToolRegistry,
    private val json: Json,
    @AppScope private val appScope: CoroutineScope,
) : ChatRepository {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val activeIds = MutableStateFlow<Set<Long>>(emptySet())

    /** 正在生成中的正文只留在内存里，避免每个 token 都写一次数据库。 */
    private val streaming = MutableStateFlow<Map<Long, StreamingText>>(emptyMap())

    /** 分享进来但还没发送的草稿；进程重启即丢弃。 */
    private val drafts = ConcurrentHashMap<Long, ChatDraft>()

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeConversation(id: Long): Flow<Conversation?> =
        conversationDao.observe(id).map { row -> row?.toDomain() }

    override fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
        combine(
            messageDao.observe(conversationId),
            attachmentDao.observeForConversation(conversationId),
            streaming.map { it[conversationId] }.distinctUntilChanged(),
        ) { messages, attachments, live ->
            val byMessage = attachments.groupBy { it.messageId }
            val snapshot = messages.map { row ->
                row.toDomain(byMessage[row.id].orEmpty().map { it.toDomain() })
            }
            if (live == null) {
                snapshot
            } else {
                snapshot.overlayStreaming(live.messageId, live.content, live.reasoning)
            }
        }

    override fun isGenerating(conversationId: Long): Flow<Boolean> =
        activeIds.map { conversationId in it }.distinctUntilChanged()

    override suspend fun createConversation(provider: ProviderId, model: String): Long {
        val config = settingsRepository.get(provider)
        val chosen = model.trim().ifBlank { config.model.ifBlank { provider.defaultModel } }
        return conversationDao.insert(
            ConversationEntity(
                // 空标题表示「还没自动命名」，界面再显示当前语言的「新对话」。
                title = "",
                provider = provider.name,
                model = chosen,
                systemPrompt = context.getString(R.string.assistant_prompt),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateModel(conversationId: Long, model: String): String? {
        val chosen = model.trim()
        if (chosen.isEmpty()) return context.getString(R.string.choose_model_error)
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        val conversation = conversationDao.get(conversationId) ?: return context.getString(R.string.conversation_missing)
        conversationDao.update(
            conversation.copy(model = chosen, updatedAt = System.currentTimeMillis()),
        )
        return null
    }

    override suspend fun updateSystemPrompt(conversationId: Long, prompt: String): String? {
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        val conversation = conversationDao.get(conversationId) ?: return context.getString(R.string.conversation_missing)
        conversationDao.update(
            conversation.copy(
                systemPrompt = prompt.trim().ifBlank { context.getString(R.string.assistant_prompt) },
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return null
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        conversationDao.setPinned(id, pinned)
    }

    override suspend fun setThinking(id: Long, thinking: Boolean) {
        conversationDao.setThinking(id, thinking)
    }

    /**
     * 分享文本落成草稿。
     *
     * 草稿不进 messages 表：它还没被发送，塞进历史会让模型以为用户已经说过这句话。
     * 因此草稿存在会话的 title 之外的一个内存表里，由 ChatViewModel 首次读取。
     */
    override suspend fun setDraft(conversationId: Long, text: String) {
        drafts[conversationId] = ChatDraft(text = text, uris = drafts[conversationId]?.uris.orEmpty())
    }

    override suspend fun setDraftImages(conversationId: Long, uris: List<Uri>) {
        val imported = uris.mapNotNull { mediaStore.importUserImage(it) }
        drafts[conversationId] = ChatDraft(
            text = drafts[conversationId]?.text.orEmpty(),
            uris = imported.map { Uri.fromFile(java.io.File(it.path)) },
        )
    }

    override fun takeDraft(conversationId: Long): ChatDraft? = drafts.remove(conversationId)

    override suspend fun renameConversation(id: Long, title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) conversationDao.rename(id, clean)
    }

    override suspend fun deleteConversation(id: Long) {
        jobs[id]?.cancel()
        val paths = attachmentDao.forConversation(id).map { it.localPath }
        conversationDao.delete(id)
        mediaStore.deletePaths(paths)
    }

    override suspend fun send(
        conversationId: Long,
        text: String,
        imageUris: List<Uri>,
        drawMode: Boolean,
    ): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && imageUris.isEmpty()) return context.getString(R.string.enter_message)
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        if (imageUris.size > ChatMediaStore.MAX_ATTACHMENTS) {
            return context.getString(R.string.too_many_images, ChatMediaStore.MAX_ATTACHMENTS)
        }
        val prepared = prepare(conversationId) ?: return context.getString(R.string.conversation_missing)
        val (conversation, provider, config) = prepared
        // 仅显式画图模式触发生图，避免「画一张…」等口语误劫持普通聊天。
        val shouldDraw = drawMode
        if (shouldDraw) {
            val doubao = settingsRepository.get(ProviderId.DOUBAO)
            // 生图只有豆包 Seedream：免费档与未填 Key 都给出明确说明。
            if (doubao.apiKey.isBlank()) return context.getString(R.string.need_doubao_for_draw)
        } else {
            val rejected = rejectIfBusyOrMissingKey(conversationId, provider, config)
            if (rejected != null) return rejected
            if (imageUris.isNotEmpty() &&
                !modelCatalog.supportsVision(provider, conversation.model.ifBlank { config.model })
            ) {
                return context.getString(R.string.model_no_vision)
            }
        }
        val imported = imageUris.mapNotNull { mediaStore.importUserImage(it) }
        if (imageUris.isNotEmpty() && imported.isEmpty()) {
            return context.getString(R.string.image_import_failed)
        }
        if (!start(conversation.id) {
                if (shouldDraw) {
                    generateImage(
                        conversation = conversation,
                        prompt = trimmed.ifBlank { context.getString(R.string.draw_default_prompt) },
                        imported = imported,
                        insertUser = true,
                        parentId = conversation.leafMessageId,
                    )
                } else {
                    generate(conversation, provider, config, trimmed, imported, conversation.leafMessageId)
                }
            }
        ) {
            mediaStore.deletePaths(imported.map { it.path })
            return context.getString(R.string.generating)
        }
        return null
    }

    override suspend fun retry(conversationId: Long): String? {
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        val prepared = prepare(conversationId) ?: return context.getString(R.string.conversation_missing)
        val (conversation, provider, config) = prepared
        // 只重试激活路径上的失败助手消息：别的分支可能也有失败记录，不能误删。
        val path = activePath(conversation)
        val last = path.lastOrNull() ?: return context.getString(R.string.nothing_to_retry)
        if (last.role != Role.ASSISTANT.name || last.status != MessageStatus.FAILED.name) {
            return context.getString(R.string.nothing_to_retry)
        }
        // 失败助手消息的父节点就是这一轮的用户消息。
        val user = last.parentId.takeIf { it > 0L }?.let { messageDao.byId(it) }
        val prompt = user?.content.orEmpty()
        val drawRetry = last.error.startsWith(DRAW_ERROR_PREFIX)
        if (drawRetry) {
            val doubao = settingsRepository.get(ProviderId.DOUBAO)
            if (doubao.apiKey.isBlank()) return context.getString(R.string.need_doubao_for_draw)
        } else {
            val rejected = rejectIfBusyOrMissingKey(conversationId, provider, config)
            if (rejected != null) return rejected
        }
        // 1.3 起不删除失败回复：在同一父节点下新增兄弟助手消息，旧回复可用切换器找回。
        if (!start(conversation.id) {
                if (drawRetry) {
                    val refs = user?.let {
                        attachmentDao.forMessage(it.id)
                            .filter { it.source == AttachmentSource.USER.name }
                            .map { attachment -> ChatMediaStore.ImportedImage(attachment.localPath, attachment.mime) }
                    }.orEmpty()
                    generateImage(
                        conversation = conversation,
                        prompt = prompt.ifBlank { context.getString(R.string.draw_default_prompt) },
                        imported = refs,
                        insertUser = false,
                        parentId = last.parentId,
                    )
                } else {
                    // 当前 leaf 还是那条失败的助手消息，直接在这种位置补一条兄弟回复。
                    generate(
                        conversation = conversation,
                        provider = provider,
                        config = config,
                        newUserText = null,
                        imported = emptyList(),
                        parentId = last.parentId,
                    )
                }
            }
        ) {
            return context.getString(R.string.generating)
        }
        return null
    }

    override suspend fun editAndSend(conversationId: Long, messageId: Long, newText: String): String? {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return context.getString(R.string.enter_message)
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        val prepared = prepare(conversationId) ?: return context.getString(R.string.conversation_missing)
        val (conversation, provider, config) = prepared
        val target = messageDao.byId(messageId) ?: return context.getString(R.string.nothing_to_retry)
        if (target.conversationId != conversationId) return context.getString(R.string.nothing_to_retry)
        val rejected = rejectIfBusyOrMissingKey(conversationId, provider, config)
        if (rejected != null) return rejected
        if (!start(conversation.id) {
                // 新用户消息挂在被编辑消息的父节点下，成为同级的兄弟分支。
                generate(
                    conversation = conversation,
                    provider = provider,
                    config = config,
                    newUserText = trimmed,
                    imported = emptyList(),
                    parentId = target.parentId,
                )
            }
        ) {
            return context.getString(R.string.generating)
        }
        return null
    }

    override suspend fun switchBranch(conversationId: Long, messageId: Long): String? {
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        val conversation = conversationDao.get(conversationId)
            ?: return context.getString(R.string.conversation_missing)
        val target = messageDao.byId(messageId) ?: return context.getString(R.string.nothing_to_retry)
        if (target.conversationId != conversationId) return context.getString(R.string.nothing_to_retry)
        val all = messageDao.list(conversationId)
        val leaf = BranchPath.deepestLeafOrdered(
            items = all,
            targetId = messageId,
            order = compareBy({ it.createdAt }, { it.id }),
            idOf = { it.id },
            parentOf = { it.parentId },
        )
        if (leaf <= 0L) return context.getString(R.string.nothing_to_retry)
        conversationDao.setLeaf(conversation.id, leaf)
        return null
    }

    /** 当前激活路径（含 FAILED，生成历史时再按状态过滤）。 */
    private suspend fun activePath(conversation: ConversationEntity): List<MessageEntity> {
        val all = messageDao.list(conversation.id)
        val leaf = conversation.leafMessageId
        return BranchPath.resolveOrdered(
            items = all,
            leafId = leaf,
            order = compareBy({ it.createdAt }, { it.id }),
            idOf = { it.id },
            parentOf = { it.parentId },
        )
    }

    override fun stop(conversationId: Long) {
        jobs[conversationId]?.cancel()
    }

    private suspend fun prepare(conversationId: Long): Triple<ConversationEntity, ProviderId, ProviderConfig>? {
        val conversation = conversationDao.get(conversationId) ?: return null
        val provider = runCatching { ProviderId.valueOf(conversation.provider) }.getOrNull() ?: return null
        return Triple(conversation, provider, settingsRepository.get(provider))
    }

    private fun rejectIfBusyOrMissingKey(
        conversationId: Long,
        provider: ProviderId,
        config: ProviderConfig,
    ): String? {
        if (jobs[conversationId]?.isActive == true) return context.getString(R.string.generating)
        // 免密钥车道（免费池 / 自定义端点）不校验 Key，也不再拦截发送。
        if (config.apiKey.isBlank() && provider.requiresKey) {
            return context.getString(R.string.need_provider_key, context.getString(provider.nameRes))
        }
        return null
    }

    private fun start(conversationId: Long, block: suspend () -> Unit): Boolean {
        // 先建一个惰性任务，等占位成功后再真正启动，避免两次快速发送都插入了用户消息。
        val job = appScope.launch(start = CoroutineStart.LAZY) { block() }
        var claimed = false
        jobs.compute(conversationId) { _, existing ->
            if (existing?.isActive == true) {
                claimed = false
                existing
            } else {
                claimed = true
                job
            }
        }
        if (!claimed) {
            job.cancel()
            return false
        }
        activeIds.update { it + conversationId }
        job.invokeOnCompletion {
            jobs.remove(conversationId, job)
            activeIds.update { ids -> ids - conversationId }
        }
        job.start()
        return true
    }

    private suspend fun generate(
        conversation: ConversationEntity,
        provider: ProviderId,
        config: ProviderConfig,
        newUserText: String?,
        imported: List<ChatMediaStore.ImportedImage>,
        parentId: Long,
    ) {
        var assistantId = 0L
        var userId = 0L
        var content = ""
        var reasoning = ""
        var terminal = false
        try {
            val now = System.currentTimeMillis()
            val userText = newUserText.orEmpty()
            if (newUserText != null || imported.isNotEmpty()) {
                userId = messageDao.insert(
                    MessageEntity(
                        conversationId = conversation.id,
                        role = Role.USER.name,
                        content = userText,
                        status = MessageStatus.DONE.name,
                        createdAt = now,
                        parentId = parentId,
                    ),
                )
                if (imported.isNotEmpty()) {
                    attachmentDao.insertAll(
                        imported.mapIndexed { index, image ->
                            AttachmentEntity(
                                messageId = userId,
                                conversationId = conversation.id,
                                localPath = image.path,
                                mime = image.mime,
                                source = AttachmentSource.USER.name,
                                sortOrder = index,
                            )
                        },
                    )
                }
            }
            val model = conversation.model.ifBlank {
                config.model.ifBlank { provider.defaultModel }
            }
            // 用列级更新而不是整行覆盖：整行写回会把刚推进的 leafMessageId 冲回旧值。
            conversationDao.touch(
                id = conversation.id,
                title = if ((newUserText != null || imported.isNotEmpty()) &&
                    isUntitled(conversation.title)
                ) {
                    userText.take(24).ifBlank { context.getString(R.string.image_message_title) }
                } else {
                    conversation.title
                },
                model = model,
                updatedAt = now,
            )
            assistantId = messageDao.insert(
                MessageEntity(
                    conversationId = conversation.id,
                    role = Role.ASSISTANT.name,
                    content = "",
                    status = MessageStatus.STREAMING.name,
                    createdAt = System.currentTimeMillis(),
                    // 新用户消息刚插入时用它作父节点；重试时为原用户消息。
                    parentId = if (userId != 0L) userId else parentId,
                ),
            )
            // 本轮的用户消息（新插入的，或重试时已有的那条）就是历史回溯的起点。
            val historyLeaf = if (userId != 0L) userId else parentId
            val messages = buildWireHistory(conversation, historyLeaf)
            val doubaoKeyReady = settingsRepository.get(ProviderId.DOUBAO).apiKey.isNotBlank()
            val enableTool = provider == ProviderId.DOUBAO && doubaoKeyReady
            val llm = providers[provider]
                ?: error(context.getString(R.string.provider_missing, context.getString(provider.nameRes)))
            // 本地工具只在收费车道声明：免费池的工具调用兼容性参差，
            // 且免费档的定位是「能聊」，不追加重试轮次。
            val toolSchemas = if (provider.requiresKey) {
                toolRegistry.specs().map { spec ->
                    com.yuguang.lumo.data.remote.toolSchemaOf(spec.name, spec.description, spec.parameters)
                }
            } else {
                emptyList()
            }
            var lastFlush = 0L
            var lastStream = 0L
            var pendingTools: List<ToolCallRequest> = emptyList()
            // 一轮对话最多允许三轮工具调用，防止模型自己绕圈。
            val working = messages.toMutableList()
            var round = 0
            while (true) {
                val trace = StringBuilder()
                var roundContent = ""
                var roundReasoning = ""
                var completed = false
                var failed: String? = null
                var usagePrompt = 0
                var usageCompletion = 0
                llm.stream(
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl,
                    request = ChatRequest(
                        model = model,
                        messages = working.toList(),
                        enableImageTool = enableTool,
                        thinking = conversation.thinking,
                        fallbackModels = if (provider == ProviderId.FREE) {
                            freeFallbackChain.filter { it != model }
                        } else {
                            emptyList()
                        },
                        maxOutputTokens = if (provider == ProviderId.FREE) FREE_MAX_OUTPUT else 0,
                        toolSchemas = toolSchemas,
                    ),
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Delta -> {
                            roundContent += event.text
                            content = if (round == 0) roundContent else content + roundContent
                            lastStream = publishStreaming(
                                conversationId = conversation.id,
                                messageId = assistantId,
                                content = content,
                                reasoning = reasoning,
                                lastPublish = lastStream,
                            )
                            lastFlush = flush(assistantId, content, reasoning, lastFlush, force = false)
                        }
                        is StreamEvent.Reasoning -> {
                            roundReasoning += event.text
                            reasoning += event.text
                            lastStream = publishStreaming(
                                conversationId = conversation.id,
                                messageId = assistantId,
                                content = content,
                                reasoning = reasoning,
                                lastPublish = lastStream,
                            )
                            lastFlush = flush(assistantId, content, reasoning, lastFlush, force = false)
                        }
                        is StreamEvent.ToolCalls -> pendingTools = event.calls
                        is StreamEvent.Completed -> {
                            usagePrompt += event.promptTokens ?: 0
                            usageCompletion += event.completionTokens ?: 0
                            completed = true
                        }
                        is StreamEvent.Failed -> failed = event.message
                    }
                }
                if (failed != null) {
                    messageDao.updateBody(
                        assistantId,
                        content,
                        reasoning,
                        MessageStatus.FAILED.name,
                        failed,
                    )
                    terminal = true
                    break
                }
                if (!completed) break

                // 生图工具仍走豆包 Seedream 的独立分支。
                val imageCall = pendingTools.firstOrNull { call ->
                    call.name.equals(DoubaoProvider.GENERATE_IMAGE_TOOL, ignoreCase = true)
                }
                if (imageCall != null) {
                    val body = content.ifBlank {
                        if (reasoning.isNotBlank()) context.getString(R.string.thinking_only_reply) else ""
                    }
                    handleImageTools(assistantId, conversation, pendingTools, body)
                    messageDao.updateUsage(assistantId, usagePrompt, usageCompletion)
                    settingsRepository.rememberRecent(provider, model)
                    scheduleMemoryExtraction(conversation, provider, model)
                    scheduleAutoTitle(conversation, provider, model)
                    terminal = true
                    break
                }

                val executable = pendingTools.filter { toolRegistry.spec(it.name) != null }
                if (executable.isEmpty() || round >= MAX_TOOL_ROUNDS) {
                    val body = content.ifBlank {
                        if (reasoning.isNotBlank()) context.getString(R.string.thinking_only_reply) else ""
                    }
                    messageDao.updateBody(assistantId, body, reasoning, MessageStatus.DONE.name, "")
                    messageDao.updateToolTrace(assistantId, trace.toString())
                    messageDao.updateUsage(assistantId, usagePrompt, usageCompletion)
                    settingsRepository.rememberRecent(provider, model)
                    scheduleMemoryExtraction(conversation, provider, model)
                    scheduleAutoTitle(conversation, provider, model)
                    terminal = true
                    break
                }

                // 把这一轮的助手回复与工具结果追加进历史，再请求下一轮。
                working += WireMessage(Role.ASSISTANT, roundContent, toolCalls = executable)
                executable.forEach { call ->
                    val result = toolRegistry.execute(call.name, call.argumentsJson)
                    working += WireMessage(
                        Role.TOOL,
                        result,
                        toolCallId = call.id,
                        toolName = call.name,
                    )
                    trace.append(call.name).append(" -> ")
                        .append(result.replace('\n', ' ').take(200)).append('\n')
                }
                messageDao.updateBody(
                    assistantId,
                    content,
                    reasoning,
                    MessageStatus.STREAMING.name,
                    "",
                )
                pendingTools = emptyList()
                round++
            }
        } catch (cancelled: CancellationException) {
            finishCancelled(assistantId, content, reasoning, terminal)
            throw cancelled
        } catch (error: Exception) {
            if (assistantId != 0L && !terminal) {
                messageDao.updateBody(
                    assistantId,
                    content,
                    reasoning,
                    MessageStatus.FAILED.name,
                    error.message ?: context.getString(R.string.request_failed),
                )
            }
        } finally {
            // 收尾后以数据库为准；这里只清理内存里的流式副本。
            streaming.update { it - conversation.id }
            // 成功/失败/取消三种收尾都把 leaf 指向本轮助手消息，
            // 助手被删（空回复取消）时退回用户消息，避免 leaf 悬空。
            // NonCancellable：取消路径里普通 suspend 调用会立刻再次抛出。
            withContext(NonCancellable) { markLeaf(conversation.id, assistantId, userId) }
        }
    }

    /**
     * 本轮结束后推进分支游标。
     *
     * 游标必须指向「刚刚产生的这条回复」，否则下一句会挂到旧分支上。
     */
    private suspend fun markLeaf(conversationId: Long, assistantId: Long, userId: Long) {
        val leaf = when {
            assistantId != 0L && messageDao.byId(assistantId) != null -> assistantId
            userId != 0L -> userId
            else -> 0L
        }
        if (leaf > 0L) conversationDao.setLeaf(conversationId, leaf)
    }

    /**
     * 异步提炼长期记忆。
     *
     * 只在记忆开启且本轮有用户消息时触发；用当前会话模型，失败静默。
     */
    /**
     * 异步生成 AI 标题（1.3）。
     *
     * 首轮结束后触发一次；开关关闭或标题已存在时直接跳过，失败静默。
     * 分支/重试路径不要额外调用——[AutoTitler.shouldTitle] 自带幂等判断。
     */
    private fun scheduleAutoTitle(
        conversation: ConversationEntity,
        provider: ProviderId,
        model: String,
    ) {
        appScope.launch {
            runCatching { autoTitler.maybeTitle(conversation.id, provider, model) }
        }
    }

    private fun scheduleMemoryExtraction(
        conversation: ConversationEntity,
        provider: ProviderId,
        model: String,
    ) {
        appScope.launch {
            runCatching {
                if (!settingsRepository.memoryEnabled()) return@runCatching
                val recent = messageDao.recentDone(conversation.id, MEMORY_SOURCE_TURNS).asReversed()
                if (recent.none { it.role == Role.USER.name }) return@runCatching
                val transcript = recent.map { WireMessage(Role.valueOf(it.role), it.content) }
                memoryStore.extract(conversation.id, provider, model, transcript)
            }
        }
    }

    private suspend fun handleImageTools(
        assistantId: Long,
        conversation: ConversationEntity,
        calls: List<ToolCallRequest>,
        priorContent: String,
    ) {
        val drawCall = calls.firstOrNull {
            it.name.equals(DoubaoProvider.GENERATE_IMAGE_TOOL, ignoreCase = true)
        }
        if (drawCall == null) {
            messageDao.updateBody(assistantId, priorContent, "", MessageStatus.DONE.name, "")
            return
        }
        val prompt = parseToolPrompt(drawCall.argumentsJson)
            ?: priorContent.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.draw_default_prompt)
        messageDao.updateBody(
            assistantId,
            context.getString(R.string.drawing_in_progress),
            "",
            MessageStatus.STREAMING.name,
            "",
        )
        try {
            attachGeneratedImage(assistantId, conversation, prompt, emptyList())
            val caption = priorContent.ifBlank { context.getString(R.string.draw_done_seedream) }
            messageDao.updateBody(assistantId, caption, "", MessageStatus.DONE.name, "")
        } catch (error: Exception) {
            messageDao.updateBody(
                assistantId,
                priorContent,
                "",
                MessageStatus.FAILED.name,
                DRAW_ERROR_PREFIX + (error.message ?: context.getString(R.string.image_gen_failed)),
            )
        }
    }

    private suspend fun generateImage(
        conversation: ConversationEntity,
        prompt: String,
        imported: List<ChatMediaStore.ImportedImage>,
        insertUser: Boolean,
        parentId: Long,
    ) {
        var assistantId = 0L
        var userId = 0L
        var terminal = false
        try {
            val now = System.currentTimeMillis()
            if (insertUser) {
                userId = messageDao.insert(
                    MessageEntity(
                        conversationId = conversation.id,
                        role = Role.USER.name,
                        content = prompt,
                        status = MessageStatus.DONE.name,
                        createdAt = now,
                        parentId = parentId,
                    ),
                )
                if (imported.isNotEmpty()) {
                    attachmentDao.insertAll(
                        imported.mapIndexed { index, image ->
                            AttachmentEntity(
                                messageId = userId,
                                conversationId = conversation.id,
                                localPath = image.path,
                                mime = image.mime,
                                source = AttachmentSource.USER.name,
                                sortOrder = index,
                            )
                        },
                    )
                }
                conversationDao.touch(
                    id = conversation.id,
                    title = if (isUntitled(conversation.title)) {
                        prompt.take(24).ifBlank { context.getString(R.string.draw_message_title) }
                    } else {
                        conversation.title
                    },
                    model = conversation.model,
                    updatedAt = now,
                )
            }
            assistantId = messageDao.insert(
                MessageEntity(
                    conversationId = conversation.id,
                    role = Role.ASSISTANT.name,
                    content = context.getString(R.string.drawing_in_progress),
                    status = MessageStatus.STREAMING.name,
                    createdAt = System.currentTimeMillis(),
                    parentId = if (userId != 0L) userId else parentId,
                ),
            )
            attachGeneratedImage(assistantId, conversation, prompt, imported)
            messageDao.updateBody(
                assistantId,
                context.getString(R.string.draw_done_seedream),
                "",
                MessageStatus.DONE.name,
                "",
            )
            terminal = true
        } catch (cancelled: CancellationException) {
            if (assistantId != 0L && !terminal) {
                messageDao.delete(assistantId)
            }
            throw cancelled
        } catch (error: Exception) {
            if (assistantId != 0L && !terminal) {
                messageDao.updateBody(
                    assistantId,
                    context.getString(R.string.drawing_in_progress),
                    "",
                    MessageStatus.FAILED.name,
                    DRAW_ERROR_PREFIX + (error.message ?: context.getString(R.string.image_gen_failed)),
                )
            }
        } finally {
            withContext(NonCancellable) { markLeaf(conversation.id, assistantId, userId) }
        }
    }

    private suspend fun attachGeneratedImage(
        assistantId: Long,
        conversation: ConversationEntity,
        prompt: String,
        imported: List<ChatMediaStore.ImportedImage>,
    ) {
        val doubao = settingsRepository.get(ProviderId.DOUBAO)
        val refs = imported.mapNotNull { mediaStore.toDataUrl(it.path, it.mime) }
        val result = seedreamClient.generate(
            apiKey = doubao.apiKey,
            baseUrl = doubao.baseUrl,
            model = modelCatalog.preferredImageGenModel(),
            prompt = prompt,
            referenceDataUrls = refs,
        )
        val bytes = result.bytes ?: error(context.getString(R.string.image_gen_empty))
        val path = mediaStore.saveGenerated(bytes, result.mime)
        attachmentDao.insert(
            AttachmentEntity(
                messageId = assistantId,
                conversationId = conversation.id,
                localPath = path,
                mime = result.mime,
                source = AttachmentSource.GENERATED.name,
                remoteUrl = result.url.orEmpty(),
                sortOrder = 0,
            ),
        )
    }

    /**
     * 组装发给模型的历史。
     *
     * 1.3 起先取激活路径（leaf 沿 parentId 回溯），再沿用条数/字符预算裁剪；
     * 别的分支的消息绝不发给模型，否则改一句话后模型会同时看到两版答案。
     *
     * @param leafId 从哪条消息回溯；编辑旧消息时是新的用户消息，重试时是原用户消息，
     *   这样历史只含该分支的祖先链，不会把后面分支的内容也带上。
     */
    private suspend fun buildWireHistory(
        conversation: ConversationEntity,
        leafId: Long = 0L,
    ): List<WireMessage> {
        val path = if (leafId > 0L) {
            BranchPath.resolveOrdered(
                items = messageDao.list(conversation.id),
                leafId = leafId,
                order = compareBy({ it.createdAt }, { it.id }),
                idOf = { it.id },
                parentOf = { it.parentId },
            )
        } else {
            activePath(conversation)
        }
        // 保留 FAILED 链路（切换器要能看到），但只把 DONE 发给模型。
        val recent = path
            .filter { it.status == MessageStatus.DONE.name }
            .takeLast(HISTORY_FETCH_LIMIT)
        val window = if (recent.isEmpty()) {
            emptyList()
        } else {
            val start = historyStartIndex(
                textLengths = recent.map { it.content.length },
                maxMessages = HISTORY_MAX_MESSAGES,
                maxChars = HISTORY_MAX_CHARS,
            )
            recent.subList(start, recent.size)
        }
        val userIds = window.filter { it.role == Role.USER.name }.map { it.id }
        val images = if (userIds.isEmpty()) {
            emptyMap()
        } else {
            attachmentDao.forUserMessages(userIds).groupBy { it.messageId }
        }
        var imagesLeft = IMAGE_HISTORY_LIMIT
        var imageCharsLeft = IMAGE_HISTORY_CHARS
        // 长期记忆：作为额外 system 消息插在人设之后，预算独立，不挤占对话历史。
        val memoryBlock = memoryStore.promptBlock()
        return buildList {
            if (conversation.systemPrompt.isNotBlank()) {
                add(WireMessage(Role.SYSTEM, conversation.systemPrompt))
            }
            if (memoryBlock.isNotBlank()) {
                add(WireMessage(Role.SYSTEM, memoryBlock))
            }
            window.forEach { row ->
                val role = Role.valueOf(row.role)
                val picked = mutableListOf<AttachmentEntity>()
                if (role == Role.USER) {
                    for (attachment in images[row.id].orEmpty()) {
                        if (picked.size >= imagesLeft) break
                        val encoded = mediaStore.encodedLength(attachment.localPath)
                        if (encoded <= 0 || encoded > imageCharsLeft) continue
                        imageCharsLeft -= encoded
                        picked += attachment
                    }
                }
                imagesLeft -= picked.size
                val dataUrls = picked.mapNotNull { mediaStore.toDataUrl(it.localPath, it.mime) }
                add(WireMessage(role, row.content, dataUrls))
            }
        }
    }

    private fun parseToolPrompt(argumentsJson: String): String? {
        if (argumentsJson.isBlank()) return null
        return runCatching {
            json.parseToJsonElement(argumentsJson).jsonObject["prompt"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun finishCancelled(
        assistantId: Long,
        content: String,
        reasoning: String,
        terminal: Boolean,
    ) {
        if (assistantId == 0L || terminal) return
        if (content.isBlank() && reasoning.isBlank()) {
            messageDao.delete(assistantId)
        } else {
            messageDao.updateBody(assistantId, content, reasoning, MessageStatus.DONE.name, "")
        }
    }

    private suspend fun flush(
        id: Long,
        content: String,
        reasoning: String,
        lastFlush: Long,
        force: Boolean,
    ): Long {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastFlush < FLUSH_INTERVAL_MS) return lastFlush
        messageDao.updateBody(id, content, reasoning, MessageStatus.STREAMING.name, "")
        return now
    }

    /** 把最新正文推到内存流；节流到几十毫秒，避免每个 token 都触发一次重组。 */
    private fun publishStreaming(
        conversationId: Long,
        messageId: Long,
        content: String,
        reasoning: String,
        lastPublish: Long,
    ): Long {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPublish < STREAM_INTERVAL_MS) return lastPublish
        streaming.update { it + (conversationId to StreamingText(messageId, content, reasoning)) }
        return now
    }

    private data class StreamingText(
        val messageId: Long,
        val content: String,
        val reasoning: String,
    )

    /**
     * 空标题表示还没自动命名。旧版本把本地化的「新对话」写进了库，
     * 读出来时统一归一成空串，避免换系统语言后标题还是旧语言。
     */
    private fun isUntitled(title: String): Boolean =
        title.isBlank() || title == LEGACY_NEW_CHAT_ZH || title == LEGACY_NEW_CHAT_EN

    private fun ConversationEntity.toDomain(): Conversation = Conversation(
        id = id,
        title = if (isUntitled(title)) "" else title,
        provider = ProviderId.valueOf(provider),
        model = model,
        systemPrompt = systemPrompt,
        updatedAt = updatedAt,
        thinking = thinking,
        leafMessageId = leafMessageId,
    )

    private fun ConversationSummaryRow.toDomain(): Conversation = Conversation(
        id = id,
        title = if (isUntitled(title)) "" else title,
        provider = ProviderId.valueOf(provider),
        model = model,
        systemPrompt = systemPrompt,
        updatedAt = updatedAt,
        pinned = pinned,
        thinking = thinking,
        preview = preview?.trim()?.replace('\n', ' ')?.take(120),
        totalTokens = totalTokens,
    )

    private fun MessageEntity.toDomain(attachments: List<MessageAttachment> = emptyList()): ChatMessage =
        ChatMessage(
            id = id,
            conversationId = conversationId,
            role = Role.valueOf(role),
            content = content,
            reasoning = reasoning,
            status = MessageStatus.valueOf(status),
            error = error.removePrefix(DRAW_ERROR_PREFIX),
            createdAt = createdAt,
            attachments = attachments,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            toolTrace = toolTrace,
            parentId = parentId,
        )

    private fun AttachmentEntity.toDomain(): MessageAttachment = MessageAttachment(
        id = id,
        messageId = messageId,
        localPath = localPath,
        mime = mime,
        source = runCatching { AttachmentSource.valueOf(source) }.getOrDefault(AttachmentSource.USER),
        remoteUrl = remoteUrl,
        sortOrder = sortOrder,
    )

    companion object {
        const val DRAW_ERROR_PREFIX = "__draw__:"
        /** 免费车道的单轮输出上限：思考与正文共享，压太低会只出思考。 */
        private const val FREE_MAX_OUTPUT = 4_096
        /** 先按条数粗取，再交给 [historyStartIndex] 按字符预算裁剪。 */
        private const val HISTORY_FETCH_LIMIT = 40
        private const val HISTORY_MAX_MESSAGES = 24
        private const val HISTORY_MAX_CHARS = 12_000
        private const val IMAGE_HISTORY_LIMIT = 4
        /** 提炼记忆时回看的消息条数。 */
        private const val MEMORY_SOURCE_TURNS = 8
        /** 一轮对话最多允许的工具调用轮次。 */
        private const val MAX_TOOL_ROUNDS = 3
        /** 历史附图 base64 后的总字符预算（约 4.5 MB 原始数据）。 */
        private const val IMAGE_HISTORY_CHARS = 6_000_000L
        /** 数据库兜底落盘间隔：正文平时只在内存里，进程被杀最多回退这么多内容。 */
        private const val FLUSH_INTERVAL_MS = 1_000L
        /** 内存流式文本的界面刷新间隔。 */
        private const val STREAM_INTERVAL_MS = 60L
        private const val LEGACY_NEW_CHAT_ZH = "新对话"
        private const val LEGACY_NEW_CHAT_EN = "New chat"
    }
}
