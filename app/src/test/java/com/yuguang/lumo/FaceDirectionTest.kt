package com.yuguang.lumo

import com.yuguang.lumo.domain.model.FaceDirection
import com.yuguang.lumo.domain.model.FaceTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 面对面模式的方向判定。
 *
 * 规则只有一条：谁是多数文字就判成谁的语言；没有任何文字信号（纯数字/标点/空白）时
 * 返回 null，让调用方保持当前方向——把「123」翻面比不翻更糟。
 */
class FaceDirectionTest {
    @Test
    fun detectsChineseAndEnglish() {
        assertEquals("zh", FaceDirection.detect("今天天气不错"))
        assertEquals("en", FaceDirection.detect("the weather is nice"))
    }

    @Test
    fun noSignalReturnsNull() {
        assertNull(FaceDirection.detect(""))
        assertNull(FaceDirection.detect("123 456"))
        assertNull(FaceDirection.detect("，。！？ —— ..."))
        assertNull(FaceDirection.detect("   "))
    }

    @Test
    fun mixedTextTakesMajority() {
        // 汉字多 → 中文；字母多 → 英文。
        assertEquals("zh", FaceDirection.detect("我去了 Starbucks 买咖啡"))
        assertEquals("en", FaceDirection.detect("I bought coffee at 星巴克 today"))
    }

    @Test
    fun sideOfComparesWithMyLanguage() {
        assertEquals(FaceDirection.ME, FaceDirection.sideOf("你好，请问洗手间在哪", "zh"))
        assertEquals(FaceDirection.OTHER, FaceDirection.sideOf("Where is the restroom", "zh"))
        // 我方是英文时反过来。
        assertEquals(FaceDirection.ME, FaceDirection.sideOf("nice to meet you", "en"))
        assertEquals(FaceDirection.OTHER, FaceDirection.sideOf("很高兴认识你", "en"))
    }

    @Test
    fun sideOfFallsBackToOtherWithoutSignal() {
        // 只有数字时归到「对方」：按住说话的人通常是对方，误判代价更小。
        assertEquals(FaceDirection.OTHER, FaceDirection.sideOf("12345", "zh"))
    }

    @Test
    fun autoTargetPicksOppositeLanguage() {
        assertEquals("en", FaceDirection.autoTarget("zh", locked = false, manualTarget = "ja"))
        assertEquals("zh", FaceDirection.autoTarget("en", locked = false, manualTarget = "ja"))
        // 其它语种无法自动配对，保留手选目标。
        assertEquals("ja", FaceDirection.autoTarget("ja", locked = false, manualTarget = "ja"))
    }

    @Test
    fun autoTargetRespectsLock() {
        assertEquals("ja", FaceDirection.autoTarget("zh", locked = true, manualTarget = "ja"))
    }

    @Test
    fun swapOnlyFlipsChineseAndEnglish() {
        assertEquals("en", FaceDirection.swap("zh"))
        assertEquals("zh", FaceDirection.swap("en"))
        assertEquals("ja", FaceDirection.swap("ja"))
    }

    private fun turn(side: String, source: String, target: String, pending: Boolean = true) = FaceTurn(
        side = side,
        sourceText = source,
        targetText = target,
        from = "zh",
        to = "en",
        createdAt = 0,
        pending = pending,
    )

    @Test
    fun appendKeepsSeparateCardsForAlternatingSides() {
        val turns = FaceDirection.append(
            FaceDirection.append(emptyList(), turn(FaceDirection.ME, "我想买票", "I want a ticket")),
            turn(FaceDirection.OTHER, "How many", "几张"),
        )
        assertEquals(2, turns.size)
        assertEquals(FaceDirection.ME, turns[0].side)
        assertEquals(FaceDirection.OTHER, turns[1].side)
    }

    @Test
    fun appendMergesConsecutivePendingSameSide() {
        val turns = FaceDirection.append(
            FaceDirection.append(emptyList(), turn(FaceDirection.ME, "第一句", "first")),
            turn(FaceDirection.ME, "第二句", "second"),
        )
        assertEquals(1, turns.size)
        assertEquals("第一句\n第二句", turns[0].sourceText)
        assertEquals("first\nsecond", turns[0].targetText)
    }

    @Test
    fun appendDoesNotMergeFinalTurns() {
        // 定稿之后的同一侧发言是新的一句，不能被合并掉（否则记录里少句子）。
        val turns = FaceDirection.append(
            FaceDirection.append(emptyList(), turn(FaceDirection.ME, "第一句", "first", pending = false)),
            turn(FaceDirection.ME, "第二句", "second"),
        )
        assertEquals(2, turns.size)
    }

    @Test
    fun appendTrimsToMax() {
        var turns = emptyList<FaceTurn>()
        repeat(70) { index ->
            turns = FaceDirection.append(
                turns,
                turn(if (index % 2 == 0) FaceDirection.ME else FaceDirection.OTHER, "s$index", "t$index", pending = false),
                max = 60,
            )
        }
        assertEquals(60, turns.size)
        assertEquals("s69", turns.last().sourceText)
    }
}
