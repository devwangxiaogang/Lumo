package com.yuguang.lumo

import com.yuguang.lumo.ui.chat.MarkdownBlock
import com.yuguang.lumo.ui.chat.MarkdownCache
import com.yuguang.lumo.ui.chat.splitMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 块级缓存的正确性契约：
 * 每次增量结果拼起来必须与「一次性全量解析」完全相同，
 * 且已闭合的块要被复用（引用相等），而不是每帧重建。
 */
class MarkdownCacheTest {

    private fun full(text: String) = splitMarkdownBlocks(text)

    /** 渐进式喂入：每加一行调用一次 advance，最终结果必须与全量解析一致。 */
    private fun assertProgressiveMatchesFull(text: String) {
        val lines = text.lines()
        var snapshot: MarkdownCache.Snapshot? = null
        var buffer = StringBuilder()
        lines.forEachIndexed { index, line ->
            if (index > 0) buffer.append('\n')
            buffer.append(line)
            snapshot = MarkdownCache.advance(snapshot, buffer.toString())
        }
        assertEquals(full(text), snapshot?.blocks().orEmpty())
    }

    @Test
    fun plainParagraphMatchesFullParse() {
        assertProgressiveMatchesFull("第一行\n第二行\n第三行")
    }

    @Test
    fun fencedCodeBlockMatchesFullParse() {
        assertProgressiveMatchesFull(
            """
            说明如下：

            ```kotlin
            fun main() {
                println("hi")
            }
            ```

            结束。
            """.trimIndent(),
        )
    }

    @Test
    fun multipleCodeBlocksMatchFullParse() {
        assertProgressiveMatchesFull(
            """
            先看 a：

            ```
            a
            ```

            再看 b：

            ```
            b
            ```
            """.trimIndent(),
        )
    }

    @Test
    fun unclosedFenceStaysOpenUntilClosed() {
        var snapshot = MarkdownCache.advance(null, "开头\n```\ncode")
        assertEquals(1, snapshot.open?.let { 1 } ?: 0)
        assertTrue(snapshot.open is MarkdownBlock.Code)

        snapshot = MarkdownCache.advance(snapshot, "开头\n```\ncode\n```\n后面的话")
        assertEquals(full("开头\n```\ncode\n```\n后面的话"), snapshot.blocks())
    }

    @Test
    fun closedBlocksAreReusedNotRebuilt() {
        val first = MarkdownCache.advance(null, "第一段\n```\na\n```")
        // 第二段开始增长时，上一轮已闭合的块必须原样复用（同一个实例），
        // 而不是每帧重新构造。
        val second = MarkdownCache.advance(first, "第一段\n```\na\n```\n第二段")
        assertTrue(second.closed.size >= first.closed.size)
        first.closed.forEachIndexed { index, block ->
            assertSame(block, second.closed[index])
        }
    }

    @Test
    fun emptyInputIsSafe() {
        val snapshot = MarkdownCache.advance(null, "")
        assertTrue(snapshot.closed.isEmpty())
        assertEquals(null, snapshot.open)
        assertTrue(snapshot.blocks().isEmpty())
    }

    @Test
    fun whitespaceOnlyInputProducesNoBlocks() {
        val snapshot = MarkdownCache.advance(null, "   \n\n  ")
        assertTrue(snapshot.blocks().isEmpty())
    }

    @Test
    fun rewritingEarlierContentIsHandled() {
        // 兜底文案替换整段正文时，缓存必须跟着重建，不能残留旧块。
        val first = MarkdownCache.advance(null, "旧内容\n```\nx\n```")
        val replaced = MarkdownCache.advance(first, "完全不同的新内容")
        assertEquals(full("完全不同的新内容"), replaced.blocks())
    }
}
