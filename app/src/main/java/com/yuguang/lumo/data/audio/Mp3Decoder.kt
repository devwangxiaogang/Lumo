package com.yuguang.lumo.data.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * MP3 → 16 bit 单声道 PCM。
 *
 * 免费朗读端点（百度 / 有道）返回的都是 MP3，而播放侧只认裸 PCM，
 * 所以在本地用 MediaCodec 解一次。解码失败（个别 ROM 缺 MP3 解码器）时返回 null，
 * 调用方据此退回本机 TTS，而不是让用户看到无声的一次朗读。
 */
object Mp3Decoder {
    data class Pcm(val bytes: ByteArray, val sampleRate: Int)

    private const val TIMEOUT_US = 10_000L

    fun decode(mp3: ByteArray): Pcm? {
        if (mp3.isEmpty()) return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(TempSource(mp3))
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    .orEmpty()
                    .startsWith("audio/")
            } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val created = MediaCodec.createDecoderByType(mime)
            codec = created
            created.configure(format, null, null, 0)
            created.start()

            val output = ByteArrayOutputStream(mp3.size * 4)
            val info = MediaCodec.BufferInfo()
            var sawInputEnd = false
            var sawOutputEnd = false
            while (!sawOutputEnd) {
                if (!sawInputEnd) {
                    val inputIndex = created.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = created.getInputBuffer(inputIndex) ?: return null
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            created.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEnd = true
                        } else {
                            created.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = created.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outputIndex >= 0 -> {
                        if (info.size > 0) {
                            val buffer = created.getOutputBuffer(outputIndex)
                            if (buffer != null) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val chunk = ByteArray(info.size)
                                buffer.get(chunk)
                                output.write(if (channels > 1) downmix(chunk, channels) else chunk)
                            }
                        }
                        created.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEnd = true
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                }
            }
            val bytes = output.toByteArray()
            if (bytes.isEmpty()) null else Pcm(bytes, sourceRate)
        } catch (error: Exception) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * 免费端点都是单声道；遇到立体声就取左声道平均，
     * 保证输出仍是单声道 16 bit，播放端不必再分支。
     */
    private fun downmix(pcm: ByteArray, channels: Int): ByteArray {
        val frames = pcm.size / (2 * channels)
        val out = ByteArray(frames * 2)
        for (frame in 0 until frames) {
            var sum = 0
            for (channel in 0 until channels) {
                val offset = (frame * channels + channel) * 2
                val sample = ((pcm[offset].toInt() and 0xFF) or (pcm[offset + 1].toInt() shl 8)).toShort().toInt()
                sum += sample
            }
            val mixed = (sum / channels).toShort()
            out[frame * 2] = (mixed.toInt() and 0xFF).toByte()
            out[frame * 2 + 1] = ((mixed.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** MediaExtractor 需要一个可读的「数据源」，内存字节包成最小实现。 */
    private class TempSource(private val data: ByteArray) : android.media.MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(size.toLong(), (data.size - position).toLong()).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, count)
            return count
        }

        override fun getSize(): Long = data.size.toLong()

        override fun close() = Unit

        private fun ByteBuffer.unused() = Unit
    }
}
