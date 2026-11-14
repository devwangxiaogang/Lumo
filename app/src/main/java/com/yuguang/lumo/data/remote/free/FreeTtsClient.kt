package com.yuguang.lumo.data.remote.free

import android.content.Context
import com.yuguang.lumo.R
import com.yuguang.lumo.data.audio.Mp3Decoder
import com.yuguang.lumo.domain.model.FreeTtsBackend
import com.yuguang.lumo.domain.model.VoiceChoice
import com.yuguang.lumo.domain.model.freeTtsSupports
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 免费朗读：中文/英/日/韩/俄 走两个公开网页端点，其余一律交给本机 TTS。
 *
 * 实测（2026-11）：
 * - 百度 `gettts` 无需 Key、无需 Referer，MP3 @16 kHz，单次 ≤400 字，20 连发不限流；
 * - 有道 `dictvoice` 同语种备选，`type=1` 英音 / `type=2` 美音，长度上限更紧。
 *
 * 两者都是**网页接口**而非官方 API，随时可能变动，所以后端可覆盖、可切换，
 * 且失败一定落回本机朗读，绝不让用户看到「点了没反应」。
 */
@Singleton
class FreeTtsClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) {
    data class Speech(val pcm: ByteArray, val sampleRate: Int)

    /**
     * @return 解码后的 PCM；语种不支持或所有后端失败时返回 null。
     */
    suspend fun synthesize(
        text: String,
        lang: String,
        speed: Int,
        voice: VoiceChoice,
        backend: FreeTtsBackend,
    ): Speech? = withContext(Dispatchers.IO) {
        val content = text.trim()
        if (content.isEmpty()) return@withContext null
        if (!freeTtsSupports(lang)) return@withContext null
        val order = when (backend) {
            FreeTtsBackend.BAIDU -> listOf(Source.BAIDU)
            FreeTtsBackend.YOUDAO -> listOf(Source.YOUDAO)
            // AUTO 与 SYSTEM：SYSTEM 由调用方直接走本机，这里只服务 AUTO。
            FreeTtsBackend.AUTO, FreeTtsBackend.SYSTEM -> listOf(Source.BAIDU, Source.YOUDAO)
        }
        val chunks = chunk(content)
        val pcm = java.io.ByteArrayOutputStream()
        var sampleRate = 16_000
        for (chunk in chunks) {
            val audio = fetchWithFallback(chunk, lang, speed, voice, order) ?: return@withContext null
            val decoded = Mp3Decoder.decode(audio) ?: return@withContext null
            sampleRate = decoded.sampleRate
            pcm.write(decoded.bytes)
        }
        val bytes = pcm.toByteArray()
        if (bytes.isEmpty()) null else Speech(bytes, sampleRate)
    }

    private fun fetchWithFallback(
        text: String,
        lang: String,
        speed: Int,
        voice: VoiceChoice,
        order: List<Source>,
    ): ByteArray? {
        for (source in order) {
            val bytes = runCatching { fetch(source, text, lang, speed, voice) }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) return bytes
        }
        return null
    }

    private fun fetch(
        source: Source,
        text: String,
        lang: String,
        speed: Int,
        voice: VoiceChoice,
    ): ByteArray {
        val url = when (source) {
            Source.BAIDU -> buildString {
                append("https://fanyi.baidu.com/gettts?lan=")
                append(baiduLang(lang))
                append("&text=")
                append(encode(text))
                append("&spd=")
                append(baiduSpeed(speed))
                // 男声只有一档（per=3），女声用默认音色。
                if (voice == VoiceChoice.MALE) append("&per=3")
                append("&source=web")
            }
            Source.YOUDAO -> buildString {
                append("https://dict.youdao.com/dictvoice?le=")
                append(youdaoLang(lang))
                append("&audio=")
                append(encode(text))
                if (lang == "en") append("&type=").append(if (voice == VoiceChoice.MALE) 2 else 1)
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(context.getString(R.string.free_tts_failed))
            }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (bytes.isEmpty()) error(context.getString(R.string.tts_no_audio))
            return bytes
        }
    }

    /** 端点按句读切块：单块 ≤400 字是实测的稳定上限。 */
    private fun chunk(text: String): List<String> {
        if (text.length <= MAX_CHUNK) return listOf(text)
        val parts = mutableListOf<String>()
        var rest = text
        while (rest.isNotEmpty()) {
            if (rest.length <= MAX_CHUNK) {
                parts += rest
                break
            }
            val window = rest.substring(0, MAX_CHUNK)
            val cut = listOf('。', '！', '？', '\n', '.', '!', '?', '；', ';')
                .maxOf { window.lastIndexOf(it) }
            val end = if (cut >= 40) cut + 1 else MAX_CHUNK
            parts += rest.substring(0, end).trim()
            rest = rest.substring(end).trimStart()
        }
        return parts.filter { it.isNotBlank() }
    }

    /** 应用语种码 → 百度 lan 码。只映射实测通过的五个。 */
    private fun baiduLang(lang: String): String = when (lang) {
        "ja" -> "jp"
        "zh", "en", "ko", "ru" -> lang
        else -> "zh"
    }

    /** 应用语种码 → 有道 le 码。 */
    private fun youdaoLang(lang: String): String = when (lang) {
        "zh", "en", "ja", "ko", "ru" -> lang
        else -> "zh"
    }

    /** 设置里的 -50..100 映射到百度 spd 的 1..7（实测范围）。 */
    private fun baiduSpeed(speed: Int): Int {
        val mapped = 5 - speed / 25
        return mapped.coerceIn(1, 7)
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    private enum class Source { BAIDU, YOUDAO }

    private companion object {
        const val MAX_CHUNK = 400
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
