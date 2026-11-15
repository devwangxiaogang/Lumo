package com.yuguang.lumo

import com.yuguang.lumo.domain.model.BranchPath
import com.yuguang.lumo.domain.model.ChatMessage
import com.yuguang.lumo.domain.model.MessageStatus
import com.yuguang.lumo.domain.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 消息树的分支语义：路径回溯、兄弟切换、最深叶子。
 *
 * 每个用例都从「已经按 (createdAt, id) 升序」的消息列表出发——仓储层保证这个顺序，
 * 纯函数不再重复排序，测试也按这个契约构造数据。
 */
class BranchPathTest {
    private fun message(
        id: Long,
        parentId: Long,
        role: Role = Role.USER,
        text: String = "m$id",
        status: MessageStatus = MessageStatus.DONE,
    ) = ChatMessage(
        id = id,
        conversationId = 1,
        role = role,
        content = text,
        reasoning = "",
        status = status,
        error = "",
        createdAt = id,
        parentId = parentId,
    )

    private fun ids(list: List<ChatMessage>) = list.map { it.id }

    @Test
    fun linearChainResolvesWholePath() {
        val chain = listOf(message(1, 0), message(2, 1, Role.ASSISTANT), message(3, 2), message(4, 3, Role.ASSISTANT))
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(BranchPath.resolvePath(chain, 4)))
    }

    @Test
    fun legacyLeafZeroFallsBackToAllMessages() {
        // 迁移前的老数据：parentId 全 0、leaf 未初始化。必须原样返回，历史窗口才不会变。
        val flat = listOf(message(1, 0), message(2, 0, Role.ASSISTANT), message(3, 0))
        assertEquals(listOf(1L, 2L, 3L), ids(BranchPath.resolvePath(flat, 0)))
    }

    @Test
    fun threeSiblingsOnlyActivePathReturned() {
        val messages = listOf(
            message(1, 0),
            message(2, 1, Role.ASSISTANT),
            message(3, 1, Role.ASSISTANT),
            message(4, 1, Role.ASSISTANT),
            message(5, 3),
            message(6, 5, Role.ASSISTANT),
        )
        assertEquals(listOf(1L, 3L, 5L, 6L), ids(BranchPath.resolvePath(messages, 6)))
        // 切到另一条兄弟：只回溯到它自己。
        assertEquals(listOf(1L, 2L), ids(BranchPath.resolvePath(messages, 2)))
    }

    @Test
    fun failedAssistantStillKeepsAncestorChain() {
        val messages = listOf(
            message(1, 0),
            message(2, 1, Role.ASSISTANT, status = MessageStatus.FAILED),
        )
        assertEquals(listOf(1L, 2L), ids(BranchPath.resolvePath(messages, 2)))
    }

    @Test
    fun unknownLeafFallsBackToAllMessages() {
        val messages = listOf(message(1, 0), message(2, 1, Role.ASSISTANT))
        assertEquals(listOf(1L, 2L), ids(BranchPath.resolvePath(messages, 99)))
    }

    @Test
    fun cycleDoesNotHang() {
        // 脏数据：2 的父节点指回 1，1 的父节点又指回 2。
        val messages = listOf(message(1, 2), message(2, 1, Role.ASSISTANT))
        val path = BranchPath.resolvePath(messages, 2)
        // 结果始终按从根到叶排列：环被 seen 截断后剩下 [1, 2]。
        assertEquals(listOf(1L, 2L), ids(path))
        assertEquals(2, path.size)
    }

    @Test
    fun siblingsAreOrderedAndIndexed() {
        val messages = listOf(
            message(1, 0),
            message(2, 1, Role.ASSISTANT),
            message(3, 1, Role.ASSISTANT),
            message(9, 5),
        )
        assertEquals(listOf(2L, 3L), ids(BranchPath.siblings(messages, 3)))
        assertEquals(1, BranchPath.indexOf(messages, 3))
        assertEquals(0, BranchPath.indexOf(messages, 2))
        // 不同父节点不算兄弟（9 挂在 5 下面）。
        assertEquals(listOf(9L), ids(BranchPath.siblings(messages, 9)))
        // 根节点语义：老数据里所有消息 parentId=0，兄弟判定依然按 (parentId, role) 生效。
        assertEquals(listOf(1L), ids(BranchPath.siblings(listOf(message(1, 0)), 1)))
    }

    @Test
    fun deepestLeafFollowsNewestChildChain() {
        val messages = listOf(
            message(1, 0),
            message(2, 1, Role.ASSISTANT),
            message(3, 1, Role.ASSISTANT),
            message(4, 3),
            message(5, 4, Role.ASSISTANT),
        )
        assertEquals(5L, BranchPath.deepestLeaf(messages, 3))
        assertEquals(2L, BranchPath.deepestLeaf(messages, 2))
    }

    @Test
    fun deepestLeafMissingTargetReturnsZero() {
        val messages = listOf(message(1, 0))
        assertEquals(0L, BranchPath.deepestLeaf(messages, 42))
        assertEquals(1L, BranchPath.deepestLeaf(messages, 1))
    }

    @Test
    fun emptyInputIsSafe() {
        assertTrue(BranchPath.resolvePath(emptyList(), 1).isEmpty())
        assertTrue(BranchPath.siblings(emptyList(), 1).isEmpty())
        assertEquals(0L, BranchPath.deepestLeaf(emptyList(), 1))
    }
}
