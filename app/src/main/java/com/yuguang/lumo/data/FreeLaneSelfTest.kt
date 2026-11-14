package com.yuguang.lumo.data

import android.content.Context
import com.yuguang.lumo.R
import com.yuguang.lumo.data.remote.EndpointUrls
import com.yuguang.lumo.data.remote.OpenAiCompatibleClient
import com.yuguang.lumo.data.remote.free.FreeTtsClient
import com.yuguang.lumo.data.remote.free.FreeTranslateClient
import com.yuguang.lumo.domain.model.ChatRequest
import com.yuguang.lumo.domain.model.FreeTtsBackend
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.domain.model.StreamEvent
import com.yuguang.lumo.domain.model.VoiceChoice
import com.yuguang.lumo.domain.model.WireMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** 自检中某一项的结果。 */
data class SelfTestItem(
    val label: String,
    val ok: Boolean,
    val detail: String,
    val elapsedMs: Long,
)

/**
 * 免费链路自检。
 *
 * 依次探测「对话 / 翻译 / 朗读」三个免费部件，各自给一句结论。
 * 目的是让用户在一处看完「到底哪一环不通」，而不是逐页试错。
 */
@Singleton
class FreeLaneSelfTest @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OpenAiCompatibleClient,
    private val translator: FreeTranslateClient,
    private val tts: FreeTtsClient,
) {
    suspend fun run(): List<SelfTestItem> = listOf(
        chatTest(context.getString(R.string.free_self_test_chat)),
        translateTest(context.getString(R.string.free_self_test_translate)),
        ttsTest(context.getString(R.string.free_self_test_tts)),
    )

    private suspend fun chatTest(label: String): SelfTestItem = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val body = buildJsonObject {
            put("model", ProviderId.FREE.defaultModel)
            put("stream", true)
            putJsonObject("stream_options") { put("include_usage", true) }
            putJsonObject("reasoning") { put("enabled", false) }
            put("max_completion_tokens", 32)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "Reply with the single word: OK")
                })
            }
        }
        val text = StringBuilder()
        val failure = StringBuilder()
        val done = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            client.stream(
                url = EndpointUrls.chatCompletions(ProviderId.FREE.defaultBaseUrl, ProviderId.FREE),
                apiKey = "",
                body = body,
                headers = mapOf("HTTP-Referer" to "https://kilo.ai/", "X-Title" to "Lumo"),
            ).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> text.append(event.text)
                    is StreamEvent.Failed -> failure.append(event.message)
                    else -> Unit
                }
            }
            true
        }
        val elapsed = System.currentTimeMillis() - start
        when {
            done == null -> fail(label, context.getString(R.string.free_self_timeout), elapsed)
            failure.isNotBlank() -> fail(label, failure.toString(), elapsed)
            else -> SelfTestItem(
                label = label,
                ok = true,
                detail = context.getString(R.string.free_self_test_ok, label, elapsed),
                elapsedMs = elapsed,
            )
        }
    }

    private suspend fun translateTest(label: String): SelfTestItem {
        val start = System.currentTimeMillis()
        val result = runCatching {
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                translator.translate("你好", "zh", "en")
            }
        }.getOrNull()
        val elapsed = System.currentTimeMillis() - start
        return if (result.isNullOrBlank()) {
            fail(label, context.getString(R.string.free_translate_failed), elapsed)
        } else {
            SelfTestItem(label, true, context.getString(R.string.free_self_test_ok, label, elapsed), elapsed)
        }
    }

    private suspend fun ttsTest(label: String): SelfTestItem {
        val start = System.currentTimeMillis()
        val speech = runCatching {
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                tts.synthesize(
                    text = "测试",
                    lang = "zh",
                    speed = 0,
                    voice = VoiceChoice.FEMALE,
                    backend = FreeTtsBackend.AUTO,
                )
            }
        }.getOrNull()
        val elapsed = System.currentTimeMillis() - start
        return if (speech == null || speech.pcm.isEmpty()) {
            fail(label, context.getString(R.string.free_tts_failed), elapsed)
        } else {
            SelfTestItem(label, true, context.getString(R.string.free_self_test_ok, label, elapsed), elapsed)
        }
    }

    private fun fail(label: String, reason: String, elapsed: Long) = SelfTestItem(
        label = label,
        ok = false,
        detail = context.getString(R.string.free_self_test_fail, label, reason),
        elapsedMs = elapsed,
    )

    private companion object {
        const val PROBE_TIMEOUT_MS = 25_000L
    }
}
