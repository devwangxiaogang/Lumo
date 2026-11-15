package com.yuguang.lumo.domain.model

/**
 * 消息树的分支路径计算。
 *
 * 会话用 parentId 表达树、leafMessageId 表达当前激活的叶子。回溯只在内存里做：
 * 会话消息量级很小，而递归 CTE 在老版本 SQLite 上兼容性不稳。
 * 所有入参都按 (createdAt, id) 升序的全量消息。
 */
object BranchPath {
    /** 回溯的最大步数；脏数据成环时保证有界退出。 */
    const val MAX_DEPTH = 10_000

    /** 从根到 [leafId] 的激活路径；leafId 为 0 或找不到时按全量线性兜底（老数据）。 */
    fun resolvePath(messages: List<ChatMessage>, leafId: Long): List<ChatMessage> =
        resolveOrdered(
            items = messages,
            leafId = leafId,
            order = compareBy({ it.createdAt }, { it.id }),
            idOf = { it.id },
            parentOf = { it.parentId },
        )

    /**
     * 通用回溯。[ChatMessage] 之外，导出/重试等场景直接作用于持久层实体，
     * 避免为了选路径先做一次领域模型转换。
     */
    fun <T> resolveOrdered(
        items: List<T>,
        leafId: Long,
        order: Comparator<T>,
        idOf: (T) -> Long,
        parentOf: (T) -> Long,
    ): List<T> {
        if (items.isEmpty()) return emptyList()
        val sorted = items.sortedWith(order)
        // 0 = 未初始化（导入的旧备份 / 异常数据），退回全量线性。
        if (leafId <= 0L) return sorted
        val byId = sorted.associateBy(idOf)
        var current = byId[leafId] ?: return sorted
        val chain = ArrayList<T>()
        val seen = HashSet<Long>()
        // seen 兜住 parentId 成环或指回自己：每跳只走一次，最多 MAX_DEPTH 跳。
        while (seen.add(idOf(current))) {
            chain += current
            if (chain.size >= MAX_DEPTH) break
            val parentId = parentOf(current)
            if (parentId == 0L || parentId == idOf(current)) break
            current = byId[parentId] ?: break
        }
        chain.reverse()
        return chain
    }

    /**
     * 同一父节点下、同角色的兄弟消息，按 (createdAt, id) 升序；目标不存在时返回空列表。
     *
     * 注意 parentId=0 是根节点：迁移前的老数据整段对话都挂在 0 下，
     * 需要「真正的分叉」的调用方应当跳过 parentId==0（见 ChatViewModel.branchEntries）。
     */
    fun siblings(messages: List<ChatMessage>, targetId: Long): List<ChatMessage> {
        val target = messages.firstOrNull { it.id == targetId } ?: return emptyList()
        return messages
            .filter { it.parentId == target.parentId && it.role == target.role }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
    }

    /** [targetId] 在同一组兄弟里的下标；不在其中时为 -1。 */
    fun indexOf(messages: List<ChatMessage>, targetId: Long): Int =
        siblings(messages, targetId).indexOfFirst { it.id == targetId }

    /** [targetId] 子树里最深的那条消息（同深度取最新）；找不到时返回 0。切分支用。 */
    fun deepestLeaf(messages: List<ChatMessage>, targetId: Long): Long =
        deepestLeafOrdered(
            items = messages,
            targetId = targetId,
            order = compareBy({ it.createdAt }, { it.id }),
            idOf = { it.id },
            parentOf = { it.parentId },
        )

    /**
     * [deepestLeaf] 的通用版：持久层实体也用它，免得多做一次领域模型转换。
     *
     * 遍历带 visited 集合，脏数据成环时不会挂死；同深度按 [order] 取最新。
     */
    fun <T> deepestLeafOrdered(
        items: List<T>,
        targetId: Long,
        order: Comparator<T>,
        idOf: (T) -> Long,
        parentOf: (T) -> Long,
    ): Long {
        val target = items.firstOrNull { idOf(it) == targetId } ?: return 0L
        val children = items.sortedWith(order).groupBy(parentOf)
        var best: T = target
        var bestDepth = 0
        val visited = HashSet<Long>()
        val stack = ArrayDeque<Pair<T, Int>>()
        visited += idOf(target)
        stack.addLast(target to 0)
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            if (depth > bestDepth || (depth == bestDepth && order.compare(node, best) > 0)) {
                best = node
                bestDepth = depth
            }
            children[idOf(node)].orEmpty().forEach { child ->
                if (visited.add(idOf(child))) stack.addLast(child to depth + 1)
            }
        }
        return idOf(best)
    }
}
