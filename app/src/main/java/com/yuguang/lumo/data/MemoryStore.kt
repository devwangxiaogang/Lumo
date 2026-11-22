package com.yuguang.lumo.data

import android.content.Context
import com.yuguang.lumo.R
import com.yuguang.lumo.data.local.MemoryDao
import com.yuguang.lumo.data.local.MemoryEntity
import com.yuguang.lumo.domain.LlmProvider
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.ChatRequest
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.domain.model.StreamEvent
import com.yuguang.lumo.domain.model.WireMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 长期记忆：回合结束后异步把「值得记住的偏好/事实」提炼成短句存本地。
 *
 * 设计取舍：
 * - 默认关闭（涉及把个人偏好发给第三方），用户显式开启才运行；
 * - 用当前会话模型做一次低预算请求，失败/超时静默放弃，绝不打断对话；
 * - 相似度去重 + 总量上限，避免同一件事反复追加。
 */
@Singleton
class MemoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDao: MemoryDao,
    private val settingsRepository: SettingsRepository,
    private val providers: Map<ProviderId, @JvmSuppressWildcards LlmProvider>,
    private val json: Json,
) {
    fun observe(): Flow<List<MemoryEntity>> = memoryDao.observeAll()

    suspend fun add(content: String, sourceConversationId: Long = 0): Boolean {
        val clean = content.trim().take(MAX_CONTENT)
        if (clean.isBlank()) return false
        val existing = memoryDao.allContents().map(::normalize)
        if (existing.any { similar(it, normalize(clean)) }) return false
        memoryDao.insert(
            MemoryEntity(
                content = clean,
                sourceConversationId = sourceConversationId,
                createdAt = System.currentTimeMillis(),
            ),
        )
        memoryDao.trimTo(MAX_ENTRIES)
        return true
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = memoryDao.setEnabled(id, enabled)

    suspend fun delete(id: Long) = memoryDao.delete(id)

    /** 一次性清空全部记忆（设置页「清空全部」用），返回实际删除条数。 */
    suspend fun clearAll(): Int {
        val count = memoryDao.allContents().size
        memoryDao.clear()
        return count
    }

    /** 注入给模型的记忆块；开关关闭或没有记忆时返回空串。 */
    suspend fun promptBlock(): String {
        if (!settingsRepository.memoryEnabled()) return ""
        val rows = memoryDao.active(INJECT_LIMIT)
        if (rows.isEmpty()) return ""
        val joined = rows.joinToString("\n") { "- ${it.content}" }.take(INJECT_CHARS)
        return context.getString(R.string.memory_prompt_header) + "\n" + joined
    }

    /**
     * 从一段对话里提炼记忆。调用方负责判断「是否到了提炼的时机」。
     *
     * @return 写入的条数；0 表示没有新记忆、开关关闭或提炼失败。
     */
    suspend fun extract(
        conversationId: Long,
        provider: ProviderId,
        model: String,
        transcript: List<WireMessage>,
    ): Int {
        if (!settingsRepository.memoryEnabled()) return 0
        if (transcript.isEmpty()) return 0
        val config = settingsRepository.get(provider)
        val llm = providers[provider] ?: return 0
        val dialog = transcript
            .filter { it.role == Role.USER || it.role == Role.ASSISTANT }
            .takeLast(EXTRACT_TURNS)
            .joinToString("\n") { message ->
                val who = if (message.role == Role.USER) "用户" else "助手"
                "$who：${message.text.take(400)}"
            }
        if (dialog.isBlank()) return 0
        val request = ChatRequest(
            model = model,
            messages = listOf(
                WireMessage(Role.SYSTEM, context.getString(R.string.memory_extract_prompt)),
                WireMessage(Role.USER, dialog),
            ),
            maxOutputTokens = 256,
        )
        val raw = StringBuilder()
        val collected = withTimeoutOrNull(EXTRACT_TIMEOUT_MS) {
            // 取消必须穿透：用户已切走会话时不该继续往本机写记忆条目。
            try {
                llm.stream(apiKey = config.apiKey, baseUrl = config.baseUrl, request = request)
                    .collect { event ->
                        when (event) {
                            is StreamEvent.Delta -> raw.append(event.text)
                            is StreamEvent.Failed -> Unit
                            else -> Unit
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 提炼失败不影响对话本身，按「没提炼出东西」处理。
            }
            raw.toString()
        } ?: return 0
        var written = 0
        parseCandidates(collected).forEach { candidate ->
            if (add(candidate, conversationId)) written++
        }
        return written
    }

    /** 模型可能返回数组、也可能带 ```json 围栏，这里都容忍。 */
    private fun parseCandidates(raw: String): List<String> {
        val text = raw.substringAfter("```json", raw)
            .substringBefore("```")
            .trim()
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        return when (element) {
            is JsonArray -> element.mapNotNull { item -> primitiveOrNull(item) }
            is JsonPrimitive -> listOfNotNull(primitiveOrNull(element))
            else -> emptyList()
        }.map { it.trim() }.filter { it.isNotBlank() }.take(MAX_PER_ROUND)
    }

    private fun primitiveOrNull(element: JsonElement): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** 归一化：小写、去标点空白，只为比较。 */
    private fun normalize(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    /** 字符二元组 Jaccard 相似度，阈值以上视为重复。 */
    private fun similar(left: String, right: String): Boolean {
        if (left.isEmpty() || right.isEmpty()) return false
        if (left == right) return true
        if (left.length < 4 || right.length < 4) return left.contains(right) || right.contains(left)
        val a = bigrams(left)
        val b = bigrams(right)
        val shared = a.count { it in b }
        val union = a.size + b.size - shared
        return union > 0 && shared.toDouble() / union >= SIMILARITY
    }

    private fun bigrams(text: String): Set<String> =
        text.windowed(2).toSet()

    private companion object {
        const val MAX_ENTRIES = 200
        const val MAX_PER_ROUND = 3
        const val MAX_CONTENT = 60
        const val INJECT_LIMIT = 20
        const val INJECT_CHARS = 1_500
        const val EXTRACT_TURNS = 8
        const val EXTRACT_TIMEOUT_MS = 20_000L
        const val SIMILARITY = 0.85
    }
}
