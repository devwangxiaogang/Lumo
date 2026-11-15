package com.yuguang.lumo

import com.yuguang.lumo.domain.ArenaRun
import com.yuguang.lumo.domain.ArenaSide
import com.yuguang.lumo.domain.ArenaSlot
import com.yuguang.lumo.domain.ArenaTally
import com.yuguang.lumo.domain.model.MessageStatus
import com.yuguang.lumo.domain.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 擂台纯逻辑：投票归一化、计时格式、token 合计、超限淘汰、状态机。 */
class ArenaTallyTest {
    private fun slot(
        provider: ProviderId = ProviderId.DEEPSEEK,
        model: String = "deepseek-flash",
        status: MessageStatus = MessageStatus.DONE,
        ttftMs: Int = 0,
        promptTokens: Int = 0,
        completionTokens: Int = 0,
    ) = ArenaSlot(
        provider = provider,
        model = model,
        status = status,
        ttftMs = ttftMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
    )

    private fun run(
        id: Long,
        createdAt: Long,
        slotA: ArenaSlot = slot(status = MessageStatus.DONE),
        slotB: ArenaSlot = slot(status = MessageStatus.DONE),
        winner: String = "",
    ) = ArenaRun(
        id = id,
        prompt = "问题",
        slotA = slotA,
        slotB = slotB,
        winner = winner,
        createdAt = createdAt,
    )

    @Test
    fun winnerNormalizedFromUiLabels() {
        assertEquals(ArenaTally.WINNER_A, ArenaTally.normalizeWinner("A"))
        assertEquals(ArenaTally.WINNER_A, ArenaTally.normalizeWinner(" a "))
        assertEquals(ArenaTally.WINNER_B, ArenaTally.normalizeWinner("B"))
        assertEquals(ArenaTally.WINNER_A, ArenaTally.normalizeWinner("A 更好"))
        assertEquals(ArenaTally.WINNER_B, ArenaTally.normalizeWinner("b更好"))
        assertEquals(ArenaTally.WINNER_TIE, ArenaTally.normalizeWinner("TIE"))
        assertEquals(ArenaTally.WINNER_TIE, ArenaTally.normalizeWinner("平局"))
        assertEquals(ArenaTally.WINNER_TIE, ArenaTally.normalizeWinner("draw"))
    }

    @Test
    fun unknownWinnerFallsBackToNone() {
        assertEquals(ArenaTally.WINNER_NONE, ArenaTally.normalizeWinner(null))
        assertEquals(ArenaTally.WINNER_NONE, ArenaTally.normalizeWinner(""))
        assertEquals(ArenaTally.WINNER_NONE, ArenaTally.normalizeWinner("   "))
        assertEquals(ArenaTally.WINNER_NONE, ArenaTally.normalizeWinner("C"))
    }

    @Test
    fun winnerSideMapsOnlyAAndB() {
        assertEquals(ArenaSide.A, ArenaTally.winnerSide("A"))
        assertEquals(ArenaSide.B, ArenaTally.winnerSide("B"))
        assertNull(ArenaTally.winnerSide("TIE"))
        assertNull(ArenaTally.winnerSide(""))
    }

    @Test
    fun ttftFormattedForBothScales() {
        assertEquals("—", ArenaTally.formatTtft(0))
        assertEquals("—", ArenaTally.formatTtft(-5))
        assertEquals("320ms", ArenaTally.formatTtft(320))
        assertEquals("999ms", ArenaTally.formatTtft(999))
        assertEquals("1.0s", ArenaTally.formatTtft(1_000))
        assertEquals("1.2s", ArenaTally.formatTtft(1_234))
        assertEquals("12.5s", ArenaTally.formatTtft(12_543))
    }

    @Test
    fun tokensSumBothSides() {
        val a = slot(promptTokens = 10, completionTokens = 20)
        val b = slot(promptTokens = 5, completionTokens = 7)
        assertEquals(30, ArenaTally.slotTokens(a))
        assertEquals(12, ArenaTally.slotTokens(b))
        assertEquals(42, ArenaTally.runTokens(run(1, 1, slotA = a, slotB = b)))
    }

    @Test
    fun negativeTokenCountsClampedToZero() {
        assertEquals(0, ArenaTally.totalTokens(-3, -9))
        assertEquals(4, ArenaTally.totalTokens(-3, 4))
    }

    @Test
    fun trimKeepsNewestRuns() {
        val runs = (1L..60L).map { id -> run(id, createdAt = id) }
        val kept = ArenaTally.trim(runs)
        assertEquals(ArenaTally.MAX_RUNS, kept.size)
        assertEquals(60L, kept.first().id)
        assertEquals(11L, kept.last().id)
    }

    @Test
    fun trimSortsBeforeCutting() {
        val runs = listOf(run(1, createdAt = 5), run(2, createdAt = 99), run(3, createdAt = 42))
        val kept = ArenaTally.trim(runs, keep = 2)
        assertEquals(listOf(2L, 3L), kept.map { it.id })
    }

    @Test
    fun trimWithNonPositiveKeepIsEmpty() {
        assertTrue(ArenaTally.trim(listOf(run(1, 1)), keep = 0).isEmpty())
        assertTrue(ArenaTally.trim(listOf(run(1, 1)), keep = -1).isEmpty())
        assertTrue(ArenaTally.trim(emptyList()).isEmpty())
    }

    @Test
    fun onlyStreamingCanReachTerminalState() {
        assertTrue(ArenaTally.canTransition(MessageStatus.STREAMING, MessageStatus.DONE))
        assertTrue(ArenaTally.canTransition(MessageStatus.STREAMING, MessageStatus.FAILED))
        assertFalse(ArenaTally.canTransition(MessageStatus.DONE, MessageStatus.FAILED))
        assertFalse(ArenaTally.canTransition(MessageStatus.FAILED, MessageStatus.DONE))
        assertFalse(ArenaTally.canTransition(MessageStatus.DONE, MessageStatus.DONE))
        assertFalse(ArenaTally.canTransition(MessageStatus.STREAMING, MessageStatus.STREAMING))
    }

    @Test
    fun terminalStatusNotOverwritten() {
        assertEquals(MessageStatus.DONE, ArenaTally.settleStatus(MessageStatus.STREAMING, MessageStatus.DONE))
        assertEquals(MessageStatus.FAILED, ArenaTally.settleStatus(MessageStatus.STREAMING, MessageStatus.FAILED))
        assertEquals(MessageStatus.DONE, ArenaTally.settleStatus(MessageStatus.DONE, MessageStatus.FAILED))
        assertEquals(MessageStatus.FAILED, ArenaTally.settleStatus(MessageStatus.FAILED, MessageStatus.DONE))
    }

    @Test
    fun ttftWrittenOnlyOnce() {
        assertEquals(320, ArenaTally.ttftAfter(0, 320))
        assertEquals(320, ArenaTally.ttftAfter(320, 4_000))
        assertEquals(0, ArenaTally.ttftAfter(0, -1))
        // 第二次测量为 0 也不能把已有值抹掉：流式结束时的总耗时不参与首字。
        assertEquals(320, ArenaTally.ttftAfter(320, 0))
    }
}
