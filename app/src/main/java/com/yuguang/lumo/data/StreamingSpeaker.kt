package com.yuguang.lumo.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 边生成边朗读的句柄。
 *
 * 调用方只要把流式增量交给 [push]，流结束时调用 [finish]（或提前 [cancel]）。
 * 句子由 [SpeechSplitter] 切分，播放串行进行——同一时刻只有一句话在合成，
 * 这样停顿不会叠加，也不会和手动朗读抢播放器。
 */
class StreamingSpeaker(
    val key: String,
    val lang: String,
    scope: CoroutineScope,
    private val onFailure: (SpeakFailure) -> Unit,
    private val speak: suspend (String, String) -> Unit,
    private val onIdle: () -> Unit,
    /** 单句下限。免费端点按句计费式地受限于请求数，调用方会传更大的值。 */
    minChunk: Int = SpeechSplitter.MIN_CHUNK,
) {
    private val splitter = SpeechSplitter(minChunk = minChunk)
    private val queue = Channel<String>(capacity = Channel.UNLIMITED)

    private val job: Job = scope.launch {
        try {
            for (sentence in queue) {
                if (sentence.isBlank()) continue
                speak(sentence, lang)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // 朗读失败不该打断对话：交给调用方提示一次，然后安静结束。
            onFailure(
                SpeakFailure(
                    error.message?.takeIf { it.isNotBlank() } ?: "TTS failed",
                    canUseLocal = true,
                ),
            )
        } finally {
            onIdle()
        }
    }

    fun push(delta: String) {
        if (delta.isEmpty()) return
        splitter.push(delta).forEach { queue.trySend(it) }
    }

    fun finish() {
        splitter.flush().forEach { queue.trySend(it) }
        queue.close()
    }

    fun cancel() {
        queue.close()
        job.cancel()
    }
}
