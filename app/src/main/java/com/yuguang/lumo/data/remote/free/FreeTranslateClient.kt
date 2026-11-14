package com.yuguang.lumo.data.remote.free

import android.content.Context
import com.yuguang.lumo.R
import com.yuguang.lumo.data.remote.EndpointUrls
import com.yuguang.lumo.data.remote.OpenAiCompatibleClient
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.StreamEvent
import com.yuguang.lumo.domain.model.freeTranslateChain
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.domain.model.WireMessage
import com.yuguang.lumo.domain.model.ChatRequest
import com.yuguang.lumo.domain.model.freeTtsSupports
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 免费翻译：先让免费池的大模型直译，失败再用 MyMemory 的匿名接口兜底。
 *
 * 实测（2026-11）：四个免费模型对中→英的翻译全部准确；MyMemory 匿名额度有限，
 * 只作为 429/超时后的第二选择。语种边界与朗读一致：只有 zh/en/ja/ko/ru。
 */
@Singleton
class FreeTranslateClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OpenAiCompatibleClient,
    private val http: OkHttpClient,
    private val json: Json,
) {
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String = withContext(Dispatchers.IO) {
        val content = text.trim()
        if (content.isEmpty()) return@withContext ""
        val source = sourceLanguage.ifBlank { "zh" }
        val target = targetLanguage.ifBlank { "en" }
        if (!freeTtsSupports(source) || !freeTtsSupports(target)) {
            error(context.getString(R.string.free_translate_limit))
        }
        llmTranslate(content, source, target)?.let { return@withContext it }
        myMemory(content, source, target)?.let { return@withContext it }
        error(context.getString(R.string.free_translate_failed))
    }

    /** 让免费池模型直译。逐个模型尝试，第一个出正文的就算成功。 */
    private suspend fun llmTranslate(text: String, source: String, target: String): String? {
        for (model in freeTranslateChain) {
            val request = ChatRequest(
                model = model,
                messages = listOf(
                    WireMessage(Role.SYSTEM, systemPrompt(target)),
                    WireMessage(Role.USER, text),
                ),
                maxOutputTokens = 1_024,
            )
            val buffer = StringBuilder()
            runCatching {
                client.stream(
                    url = EndpointUrls.chatCompletions(ProviderId.FREE.defaultBaseUrl, ProviderId.FREE),
                    apiKey = "",
                    body = translateBody(request),
                    headers = ATTRIBUTION,
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Delta -> buffer.append(event.text)
                        else -> Unit
                    }
                }
            }
            val out = buffer.toString().trim().trim('"', '“', '”', '\n')
            if (out.isNotBlank() && !looksLikeRefusal(out)) return out
        }
        return null
    }

    /** MyMemory 匿名接口：`langpair=zh-CN|en`，失败返回 null。 */
    private fun myMemory(text: String, source: String, target: String): String? {
        val pair = "${myMemoryCode(source)}|${myMemoryCode(target)}"
        val url = "https://api.mymemory.translated.net/get?q=${encode(text)}&langpair=${encode(pair)}"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val raw = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
                val data = root["responseData"] as? JsonObject ?: return null
                val translated = data["translatedText"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val status = (root["responseStatus"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.jsonPrimitive?.contentOrNull
                if (status != null && status != "200") return null
                translated.trim().takeIf { it.isNotBlank() && !looksLikeRefusal(it) }
            }
        }.getOrNull()
    }

    /** 模型偶尔会回「我无法翻译…」，这类结果不算成功。 */
    private fun looksLikeRefusal(text: String): Boolean {
        val blob = text.lowercase()
        return blob.startsWith("i cannot") ||
            blob.startsWith("i'm sorry") ||
            blob.startsWith("抱歉") ||
            blob.contains("无法翻译") ||
            blob.contains("as an ai")
    }

    private fun systemPrompt(target: String): String = buildString {
        append("You are a professional translator. Translate the user's text into ")
        append(languageName(target))
        append(". Output ONLY the translation, without quotes, notes, or explanations.")
    }

    private fun languageName(code: String): String = when (code) {
        "zh" -> "Simplified Chinese"
        "en" -> "English"
        "ja" -> "Japanese"
        "ko" -> "Korean"
        "ru" -> "Russian"
        else -> "English"
    }

    private fun myMemoryCode(code: String): String = when (code) {
        "zh" -> "zh-CN"
        "en" -> "en-GB"
        "ja" -> "ja-JP"
        "ko" -> "ko-KR"
        "ru" -> "ru-RU"
        else -> "en-GB"
    }

    private fun translateBody(request: ChatRequest): JsonObject = buildJsonObject {
        put("model", request.model)
        put("stream", true)
        putJsonObject("reasoning") { put("enabled", false) }
        put("max_completion_tokens", request.maxOutputTokens)
        putJsonArray("messages") {
            request.messages.forEach { message ->
                addJsonObject {
                    put("role", if (message.role == Role.SYSTEM) "system" else "user")
                    put("content", message.text)
                }
            }
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13)"
        val ATTRIBUTION = mapOf(
            "HTTP-Referer" to "https://kilo.ai/",
            "X-Title" to "Lumo",
        )
    }
}
