package com.yuguang.lumo.ui.chat

/**
 * 流式 Markdown 的块级缓存。
 *
 * 流式期间正文大约每 60 ms 变一次（见 ChatRepositoryImpl.STREAM_INTERVAL_MS），
 * 而 `remember(text)` 会让 [splitMarkdownBlocks] 与行内解析每次都从头跑一遍，
 * 于是长回复的解析成本随生成长度线性叠加，生成本身反而成了次要开销。
 *
 * 这里的办法很朴素：只在**最后一个块**上做增量。已经闭合的块（后面已经出现了
 * 新的 ``` 围栏或完整段落）内容不会再变，直接复用上一次的解析结果。
 * 纯函数，便于单测；不引入 memoization 框架。
 */
internal object MarkdownCache {

    /** 一次增量解析的结果。 */
    data class Snapshot(
        /** 已闭合、可以复用的块。 */
        val closed: List<MarkdownBlock>,
        /** 正在增长的最后一块（可能为空）。 */
        val open: MarkdownBlock?,
    ) {
        /** 拼成完整块列表，供渲染使用。 */
        fun blocks(): List<MarkdownBlock> = if (open == null) closed else closed + open
    }

    /**
     * 计算 [next] 相对于 [previous] 的增量。
     *
     * @param previous 上一次的解析结果；null 表示第一次。
     * @param next 当前完整正文。
     */
    fun advance(previous: Snapshot?, next: String): Snapshot {
        val full = splitMarkdownBlocks(next)
        if (previous == null) {
            return splitClosed(full)
        }
        // 前缀块没变就复用：逐个比对，遇到第一个不同的位置停下。
        val reuse = ArrayList<MarkdownBlock>(previous.closed.size)
        var shared = 0
        while (shared < previous.closed.size && shared < full.size) {
            if (previous.closed[shared] != full[shared]) break
            reuse += previous.closed[shared]
            shared += 1
        }
        val tail = full.drop(shared)
        // 尾部里除最后一块外都已闭合。
        val closed = ArrayList<MarkdownBlock>(reuse.size + tail.size)
        closed += reuse
        if (tail.size > 1) closed += tail.dropLast(1)
        return Snapshot(closed = closed, open = tail.lastOrNull())
    }

    private fun splitClosed(blocks: List<MarkdownBlock>): Snapshot {
        if (blocks.isEmpty()) return Snapshot(emptyList(), null)
        return Snapshot(closed = blocks.dropLast(1), open = blocks.last())
    }
}
