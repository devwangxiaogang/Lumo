package com.yuguang.lumo.data

/**
 * 自动标题的纯逻辑：把模型返回的原始文本洗成一条可上列表的短标题。
 *
 * 独立成无 Android 依赖的函数，便于单元测试；失败的输入统一返回 null，
 * 调用方保留「首条消息截断」的旧标题，不写库。
 */
object AutoTitle {
    /** 标题上限：中文 24 字以内足够扫读，再长会被列表省略号吃掉。 */
    const val MAX_LENGTH = 24

    private val prefixes = listOf("标题：", "标题:", "题目：", "题目:", "Title:", "title:")

    /** 成对包裹符号：只有整串被同一对包住才剥掉，保留《三体》这类书名号在内文里的写法。 */
    private val wrappers = listOf(
        '"' to '"',
        '\'' to '\'',
        '「' to '」',
        '『' to '』',
        '《' to '》',
        '“' to '”',
        '‘' to '’',
    )

    /**
     * 清洗模型输出。
     *
     * 容忍：\`\`\` 围栏、「标题：」前缀、整串引号/书名号、换行、
     * 结尾句号；不做语义改写，只去掉明显的外壳。
     *
     * @return 可用标题；空白、纯符号或只有壳的输入返回 null。
     */
    fun clean(raw: String): String? {
        val lines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("```") }
            .toList()
        if (lines.isEmpty()) return null
        // 模型有时先来一句「好的，标题如下：」，真正标题在下一行。
        var text = lines.first()
        if (lines.size > 1 && (text.endsWith("：") || text.endsWith(":"))) {
            text = lines[1]
        }
        prefixes.firstOrNull { text.startsWith(it) }?.let { text = text.removePrefix(it).trim() }
        wrappers.forEach { (open, close) ->
            if (text.length > 1 && text.first() == open && text.last() == close) {
                text = text.substring(1, text.length - 1).trim()
            }
        }
        text = text.removeSuffix("。").removeSuffix(".").trim()
        if (text.isEmpty()) return null
        // 纯符号（如 "——"、"..."）不当作标题。
        if (text.none { it.isLetterOrDigit() }) return null
        return if (text.length > MAX_LENGTH) text.take(MAX_LENGTH).trim() else text
    }

    /** 会话标题是否还需要自动命名：空串或旧版本写死的「新对话」。 */
    fun needsTitle(current: String): Boolean {
        val clean = current.trim()
        return clean.isEmpty() || clean == "新对话" || clean == "New chat"
    }

    /**
     * 结合首条用户消息判断是否还需要自动命名。
     *
     * 生成流程在第一条消息落库时就会把标题写成「首条消息前 24 字」，
     * 只比对空串/「新对话」会让自动标题永远不触发；这里把那类自动派生标题
     * 也视为「还没有真正的标题」。用户手动改成与之相同的文本仍不会被覆盖——
     * 因为 AI 标题随后会写入并二次校验。
     */
    fun needsTitle(current: String, firstUserText: String): Boolean {
        if (needsTitle(current)) return true
        val derived = fallback(firstUserText)
        return derived.isNotEmpty() && current.trim() == derived
    }

    /** 发给模型的提示词；对话内容作为第二条 user 消息。 */
    fun prompt(titleLanguage: String): String =
        "用 $titleLanguage 给下面这段对话起一个标题。" +
            "第一行就是标题本身：6-12 个字，不要引号、不要句号、不要解释、不要 Markdown。" +
            "如需先思考，思考后请把标题单独写在最后一行。"

    /** 会话没有标题时，用首条用户消息截断兜底。 */
    fun fallback(firstUserText: String): String =
        firstUserText.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(MAX_LENGTH).orEmpty()
}
