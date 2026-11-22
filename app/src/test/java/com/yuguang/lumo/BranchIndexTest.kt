package com.yuguang.lumo

import com.yuguang.lumo.domain.model.BranchIndex
import com.yuguang.lumo.domain.model.BranchPath
import com.yuguang.lumo.domain.model.ChatMessage
import com.yuguang.lumo.domain.model.MessageStatus
import com.yuguang.lumo.domain.model.Role
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [BranchIndex] 必须与旧的逐条 [BranchPath.siblings] 实现完全等价。
 *
 * 旧实现简单但平方级；新实现一次分组。等价的唯一可靠证据是「同一份输入，两边结果相同」，
 * 所以这里把旧逻辑照抄成 golden。
 */
class BranchIndexTest {

    private fun message(
        id: Long,
        parentId: Long,
        role: Role,
        createdAt: Long,
    ) = ChatMessage(
        id = id,
        conversationId = 1,
        role = role,
        content = "m$id",
        reasoning = "",
        status = MessageStatus.DONE,
        error = "",
        createdAt = createdAt,
        parentId = parentId,
    )

    /** 旧实现（逐条 siblings），作为对照基准。 */
    private fun legacyEntries(
        active: List<ChatMessage>,
        all: List<ChatMessage>,
    ): Map<Long, Pair<List<Long>, Int>> = buildMap {
        val hasTree = all.any { it.parentId != 0L }
        active.forEach { message ->
            if (message.parentId == 0L && !hasTree) return@forEach
            val group = BranchPath.siblings(all, message.id)
            val index = group.indexOfFirst { it.id == message.id }
            if (group.size > 1 && index >= 0) {
                put(message.id, group.map { it.id } to index)
            }
        }
    }

    private fun assertSame(active: List<ChatMessage>, all: List<ChatMessage>) {
        val expected = legacyEntries(active, all)
        val actual = BranchIndex.entries(active, all).mapValues { it.value.ids to it.value.index }
        assertEquals(expected, actual)
    }

    @Test
    fun singleChainHasNoBranches() {
        val all = listOf(
            message(1, 0, Role.USER, 1),
            message(2, 1, Role.ASSISTANT, 2),
            message(3, 2, Role.USER, 3),
        )
        assertSame(all, all)
    }

    @Test
    fun retriedAssistantCreatesBranch() {
        // 同一用户消息下两条助手回复：切到第二条时，切换器应显示 1/2。
        val all = listOf(
            message(1, 0, Role.USER, 1),
            message(2, 1, Role.ASSISTANT, 2),
            message(3, 1, Role.ASSISTANT, 3),
        )
        val active = listOf(all[0], all[2])
        assertSame(active, all)
        val entry = BranchIndex.entries(active, all).getValue(3)
        assertEquals(listOf(2L, 3L), entry.ids)
        assertEquals(1, entry.index)
    }

    @Test
    fun editedUserMessageCreatesRootBranchOnlyWhenTreeExists() {
        val rooted = listOf(
            message(1, 0, Role.USER, 1),
            message(2, 1, Role.ASSISTANT, 2),
            message(3, 0, Role.USER, 3),
        )
        assertSame(listOf(rooted[2]), rooted)

        // 老备份：整片 parentId=0，此时根节点上多条用户消息是「一条链」，不是分支。
        val flat = listOf(
            message(1, 0, Role.USER, 1),
            message(2, 0, Role.ASSISTANT, 2),
            message(3, 0, Role.USER, 3),
        )
        assertSame(flat, flat)
    }

    @Test
    fun differentRolesUnderSameParentAreNotSiblings() {
        val all = listOf(
            message(1, 0, Role.USER, 1),
            message(2, 1, Role.ASSISTANT, 2),
            message(3, 1, Role.USER, 3),
        )
        assertSame(all, all)
        // 角色不同 → 各自只有 1 条，不生成切换器。
        assertEquals(emptyMap<Long, BranchIndex.Entry>(), BranchIndex.entries(all, all))
    }

    @Test
    fun emptyInputsAreSafe() {
        assertEquals(emptyMap<Long, BranchIndex.Entry>(), BranchIndex.entries(emptyList(), emptyList()))
        val all = listOf(message(1, 0, Role.USER, 1))
        assertEquals(emptyMap<Long, BranchIndex.Entry>(), BranchIndex.entries(emptyList(), all))
    }

    @Test
    fun previousAndNextStayInsideBounds() {
        val entry = BranchIndex.Entry(listOf(2L, 3L, 4L), 0)
        assertEquals(null, entry.previous())
        assertEquals(3L, entry.next())
        val last = BranchIndex.Entry(listOf(2L, 3L, 4L), 2)
        assertEquals(3L, last.previous())
        assertEquals(null, last.next())
    }

    @Test
    fun largeConversationMatchesLegacy() {
        // 300 条消息、每 10 条出现一次重试分支：两边结果必须逐字段相同。
        val all = ArrayList<ChatMessage>()
        var id = 1L
        var parent = 0L
        while (id <= 300) {
            all += message(id, parent, Role.USER, id)
            val user = id
            id++
            all += message(id, user, Role.ASSISTANT, id)
            id++
            if (id % 20 == 1L) {
                all += message(id, user, Role.ASSISTANT, id)
                id++
            }
            parent = id - 1
        }
        assertSame(all, all)
    }
}
