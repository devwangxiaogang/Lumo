package com.yuguang.lumo.data

/**
 * 把流式增量整理成可朗读的句子。
 *
 * 规则：
 * - 围栏代码块整段跳过：朗读代码没有意义，也会让 TTS 念出一堆符号；
 * - 在中文标点与换行处断句；英文句点只在后接空白/结尾且前一字不是数字时断（避免 3.14、v1.2）；
 * - 过短的句子先攒着，与后面的句子合并到 [MIN_CHUNK]，减少免费端点的请求次数；
 * - 没有标点的超长句在 [MAX_CHUNK] 附近回退到空格或顿号处硬切。
 */
class SpeechSplitter(
    private val minChunk: Int = MIN_CHUNK,
    private val maxChunk: Int = MAX_CHUNK,
) {
    private val buffer = StringBuilder()
    private var ticks = 0
    private var inFence = false

    /** 追加一段增量，返回已经可以朗读的句子（可能为空）。 */
    fun push(delta: String): List<String> {
        append(delta)
        return drain()
    }

    /** 流结束：把剩下的内容全部交出来。 */
    fun flush(): List<String> {
        val out = drain().toMutableList()
        while (buffer.length > maxChunk) {
            out += clean(buffer.substring(0, maxChunk))
            buffer.delete(0, maxChunk)
        }
        val rest = clean(buffer.toString())
        buffer.setLength(0)
        if (rest.isNotEmpty()) out += rest
        return out
    }

    private fun append(delta: String) {
        var index = 0
        while (index < delta.length) {
            val char = delta[index]
            if (char == '`') {
                var run = 0
                while (index + run < delta.length && delta[index + run] == '`') run++
                ticks += run
                index += run
                if (ticks >= 3) {
                    inFence = !inFence
                    ticks = 0
                }
                continue
            }
            index++
            if (inFence) {
                ticks = 0
                continue
            }
            ticks = 0
            when (char) {
                '*', '#', '>', '_', '~', '\r' -> Unit
                else -> buffer.append(char)
            }
        }
    }

    private fun drain(): List<String> {
        val out = mutableListOf<String>()
        while (true) {
            val cut = findCut() ?: break
            val chunk = clean(buffer.substring(0, cut + 1))
            buffer.delete(0, cut + 1)
            if (chunk.isNotEmpty()) out += chunk
        }
        return out
    }

    /** 返回可以断句的下标；还没有合适的断点时返回 null。 */
    private fun findCut(): Int? {
        var index = 0
        while (index < buffer.length) {
            if (index + 1 >= minChunk && isBoundary(index)) return index
            index++
        }
        if (buffer.length < maxChunk) return null
        for (index in (maxChunk - 1) downTo minChunk) {
            val char = buffer[index]
            if (char == ' ' || char == '，' || char == ',' || char == '、') return index
        }
        return maxChunk - 1
    }

    private fun isBoundary(index: Int): Boolean {
        return when (val char = buffer[index]) {
            // 中文标点没有歧义，直接断句。
            '。', '！', '？', '；', '…', '\n' -> true
            // 半角标点只有在后面紧跟空白、结尾或中文字符时才断：
            // 否则 3.14、v1.2、URL 里的 ?b=1 会被切成两段。
            '.', '!', '?', ';' -> {
                val next = buffer.getOrNull(index + 1)
                val previous = buffer.getOrNull(index - 1)
                val boundaryAfter = next == null || next.isWhitespace() || next.isCjk()
                boundaryAfter && !(char == '.' && previous?.isDigit() == true)
            }
            else -> false
        }
    }

    private fun Char.isCjk(): Boolean = this in '\u4E00'..'\u9FFF' || this in '\u3000'..'\u303F'

    /** 去掉 Markdown 链接、图片与多余空白，避免念出 URL。 */
    private fun clean(text: String): String = text
        .replace(LINK, " ")
        .replace(URL, " ")
        .replace(WHITESPACE, " ")
        .trim()

    companion object {
        const val MIN_CHUNK = 40
        const val MAX_CHUNK = 300
        private val LINK = Regex("\\[[^\\]]*\\]\\([^)]*\\)")
        private val URL = Regex("https?://\\S+")
        private val WHITESPACE = Regex("\\s+")
    }
}
