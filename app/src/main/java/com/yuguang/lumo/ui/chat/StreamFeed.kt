package com.yuguang.lumo.ui.chat

/**
 * 边生成边朗读的增量判定。
 *
 * 抽成纯函数是为了能测：流式期间界面读的是内存覆盖副本，落库后会换成数据库版本，
 * 正文可能被替换（只剩思考时的兜底文案）、被截断（取消/失败）或变短（flush 节流）。
 * 只比长度会在这些时刻把字符串从中间切开。
 */
object StreamFeed {
    /**
     * @return 本次该朗读的新增文本；空串表示没有新增。
     *         返回 null 表示内容整体被换掉了，调用方应当收尾并停止朗读。
     */
    fun delta(fed: String, current: String): String? = when {
        current.startsWith(fed) -> current.substring(fed.length)
        // 内容变短但仍是「已喂部分的截断」：这是回滚，不是新内容。
        fed.startsWith(current) -> ""
        else -> null
    }
}
