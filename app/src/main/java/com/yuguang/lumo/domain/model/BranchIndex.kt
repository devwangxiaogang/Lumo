package com.yuguang.lumo.domain.model

/**
 * 分支切换器的批量计算。
 *
 * [BranchPath.siblings] 每次都要扫一遍全量消息，而 [ChatViewModel] 是对激活路径上的
 * 每条消息各调一次——长会话下就是平方级扫描，而且这段还挂在流式更新的 collect 里，
 * 生成回复时反复执行。
 *
 * 这里改成一次分组：按 (parentId, role) 建索引，再对激活路径上的每条消息查表。
 * 结果与逐个调用 [BranchPath.siblings] 完全一致（有 golden 测试守着）。
 */
object BranchIndex {

    /** 一组兄弟：升序 id 列表与目标在其中的下标。 */
    data class Entry(val ids: List<Long>, val index: Int) {
        val count: Int get() = ids.size

        fun previous(): Long? = ids.getOrNull(index - 1)

        fun next(): Long? = ids.getOrNull(index + 1)
    }

    /**
     * @param active 激活路径上的消息（界面实际显示的那一段）。
     * @param all 该会话的全量消息，用来找兄弟。
     * @param trustRootSiblings 只有库里真的存在父子关系时才承认根节点上的兄弟；
     *   1.2 及更早的备份导入后整段 parentId 都是 0，那时它们是一条链而不是分支。
     */
    fun entries(
        active: List<ChatMessage>,
        all: List<ChatMessage>,
        trustRootSiblings: Boolean = all.any { it.parentId != 0L },
    ): Map<Long, Entry> {
        if (active.isEmpty()) return emptyMap()
        val groups = HashMap<GroupKey, MutableList<ChatMessage>>()
        all.forEach { message ->
            groups.getOrPut(GroupKey(message.parentId, message.role)) { ArrayList() } += message
        }
        val result = HashMap<Long, Entry>(active.size)
        active.forEach { message ->
            if (message.parentId == 0L && !trustRootSiblings) return@forEach
            val group = groups[GroupKey(message.parentId, message.role)] ?: return@forEach
            if (group.size < 2) return@forEach
            val ordered = group.sortedWith(compareBy({ it.createdAt }, { it.id }))
            val index = ordered.indexOfFirst { it.id == message.id }
            if (index >= 0) {
                result[message.id] = Entry(ordered.map { it.id }, index)
            }
        }
        return result
    }

    private data class GroupKey(val parentId: Long, val role: Role)
}
