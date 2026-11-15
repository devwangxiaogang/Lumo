package com.yuguang.lumo.data

import android.content.Context
import com.yuguang.lumo.R
import com.yuguang.lumo.data.remote.EndpointUrls
import com.yuguang.lumo.data.remote.OpenAiCompatibleClient
import com.yuguang.lumo.data.remote.free.FreeTtsClient
import com.yuguang.lumo.data.remote.free.FreeTranslateClient
import com.yuguang.lumo.domain.model.ChatRequest
import com.yuguang.lumo.domain.model.FreeTtsBackend
import com.yuguang.lumo.domain.model.freeFallbackChain
import com.yuguang.lumo.domain.model.freeForcedReasoning
import com.yuguang.lumo.domain.model.freeVisionChain
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.domain.model.StreamEvent
import com.yuguang.lumo.domain.model.VoiceChoice
import com.yuguang.lumo.domain.model.WireMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val health: FreeModelHealth,
) {
    suspend fun run(): List<SelfTestItem> {
        val items = mutableListOf(
            chatTest(context.getString(R.string.free_self_test_chat)),
            translateTest(context.getString(R.string.free_self_test_translate)),
            ttsTest(context.getString(R.string.free_self_test_tts)),
        )
        items += modelProbes()
        return items
    }

    /**
     * 逐个探测免费池里的模型（含远端目录里新出现、静态清单没有的那些）。
     *
     * 上游的可用性变化比发版快：这里的探测结果同时写进 [FreeModelHealth]，
     * 后续请求会自动跳过失败的模型，而不是每次都撞同一堵墙。
     *
     * 并发探测并限制单个模型 12 秒、整批 20 秒：自检是给人看的，
     * 不能因为一个坏模型把整张表拖成两分钟。
     */
    private suspend fun modelProbes(): List<SelfTestItem> = withContext(Dispatchers.IO) {
        val candidates = buildList {
            addAll(freeFallbackChain)
            addAll(freeVisionChain)
            add(ProviderId.FREE.defaultModel)
        }.filter { it.isNotBlank() }.distinct().take(MAX_PROBED_MODELS)
        withTimeoutOrNull(BATCH_TIMEOUT_MS) {
            coroutineScope {
                candidates.map { model -> async { probeModel(model) } }.awaitAll()
            }
        } ?: emptyList()
    }

    private suspend fun probeModel(model: String): SelfTestItem {
        val start = System.currentTimeMillis()
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            putJsonObject("stream_options") { put("include_usage", true) }
            // 强制思考的模型不能带这个字段，否则一律 400。
            if (model !in freeForcedReasoning) {
                putJsonObject("reasoning") { put("enabled", false) }
            }
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
        val finished = withTimeoutOrNull(MODEL_PROBE_TIMEOUT_MS) {
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
        val label = shortModelLabel(model)
        return when {
            finished == null -> {
                val reason = context.getString(R.string.free_self_timeout)
                health.recordFailure(model, reason)
                fail(label, reason, elapsed)
            }
            failure.isNotBlank() -> {
                health.recordFailure(model, failure.toString())
                fail(label, failure.toString(), elapsed)
            }
            else -> {
                health.recordSuccess(model)
                SelfTestItem(
                    label = label,
                    ok = true,
                    detail = context.getString(R.string.free_self_test_ok, label, elapsed),
                    elapsedMs = elapsed,
                )
            }
        }
    }

    // 短名规则见 data/ModelLabel.kt：自检列表与模型健康卡片共用同一份实现。

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

        /** 单模型探测的超时：比整体自检短，避免一个坏模型拖住整张表。 */
        const val MODEL_PROBE_TIMEOUT_MS = 12_000L

        /** 整批探测的总预算：并发执行，超时后已完成的照常显示。 */
        const val BATCH_TIMEOUT_MS = 20_000L

        /** 最多探测多少个模型：再多一屏也放不下。 */
        const val MAX_PROBED_MODELS = 8
    }
}
