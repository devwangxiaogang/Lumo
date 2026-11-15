package com.yuguang.lumo.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuguang.lumo.R
import com.yuguang.lumo.data.ChatExporter
import com.yuguang.lumo.data.SpeechReader
import com.yuguang.lumo.data.SpeechSplitter
import com.yuguang.lumo.data.StreamingSpeaker
import com.yuguang.lumo.data.local.ChatMediaStore
import com.yuguang.lumo.data.remote.DoubaoAsrClient
import com.yuguang.lumo.domain.ChatCommand
import com.yuguang.lumo.domain.ChatRepository
import com.yuguang.lumo.domain.ModelCatalogRepository
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.BranchPath
import com.yuguang.lumo.domain.model.ChatMessage
import com.yuguang.lumo.domain.model.Conversation
import com.yuguang.lumo.domain.model.MessageStatus
import com.yuguang.lumo.domain.model.ModelChoice
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val conversation: Conversation? = null,
    /** 已按当前激活分支（leaf）过滤的消息，界面只显示这一段。 */
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val pendingImages: List<Uri> = emptyList(),
    val drawMode: Boolean = false,
    val generating: Boolean = false,
    val speakingId: Long? = null,
    val catalog: List<ModelChoice> = emptyList(),
    /** 边生成边朗读开关（来自语音偏好）。 */
    val autoRead: Boolean = false,
    /** 分支切换器：消息 id → 它在同角色兄弟里的位置与全部候选。 */
    val branches: Map<Long, BranchEntry> = emptyMap(),
)

/** [ids] 是同一父节点下升序的兄弟 id，[index] 是当前消息在其中的下标。 */
data class BranchEntry(val ids: List<Long>, val index: Int) {
    val count: Int get() = ids.size

    fun previous(): Long? = ids.getOrNull(index - 1)

    fun next(): Long? = ids.getOrNull(index + 1)
}

data class SpeakPrompt(val message: String, val messageId: Long, val content: String)

/** 斜杠指令需要界面配合完成的一次跳转或动作。 */
sealed interface ChatNav {
    data class Translate(val text: String) : ChatNav

    data class OpenConversation(val id: Long) : ChatNav

    data object Voice : ChatNav

    data object PickModel : ChatNav

    data object Help : ChatNav
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val modelCatalog: ModelCatalogRepository,
    private val speechRecognizer: DoubaoAsrClient,
    private val speechReader: SpeechReader,
    private val exporter: ChatExporter,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val conversationId: Long = savedStateHandle.get<Long>("id")
        ?: savedStateHandle.get<String>("id")?.toLongOrNull()
        ?: error(context.getString(R.string.missing_conversation_id))

    private val input = MutableStateFlow("")
    private val pendingImages = MutableStateFlow<List<Uri>>(emptyList())
    private val drawMode = MutableStateFlow(false)
    private val errorsFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors = errorsFlow.asSharedFlow()

    private val promptsFlow = MutableSharedFlow<SpeakPrompt>(extraBufferCapacity = 1)
    val speakPrompts = promptsFlow.asSharedFlow()

    private val navFlow = MutableSharedFlow<ChatNav>(extraBufferCapacity = 4)
    val nav = navFlow.asSharedFlow()

    private val conversationFlow = repository.observeConversation(conversationId)
    private val catalogFlow = conversationFlow.flatMapLatest { conversation ->
        if (conversation == null) flowOf(emptyList())
        else modelCatalog.observe(conversation.provider)
    }
    private val autoReadFlow = MutableStateFlow(false)
    private var autoReadEnabled = false
    /** 免费档朗读走公开网页端点，按句请求更容易撞限速，句长下限放大一些。 */
    private var freeLane = false
    private var streamSpeaker: StreamingSpeaker? = null
    private var streamMessageId = 0L
    /**
     * 已经喂给朗读器的完整文本，而不是长度。
     *
     * 流式期间界面读的是内存覆盖副本，落库后会换成数据库版本：正文可能被替换
     * （只有思考时的兜底文案）、被截断（取消/失败）或变短（flush 节流）。
     * 只比较长度会在这些时刻把字符串从中间切开——读一半、重读或跳读。
     * 用「已喂内容」做前缀校验，就能区分「只是变长了」和「内容被换掉了」。
     */
    private var fedContent = ""
    private var streamClosed = false

    private val core = combine(
        conversationFlow,
        repository.observeMessages(conversationId),
        repository.isGenerating(conversationId),
        input,
        pendingImages,
    ) { conversation, messages, generating, text, images ->
        CoreSlice(conversation, messages, generating, text, images)
    }

    val state: StateFlow<ChatUiState> = combine(
        core,
        drawMode,
        speechReader.speaking,
        catalogFlow,
        autoReadFlow,
    ) { slice, drawing, speaking, catalog, autoRead ->
        val speakingId = speaking?.removePrefix("chat:")?.toLongOrNull()
        // 只显示激活分支：叶子 0（老会话）时 resolvePath 退回全量，与 1.2 行为一致。
        val active = BranchPath.resolvePath(
            slice.messages,
            slice.conversation?.leafMessageId ?: 0L,
        )
        ChatUiState(
            conversation = slice.conversation,
            messages = active,
            input = slice.input,
            pendingImages = slice.images,
            drawMode = drawing,
            generating = slice.generating,
            speakingId = speakingId,
            catalog = catalog,
            autoRead = autoRead,
            branches = branchEntries(active, slice.messages),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    /**
     * 给激活路径里的消息算兄弟切换器数据；全量消息参与统计，切走的分支也算候选。
     *
     * 两种分支都要能看见：
     * - 助手消息重试 → 同一用户消息下多条助手回复；
     * - 用户消息「编辑并分支」→ 同一父节点下多条用户消息（根节点也适用）。
     *
     * 但 1.2 及更早的备份导入后整段对话 parentId 都是 0，那时它们不是「分支」而是同一条链，
     * 因此只有当库里真的存在父子关系（[hasTree]）时才承认根节点上的兄弟是一个分支。
     */
    private fun branchEntries(
        active: List<ChatMessage>,
        all: List<ChatMessage>,
    ): Map<Long, BranchEntry> = buildMap {
        val hasTree = all.any { it.parentId != 0L }
        active.forEach { message ->
            if (message.parentId == 0L && !hasTree) return@forEach
            val group = BranchPath.siblings(all, message.id)
            val index = group.indexOfFirst { it.id == message.id }
            if (group.size > 1 && index >= 0) {
                put(message.id, BranchEntry(group.map { it.id }, index))
            }
        }
    }

    private data class CoreSlice(
        val conversation: Conversation?,
        val messages: List<ChatMessage>,
        val generating: Boolean,
        val input: String,
        val images: List<Uri>,
    )

    init {
        viewModelScope.launch {
            settingsRepository.observeSpeech().collect { preferences ->
                autoReadEnabled = preferences.autoRead
                freeLane = preferences.tier == com.yuguang.lumo.domain.model.SpeechTier.FREE
                autoReadFlow.value = preferences.autoRead
                if (!preferences.autoRead) cancelStreamingRead()
            }
        }
        // 边生成边朗读：从已经合并了流式覆盖的消息流里取激活分支尾部增量。
        // 必须按 leaf 过滤，否则切到旧分支时会把另一条分支的回复读出来。
        viewModelScope.launch {
            combine(conversationFlow, repository.observeMessages(conversationId)) { conversation, messages ->
                BranchPath.resolvePath(messages, conversation?.leafMessageId ?: 0L)
            }.collect { messages -> feedStreamingRead(messages.lastOrNull()) }
        }
        viewModelScope.launch {
            var lastProvider: ProviderId? = null
            conversationFlow.collect { conversation ->
                val provider = conversation?.provider ?: return@collect
                if (provider == lastProvider) return@collect
                lastProvider = provider
                modelCatalog.refreshIfStale(provider)
            }
        }
        // 分享进来的内容：预填到输入框与附图区，不自动发送。
        viewModelScope.launch {
            val draft = repository.takeDraft(conversationId) ?: return@launch
            if (draft.text.isNotBlank()) input.value = draft.text
            if (draft.uris.isNotEmpty()) pendingImages.value = draft.uris
        }
    }

    fun updateInput(value: String) {
        input.value = value
    }

    /** 开关边生成边朗读；写入语音偏好，所有会话共用。 */
    fun setAutoRead(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.editSpeech { it.copy(autoRead = enabled) }
            errorsFlow.tryEmit(
                context.getString(if (enabled) R.string.auto_read_on else R.string.auto_read_off),
            )
        }
    }

    /**
     * 把尾部助手消息的增量喂给朗读器。
     *
     * 消息流已经合并了仓储的内存流式覆盖（见 ChatRepositoryImpl.observeMessages），
     * 所以这里按「已喂内容」取前缀差即可，不需要改动发送链路。
     */
    private fun feedStreamingRead(tail: ChatMessage?) {
        if (!autoReadEnabled || tail == null || tail.role != Role.ASSISTANT) {
            cancelStreamingRead()
            return
        }
        if (tail.status == MessageStatus.FAILED) {
            cancelStreamingRead()
            return
        }
        if (tail.id != streamMessageId) {
            cancelStreamingRead()
            // 历史里已经完成的消息不朗读，只朗读本次正在生成的这一条。
            if (tail.status != MessageStatus.STREAMING) return
            streamMessageId = tail.id
        }
        // 收尾过一次就不要重播：同一条消息还会因为后续落库而再次进入这个回调。
        if (streamClosed) return

        val content = tail.content
        // 内容不再以已喂部分开头，说明这一轮被替换或回滚过（兜底文案、取消、重试）。
        // 已经念出去的收不回来，但绝不能接着从中间往下念——直接收尾并停止。
        val delta = StreamFeed.delta(fedContent, content)
        if (delta == null) {
            finishStreamingRead()
            streamClosed = true
            return
        }
        val speaker = streamSpeaker ?: speechReader.beginStreaming(
            scope = viewModelScope,
            key = "stream:" + tail.id,
            minChunk = if (freeLane) FREE_MIN_CHUNK else SpeechSplitter.MIN_CHUNK,
        ).also { streamSpeaker = it }
        if (delta.isNotEmpty()) {
            speaker.push(delta)
            fedContent = content
        }
        if (tail.status == MessageStatus.DONE) {
            finishStreamingRead()
            streamClosed = true
        }
    }

    /** 把剩下的句子交出去并清掉句柄；不清 [fedContent]，收尾判断还要用它。 */
    private fun finishStreamingRead() {
        streamSpeaker?.finish()
        streamSpeaker = null
        fedContent = ""
    }

    private fun cancelStreamingRead() {
        streamSpeaker?.cancel()
        streamSpeaker = null
        fedContent = ""
        streamMessageId = 0L
        streamClosed = false
    }

    fun toggleDrawMode() {
        drawMode.update { !it }
    }

    fun toggleThinking() {
        val current = state.value.conversation ?: return
        viewModelScope.launch { repository.setThinking(conversationId, !current.thinking) }
    }

    /** 导出当前会话；成功/失败都通过 errors 通道反馈。 */
    fun export(uri: Uri, format: ChatExporter.Format) {
        viewModelScope.launch {
            runCatching { exporter.export(conversationId, uri, format) }.fold(
                onSuccess = { count -> errorsFlow.emit(context.getString(R.string.export_done, count)) },
                onFailure = { error ->
                    errorsFlow.emit(
                        context.getString(R.string.export_failed) + "：" + error.message.orEmpty(),
                    )
                },
            )
        }
    }

    fun addImages(uris: List<Uri>) {
        pendingImages.update { current ->
            (current + uris).distinct().take(ChatMediaStore.MAX_ATTACHMENTS)
        }
    }

    fun removeImage(uri: Uri) {
        pendingImages.update { it - uri }
    }

    fun clearImages() {
        pendingImages.value = emptyList()
    }

    /** 输入框的提交入口：先看是不是斜杠指令，再走普通发送。 */
    fun submit() {
        val command = ChatCommand.parse(input.value)
        if (command == null) {
            send()
            return
        }
        viewModelScope.launch { runCommand(command) }
    }

    private suspend fun runCommand(command: ChatCommand) {
        when (command) {
            is ChatCommand.Draw -> {
                if (command.prompt.isBlank()) {
                    errorsFlow.emit(context.getString(R.string.cmd_need_argument, "/draw"))
                    return
                }
                cancelStreamingRead()
                val reason = repository.send(conversationId, command.prompt, emptyList(), drawMode = true)
                if (reason == null) {
                    input.value = ""
                    drawMode.value = false
                } else {
                    errorsFlow.emit(reason)
                }
            }

            is ChatCommand.Translate -> {
                if (command.text.isBlank()) {
                    errorsFlow.emit(context.getString(R.string.cmd_need_argument, "/translate"))
                    return
                }
                input.value = ""
                navFlow.emit(ChatNav.Translate(command.text))
            }

            is ChatCommand.Summary -> {
                // 直接发，不经过输入框：让用户看着自己的话被替换成一长串提示词很别扭。
                input.value = ""
                val extra = command.text.takeIf { it.isNotBlank() }?.let { "\n" + it }.orEmpty()
                val prompt = context.getString(R.string.summary_prompt) + extra
                viewModelScope.launch {
                    cancelStreamingRead()
                    val reason = repository.send(conversationId, prompt)
                    if (reason != null) errorsFlow.emit(reason)
                }
            }

            is ChatCommand.Model -> {
                val query = command.query
                if (query.isBlank()) {
                    input.value = ""
                    navFlow.emit(ChatNav.PickModel)
                    return
                }
                val catalog = state.value.catalog
                // 精确命中优先；其次是唯一的前缀/包含命中。
                // 多个模型同时匹配时不能悄悄挑第一个——「flash」在免费池里能对上好几个。
                val exact = catalog.filter { it.id.equals(query, ignoreCase = true) }
                val loose = catalog.filter {
                    it.id.contains(query, ignoreCase = true) ||
                        it.label.contains(query, ignoreCase = true)
                }
                val candidates = if (exact.isNotEmpty()) exact else loose
                when {
                    candidates.isEmpty() -> {
                        errorsFlow.emit(context.getString(R.string.cmd_model_missing, query))
                    }
                    candidates.size == 1 -> {
                        input.value = ""
                        selectModel(candidates.single().id)
                    }
                    else -> {
                        // 有歧义就把选择权交回用户，顺带列出候选。
                        input.value = ""
                        errorsFlow.emit(
                            context.getString(
                                R.string.cmd_model_ambiguous,
                                candidates.joinToString("、") { it.label },
                            ),
                        )
                        navFlow.emit(ChatNav.PickModel)
                    }
                }
            }

            ChatCommand.Voice -> {
                input.value = ""
                navFlow.emit(ChatNav.Voice)
            }

            ChatCommand.NewChat -> {
                input.value = ""
                val starting = settingsRepository.startingModel()
                val current = state.value.conversation
                val id = repository.createConversation(
                    provider = current?.provider ?: starting.provider,
                    model = current?.model?.takeIf { it.isNotBlank() } ?: starting.model,
                )
                navFlow.emit(ChatNav.OpenConversation(id))
            }

            ChatCommand.Help -> {
                input.value = ""
                navFlow.emit(ChatNav.Help)
            }

            ChatCommand.Unknown -> {
                // 不认识的「指令」按普通消息发出去：粘贴一段 /usr/bin/env 之类的路径时，
                // 吞掉原文比多问模型一次糟糕得多。真正的指令命中前面几个分支。
                send()
            }
        }
    }

    fun send() {
        cancelStreamingRead()
        val text = input.value
        val images = pendingImages.value
        val drawing = drawMode.value
        viewModelScope.launch {
            val reason = repository.send(conversationId, text, images, drawing)
            if (reason == null) {
                input.value = ""
                pendingImages.value = emptyList()
                if (drawing) drawMode.value = false
            } else {
                errorsFlow.emit(reason)
            }
        }
    }

    fun applySuggestion(text: String, draw: Boolean = false) {
        input.value = text
        if (draw) drawMode.value = true
    }

    fun retry() {
        viewModelScope.launch {
            val reason = repository.retry(conversationId)
            if (reason != null) errorsFlow.emit(reason)
        }
    }

    /**
     * 编辑用户消息并从那里长出新分支。
     *
     * 原分支一条都不删；成功开始生成后清空输入并收起朗读，避免旧分支的语音继续念。
     */
    fun editAndSend(messageId: Long, text: String) {
        if (text.isBlank()) return
        cancelStreamingRead()
        viewModelScope.launch {
            val reason = repository.editAndSend(conversationId, messageId, text)
            if (reason != null) {
                errorsFlow.emit(reason)
            } else {
                input.value = ""
                pendingImages.value = emptyList()
            }
        }
    }

    /** 切到兄弟分支：仓储会把 leaf 移到该消息子树的最深叶子。 */
    fun switchBranch(messageId: Long) {
        viewModelScope.launch {
            val reason = repository.switchBranch(conversationId, messageId)
            if (reason != null) errorsFlow.emit(reason)
        }
    }

    fun stop() {
        cancelStreamingRead()
        repository.stop(conversationId)
    }

    fun selectModel(model: String) {
        viewModelScope.launch {
            val reason = repository.updateModel(conversationId, model)
            if (reason != null) errorsFlow.emit(reason)
        }
    }

    fun updatePersona(prompt: String) {
        viewModelScope.launch {
            val reason = repository.updateSystemPrompt(conversationId, prompt)
            if (reason != null) errorsFlow.emit(reason)
        }
    }

    fun speakLocal(messageId: Long, content: String) {
        cancelStreamingRead()
        speechReader.toggle(
            scope = viewModelScope,
            key = "chat:$messageId",
            text = content,
            forceLocal = true,
        ) { failure ->
            errorsFlow.tryEmit(failure.message)
        }
    }

    fun toggleSpeak(message: ChatMessage, forceLocal: Boolean = false) {
        cancelStreamingRead()
        speechReader.toggle(
            scope = viewModelScope,
            key = "chat:${message.id}",
            text = message.content,
            forceLocal = forceLocal,
        ) { failure ->
            if (failure.canUseLocal) {
                promptsFlow.tryEmit(SpeakPrompt(failure.message, message.id, message.content))
            } else {
                errorsFlow.tryEmit(failure.message)
            }
        }
    }

    override fun onCleared() {
        cancelStreamingRead()
        if (speechReader.speaking.value?.startsWith("chat:") == true) speechReader.stop()
    }

    fun recognize(wav: ByteArray, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val config = settingsRepository.get(ProviderId.DOUBAO)
                val apiKey = config.speechApiKey.trim()
                val appId = config.speechAppId.trim()
                val accessToken = config.speechAccessToken.trim()
                if (apiKey.isEmpty() && (appId.isEmpty() || accessToken.isEmpty())) {
                    onFailure(context.getString(R.string.asr_key_missing))
                } else {
                    onSuccess(
                        speechRecognizer.transcribe(
                            apiKey = apiKey,
                            wav = wav,
                            appId = appId,
                            accessToken = accessToken,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.asr_failed))
            }
        }
    }

    private companion object {
        /** 免费档的单句下限：公开端点按句请求，句子太短会把限速额度用光。 */
        const val FREE_MIN_CHUNK = 90
    }
}
