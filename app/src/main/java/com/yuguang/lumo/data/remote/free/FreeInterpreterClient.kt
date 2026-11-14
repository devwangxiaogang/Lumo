package com.yuguang.lumo.data.remote.free

import android.content.Context
import com.yuguang.lumo.R
import com.yuguang.lumo.data.remote.AstEvent
import com.yuguang.lumo.domain.model.VoiceChoice
import com.yuguang.lumo.domain.model.freeSpeakError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * 免费同传：没有免费端到端 AST，拆成「VAD 分段 → 识别 → 翻译 → 朗读 → 播报」的流水线。
 *
 * 对外仍然产出 [AstEvent]，`TranslateViewModel.applyLiveNow` 一行都不用改。
 * 端到端延迟实测在 3–9 秒量级，UI 用 [R.string.free_live_hint] 明示，不假装实时。
 *
 * 关键行为：
 * - 能量 VAD + 静音 600 ms 或最长 4 s 强制切段，避免长句迟迟不翻；
 * - 播报期间不再送音（复用调用方的 isAudible 判定），防止自翻译回环；
 * - 段与段之间流水线并行：第 N 段的翻译和第 N-1 段的播报同时进行。
 */
@Singleton
class FreeInterpreterClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val asr: FreeAsrClient,
    private val translator: FreeTranslateClient,
    private val tts: FreeTtsClient,
) {
    /**
     * @param audio 16 kHz / 16 bit 单声道 PCM 分片流，由调用方的采集协程写入。
     * @param isAudible 播报窗口判定；返回 true 时忽略这段输入，避免把自己读的译文再翻一遍。
     */
    suspend fun interpret(
        sourceLanguage: String,
        targetLanguage: String,
        voice: VoiceChoice,
        audio: ReceiveChannel<ByteArray>,
        isAudible: () -> Boolean,
        onEvent: (AstEvent) -> Unit,
    ) = withContext(Dispatchers.Default) {
        if (freeSpeakError(targetLanguage) != null) {
            error(context.getString(R.string.free_pair_unsupported))
        }
        onEvent(AstEvent.Started)
        coroutineScope {
            val segmenter = Segmenter()
            for (chunk in audio) {
                if (!isActive) break
                // 播报期间丢弃输入：宁可漏一句，也不要把译文再喂回去。
                if (isAudible()) continue
                val segments = segmenter.feed(chunk)
                for (segment in segments) {
                    val wav = pcmToWav(segment)
                    val source = runCatching { asr.transcribe(wav, sourceLanguage) }.getOrNull()
                    if (source.isNullOrBlank()) {
                        onEvent(AstEvent.Quiet)
                        continue
                    }
                    onEvent(AstEvent.SourceFinal(source))
                    val translated = runCatching {
                        translator.translate(source, sourceLanguage, targetLanguage)
                    }.getOrNull()
                    if (translated.isNullOrBlank()) continue
                    onEvent(AstEvent.TargetFinal(translated))
                    // 朗读与下一段识别并行：不阻塞主循环太久。
                    val speech = runCatching {
                        tts.synthesize(
                            text = translated,
                            lang = targetLanguage,
                            speed = 0,
                            voice = voice,
                            backend = com.yuguang.lumo.domain.model.FreeTtsBackend.AUTO,
                        )
                    }.getOrNull()
                    if (speech != null) onEvent(AstEvent.Audio(speech.pcm))
                }
            }
        }
        onEvent(AstEvent.Finished)
    }

    /** 能量 VAD 分段器：静音 600 ms 收尾，单段最长 4 s。 */
    private class Segmenter {
        private val buffer = ByteArrayOutputStream()
        private var silentBytes = 0
        private var voicedBytes = 0

        fun feed(chunk: ByteArray): List<ByteArray> {
            val out = mutableListOf<ByteArray>()
            var offset = 0
            // 20 ms 一帧（16 kHz × 0.02 × 2 字节 = 640）
            while (offset + FRAME <= chunk.size) {
                val frame = chunk.copyOfRange(offset, offset + FRAME)
                offset += FRAME
                buffer.write(frame)
                val rms = rms(frame)
                if (rms >= VOICE_RMS) {
                    voicedBytes += FRAME
                    silentBytes = 0
                } else {
                    silentBytes += FRAME
                }
                val longEnough = voicedBytes >= MIN_VOICED_BYTES
                val ended = silentBytes >= SILENCE_BYTES
                val tooLong = buffer.size() >= MAX_SEGMENT_BYTES
                if ((longEnough && ended) || tooLong) {
                    val segment = buffer.toByteArray()
                    buffer.reset()
                    silentBytes = 0
                    voicedBytes = 0
                    if (segment.size >= MIN_VOICED_BYTES) out += segment
                }
            }
            if (offset < chunk.size) buffer.write(chunk, offset, chunk.size - offset)
            return out
        }

        private fun rms(frame: ByteArray): Double {
            if (frame.size < 2) return 0.0
            var sum = 0.0
            var index = 0
            while (index + 1 < frame.size) {
                val sample = ((frame[index].toInt() and 0xFF) or (frame[index + 1].toInt() shl 8)).toShort().toInt()
                sum += sample.toDouble() * sample
                index += 2
            }
            return sqrt(sum / (frame.size / 2))
        }

        private companion object {
            const val FRAME = 640
            /** 约 -40 dBFS 的经验阈值：低于它算静音。 */
            const val VOICE_RMS = 700.0
            const val SILENCE_BYTES = 640 * 30
            const val MIN_VOICED_BYTES = 640 * 10
            const val MAX_SEGMENT_BYTES = 640 * 200
        }
    }

    /** 裸 PCM 包一层标准 WAV 头，识别端只认 WAV。 */
    private fun pcmToWav(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size)
        fun ascii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
        fun int32(value: Int) {
            out.write(value and 0xff)
            out.write(value shr 8 and 0xff)
            out.write(value shr 16 and 0xff)
            out.write(value shr 24 and 0xff)
        }
        fun int16(value: Int) {
            out.write(value and 0xff)
            out.write(value shr 8 and 0xff)
        }
        ascii("RIFF")
        int32(36 + pcm.size)
        ascii("WAVE")
        ascii("fmt ")
        int32(16)
        int16(1)
        int16(1)
        int32(16_000)
        int32(32_000)
        int16(2)
        int16(16)
        ascii("data")
        int32(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }
}
