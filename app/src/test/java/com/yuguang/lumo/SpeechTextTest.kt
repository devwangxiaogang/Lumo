package com.yuguang.lumo

import com.yuguang.lumo.data.SpeechSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    @Test
    fun `splits on chinese sentence marks`() {
        val splitter = SpeechSplitter(minChunk = 4, maxChunk = 100)
        assertEquals(
            listOf("第一句话在这里。", "第二句话也在这里！", "第三句呢？"),
            splitter.push("第一句话在这里。第二句话也在这里！第三句呢？"),
        )
        assertEquals(emptyList<String>(), splitter.flush())
    }

    @Test
    fun `keeps short sentence until it grows`() {
        val splitter = SpeechSplitter(minChunk = 6, maxChunk = 100)
        assertTrue(splitter.push("短句。").isEmpty())
        assertEquals(listOf("短句。再补一句。"), splitter.push("再补一句。"))
    }

    @Test
    fun `does not split decimal numbers`() {
        val splitter = SpeechSplitter(minChunk = 4, maxChunk = 100)
        assertEquals(listOf("圆周率是 3.14159 这个数。"), splitter.push("圆周率是 3.14159 这个数。"))
    }

    @Test
    fun `does not split inside urls`() {
        val splitter = SpeechSplitter(minChunk = 4, maxChunk = 100)
        // URL 本身会被剥掉，朗读链接没有意义；关键是 ? 与 / 不能把句子切成两段。
        val out = splitter.push("请看 https://example.com/a?b=1 这里。")
        assertEquals(1, out.size)
        assertTrue(out.single().endsWith("这里。"))
    }

    @Test
    fun `skips fenced code blocks`() {
        val splitter = SpeechSplitter(minChunk = 4, maxChunk = 100)
        assertEquals(
            listOf("看下面这段代码。"),
            splitter.push("看下面这段代码。```kotlin\nval x = 1.\n```"),
        )
        assertEquals(listOf("这里解释一下。"), splitter.push("这里解释一下。"))
        assertEquals(emptyList<String>(), splitter.flush())
    }

    @Test
    fun `strips markdown noise and urls`() {
        val splitter = SpeechSplitter(minChunk = 4, maxChunk = 100)
        assertEquals(listOf("重点见 这里。"), splitter.push("**重点**见 https://example.com/a?b=1 这里。"))
    }

    @Test
    fun `hard wraps very long runs without punctuation`() {
        val splitter = SpeechSplitter(minChunk = 10, maxChunk = 20)
        val out = splitter.push("a".repeat(50))
        assertTrue(out.isNotEmpty())
        assertTrue(out.all { it.length <= 20 })
        assertTrue(splitter.flush().all { it.length <= 20 })
    }

    @Test
    fun `flush returns remaining text once`() {
        val splitter = SpeechSplitter(minChunk = 40, maxChunk = 300)
        assertTrue(splitter.push("没有结束标点的一句话").isEmpty())
        assertEquals(listOf("没有结束标点的一句话"), splitter.flush())
        assertEquals(emptyList<String>(), splitter.flush())
    }

    @Test
    fun `splits english sentences on ASCII marks`() {
        val splitter = SpeechSplitter(minChunk = 4, maxChunk = 100)
        assertEquals(
            listOf("Hello there.", "Next sentence!", "Done?"),
            splitter.push("Hello there. Next sentence! Done?"),
        )
    }
}
