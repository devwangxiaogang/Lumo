package com.yuguang.lumo.data.remote.free

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.yuguang.lumo.R
import com.yuguang.lumo.data.remote.EndpointUrls
import com.yuguang.lumo.data.remote.OpenAiCompatibleClient
import com.yuguang.lumo.domain.model.FREE_AUDIO_MODEL
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.StreamEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 免费语音识别。
 *
 * 两条路：
 * 1. 设备自带识别器（`SpeechRecognizer`）——多数国产 ROM 没装识别服务，先探测再缓存结论；
 * 2. 免费池的 omni 模型走 `input_audio`——实测能把中文语音转成汉字，但准确率明显低于豆包，
 *    因此定位是「试验级兜底」，UI 会常驻一行说明。
 */
@Singleton
class FreeAsrClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val client: OpenAiCompatibleClient,
    private val json: Json,
) {
    @Volatile
    private var systemAsrAvailable: Boolean? = null

    /**
     * @param wav 16 kHz / 16 bit / 单声道 WAV（与豆包链路同一份采集结果）。
     * @param language 应用内部语种码（zh/en/ja/ko/ru）。
     */
    suspend fun transcribe(wav: ByteArray, language: String): String {
        systemTranscribe(wav, language)?.takeIf { it.isNotBlank() }?.let { return it }
        llmTranscribe(wav, language)?.takeIf { it.isNotBlank() }?.let { return it }
        error(context.getString(R.string.free_asr_failed))
    }

    /** 设备识别器：把 WAV 的 PCM 部分交给系统识别器（它只要 16k 单声道裸 PCM 也能吃）。 */
    private suspend fun systemTranscribe(wav: ByteArray, language: String): String? {
        if (systemAsrAvailable == false) return null
        val available = systemAsrAvailable ?: SpeechRecognizer.isRecognitionAvailable(context).also {
            systemAsrAvailable = it
        }
        if (!available) return null
        val pcm = wavPcm(wav)
        if (pcm.isEmpty()) return null
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(SYSTEM_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    var resumed = false
                    fun finish(text: String?) {
                        if (resumed) return
                        resumed = true
                        runCatching { recognizer.destroy() }
                        if (continuation.isActive) continuation.resume(text)
                    }
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onResults(results: Bundle?) {
                            val text = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                            finish(text)
                        }

                        override fun onError(error: Int) = finish(null)
                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onPartialResults(partialResults: Bundle?) = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47(language))
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    }
                    runCatching { recognizer.startListening(intent) }
                        .onFailure { finish(null) }
                    continuation.invokeOnCancellation {
                        runCatching { recognizer.destroy() }
                    }
                }
            }
        }
    }

    /** 免费池 omni 模型：base64 WAV 走 input_audio。 */
    private suspend fun llmTranscribe(wav: ByteArray, language: String): String? = withContext(Dispatchers.IO) {
        val encoded = Base64.encodeToString(wav, Base64.NO_WRAP)
        val body = buildJsonObject {
            put("model", FREE_AUDIO_MODEL)
            put("stream", true)
            putJsonObject("stream_options") { put("include_usage", true) }
            putJsonObject("reasoning") { put("enabled", false) }
            put("max_completion_tokens", 1_024)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", context.getString(R.string.free_asr_prompt))
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", context.getString(R.string.free_asr_hint, languageName(language)))
                        }
                        addJsonObject {
                            put("type", "input_audio")
                            putJsonObject("input_audio") {
                                put("data", encoded)
                                put("format", "wav")
                            }
                        }
                    }
                }
            }
        }
        val collected = StringBuilder()
        withTimeoutOrNull(ASR_TIMEOUT_MS) {
            client.stream(
                url = EndpointUrls.chatCompletions(ProviderId.FREE.defaultBaseUrl, ProviderId.FREE),
                apiKey = "",
                body = body,
                headers = mapOf("HTTP-Referer" to "https://kilo.ai/", "X-Title" to "Lumo"),
            ).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> collected.append(event.text)
                    else -> Unit
                }
            }
        }
        clean(collected.toString())
    }

    /** 模型可能返回 `[no speech]`、拼音串或带引号的结果，这里统一清洗。 */
    private fun clean(raw: String): String? {
        var text = raw.trim().trim('"', '“', '”', '\'', '。')
        if (text.isBlank()) return null
        if (text.equals("[no speech]", ignoreCase = true) || text.contains("no speech", ignoreCase = true)) {
            return null
        }
        // 全 ASCII 且带空格的结果多为拼音串，视为转写失败。
        if (text.all { it.code < 128 } && text.count { it == ' ' } >= 2) return null
        return text
    }

    private fun wavPcm(wav: ByteArray): ByteArray {
        if (wav.size <= 44) return ByteArray(0)
        // 标准 44 字节头；如果不是 RIFF，就当作裸 PCM 直接用。
        if (wav[0] == 'R'.code.toByte() && wav[1] == 'I'.code.toByte()) {
            return wav.copyOfRange(44, wav.size)
        }
        return wav
    }

    private fun bcp47(language: String): String = when (language) {
        "zh" -> "zh-CN"
        "en" -> "en-US"
        "ja" -> "ja-JP"
        "ko" -> "ko-KR"
        "ru" -> "ru-RU"
        else -> "zh-CN"
    }

    private fun languageName(language: String): String = when (language) {
        "zh" -> "简体中文"
        "en" -> "English"
        "ja" -> "日本語"
        "ko" -> "한국어"
        "ru" -> "Русский"
        else -> "简体中文"
    }

    private companion object {
        const val SYSTEM_TIMEOUT_MS = 15_000L
        const val ASR_TIMEOUT_MS = 25_000L
    }
}
