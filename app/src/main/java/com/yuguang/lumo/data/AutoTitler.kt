package com.yuguang.lumo.data

import android.content.Context
import com.yuguang.lumo.R
import com.yuguang.lumo.data.local.ConversationDao
import com.yuguang.lumo.data.local.MessageDao
import com.yuguang.lumo.domain.LlmProvider
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.ChatRequest
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.domain.model.StreamEvent
import com.yuguang.lumo.domain.model.WireMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 自动标题：会话首轮结束后，用一次低预算请求生成 6-12 字标题。
 *
 * 设计取舍：
 * - 只在标题为空 / 旧版「新对话」时触发，用户手动改过的标题永不覆盖；
 * - 失败、超时（8 秒）、限流全部静默，列表保留首条消息截断标题；
 * - 与 [MemoryStore] 同款「低预算单请求」模式，不进入主生成循环，也不占工具轮次。
 */
@Singleton
class AutoTitler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val settingsRepository: SettingsRepository,
    private val providers: Map<ProviderId, @JvmSuppressWildcards LlmProvider>,
) {
    /** 是否到了值得自动命名的时机：首轮助手回复成功、且标题仍是默认值。 */
    suspend fun shouldTitle(conversationId: Long): Boolean {
        if (!settingsRepository.autoTitleEnabled()) return false
        val conversation = conversationDao.get(conversationId) ?: return false
        val done = messageDao.recentDone(conversationId, TITLE_TURNS)
        if (done.none { it.role == Role.ASSISTANT.name }) return false
        // 首条用户消息决定「截断标题」长什么样：生成流程写进去的就是它。
        val firstUser = done.asReversed().firstOrNull { it.role == Role.USER.name }?.content.orEmpty()
        return AutoTitle.needsTitle(conversation.title, firstUser)
    }

    /**
     * 生成并写入标题。
     *
     * @return 写入的标题；null 表示开关关闭、时机不对或失败的静默放弃。
     */
    suspend fun maybeTitle(
        conversationId: Long,
        provider: ProviderId,
        model: String,
    ): String? {
        if (!shouldTitle(conversationId)) return null
        val history = messageDao.recentDone(conversationId, TITLE_TURNS).asReversed()
        val firstUser = history.firstOrNull { it.role == Role.USER.name }?.content.orEmpty()
        if (firstUser.isBlank()) return null
        val transcript = history.joinToString("\n") { message ->
            val who = if (message.role == Role.USER.name) "用户" else "助手"
            who + "：" + message.content.take(300)
        }
        // 提示词跟随界面语言：中文环境下模型容易输出英文标题。
        val language = context.getString(R.string.auto_title_language)
        val request = ChatRequest(
            model = model,
            messages = listOf(
                WireMessage(Role.SYSTEM, AutoTitle.prompt(language)),
                WireMessage(Role.USER, transcript),
            ),
            maxOutputTokens = TITLE_MAX_OUTPUT,
            thinking = false,
        )
        val config = settingsRepository.get(provider)
        val llm = providers[provider] ?: return null
        val raw = StringBuilder()
        // 思考型模型可能整轮只出 reasoning、正文为空：想到的标题也得能用，
        // 否则「自动标题」在这类模型上会静默失效（实测豆包 Seed 2.1 Turbo 就会这样）。
        val thinking = StringBuilder()
        withTimeoutOrNull(TITLE_TIMEOUT_MS) {
            runCatching {
                llm.stream(apiKey = config.apiKey, baseUrl = config.baseUrl, request = request)
                    .collect { event ->
                        when (event) {
                            is StreamEvent.Delta -> raw.append(event.text)
                            is StreamEvent.Reasoning -> thinking.append(event.text)
                            else -> Unit
                        }
                    }
            }
        }
        // 正文为空时退到思考的最后一行：那里通常就是模型自己写下的结论。
        val title = AutoTitle.clean(raw.toString())
            ?: AutoTitle.clean(
                thinking.toString().lineSequence().lastOrNull { it.isNotBlank() }.orEmpty(),
            )
            ?: return null
        // 二次确认：生成期间用户可能已经手动改名。
        val latest = conversationDao.get(conversationId) ?: return null
        if (!AutoTitle.needsTitle(latest.title, firstUser)) return null
        conversationDao.rename(conversationId, title)
        return title
    }

    /** 用首条消息写一条兜底标题（AI 关闭或失败时也能扫读）。 */
    suspend fun fallbackTitle(conversationId: Long): String? {
        val conversation = conversationDao.get(conversationId) ?: return null
        // recentDone 是倒序，兜底标题要用最早的一条用户消息。
        val firstUser = messageDao.recentDone(conversationId, TITLE_TURNS).asReversed()
            .firstOrNull { it.role == Role.USER.name }?.content.orEmpty()
        if (!AutoTitle.needsTitle(conversation.title, firstUser)) return null
        val title = AutoTitle.fallback(firstUser).ifBlank { return null }
        conversationDao.rename(conversationId, title)
        return title
    }

    private companion object {
        /** 首轮判断回看条数：用户 + 助手，留一点余量。 */
        const val TITLE_TURNS = 4
        /**
         * 输出预算。
         *
         * 不能压到 48：豆包的思考型模型会先把预算烧在思考上，正文与思考流都可能为空
         * （实测 Seed 2.1 Turbo）。256 足够「思考 + 一行标题」，仍然很便宜。
         */
        const val TITLE_MAX_OUTPUT = 256
        /**
         * 超时。
         *
         * 8 秒对思考型模型太紧：实测豆包 Seed 2.1 Turbo 生成一行标题要 11.9 秒，
         * 超时会让自动标题在中高端模型上全部静默失败。20 秒仍在后台，不影响对话。
         */
        const val TITLE_TIMEOUT_MS = 20_000L
    }
}
