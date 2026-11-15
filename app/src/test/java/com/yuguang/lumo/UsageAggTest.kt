package com.yuguang.lumo

import com.yuguang.lumo.data.DailyBar
import com.yuguang.lumo.data.UsageAgg
import com.yuguang.lumo.data.local.UsageDayRow
import com.yuguang.lumo.data.local.UsageModelRow
import com.yuguang.lumo.domain.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 统计聚合：补零、时区、排序与脏数据防御。 */
class UsageAggTest {
    /** 与价格表无关的假价格：输入 1 美元/百万，输出 2 美元/百万。 */
    private val fakeCost: (ProviderId, String, Int, Int) -> Double =
        { _, _, prompt, completion -> prompt / 1_000_000.0 + completion * 2 / 1_000_000.0 }

    private val zeroCost: (ProviderId, String, Int, Int) -> Double = { _, _, _, _ -> 0.0 }

    @Test
    fun fourteenDaysAreZeroFilledAndAscending() {
        val bars = UsageAgg.fillDays(emptyList(), days = 14, zoneOffsetMinutes = 0, now = 0L, cost = zeroCost)
        assertEquals(14, bars.size)
        assertEquals("1969-12-19", bars.first().day)
        assertEquals("1970-01-01", bars.last().day)
        assertTrue(bars.all { it.prompt == 0 && it.completion == 0 && it.cost == 0.0 })
        val days = bars.map { it.day }
        assertEquals(days.sorted(), days)
    }

    @Test
    fun sameDayRowsMergeAndCostSums() {
        val rows = listOf(
            UsageDayRow("1970-01-01", ProviderId.DEEPSEEK.name, "deepseek-flash", 10, 3),
            UsageDayRow("1970-01-01", ProviderId.DEEPSEEK.name, "deepseek-v4-pro", 5, 1),
            UsageDayRow("1969-12-31", ProviderId.DEEPSEEK.name, "deepseek-flash", 7, 0),
        )
        val bars = UsageAgg.fillDays(rows, days = 3, zoneOffsetMinutes = 0, now = 0L, cost = fakeCost)
        assertEquals("1969-12-30", bars[0].day)
        assertEquals("1969-12-31", bars[1].day)
        assertEquals(7, bars[1].prompt)
        assertEquals(15, bars[2].prompt)
        assertEquals(4, bars[2].completion)
        assertEquals(15 / 1_000_000.0 + 4 * 2 / 1_000_000.0, bars[2].cost, 1e-12)
    }

    @Test
    fun localDayFollowsZoneOffset() {
        val eveningUtc = 20 * 3_600_000L
        val utc = UsageAgg.fillDays(emptyList(), days = 1, zoneOffsetMinutes = 0, now = eveningUtc, cost = zeroCost)
        val shanghai = UsageAgg.fillDays(emptyList(), days = 1, zoneOffsetMinutes = 480, now = eveningUtc, cost = zeroCost)
        assertEquals("1970-01-01", utc.single().day)
        assertEquals("1970-01-02", shanghai.single().day)
    }

    @Test
    fun rowsOutsideWindowAreDropped() {
        val rows = listOf(
            UsageDayRow("1969-12-01", ProviderId.DEEPSEEK.name, "deepseek-flash", 99, 99),
            UsageDayRow("1970-02-01", ProviderId.DEEPSEEK.name, "deepseek-flash", 99, 99),
        )
        val bars = UsageAgg.fillDays(rows, days = 2, zoneOffsetMinutes = 0, now = 0L, cost = fakeCost)
        assertTrue(bars.all { it.prompt == 0 && it.completion == 0 })
    }

    @Test
    fun topModelsSortsByTokensAndKeepsTiesStable() {
        val rows = listOf(
            UsageModelRow(ProviderId.DEEPSEEK.name, "deepseek-flash", 100, 50, 2),
            UsageModelRow(ProviderId.DOUBAO.name, "doubao-seed-2-1-lite-260915", 200, 0, 1),
            UsageModelRow(ProviderId.FREE.name, "nvidia/nemotron-3.5-lightning:free", 50, 50, 1),
            UsageModelRow(ProviderId.DEEPSEEK.name, "deepseek-v4-pro", 100, 0, 1),
            UsageModelRow(ProviderId.DOUBAO.name, "glm-5-2-260617", 100, 0, 1),
        )
        val top = UsageAgg.topModels(rows, cost = fakeCost)
        assertEquals(5, top.size)
        assertEquals("doubao-seed-2-1-lite-260915", top[0].model)
        assertEquals(200, top[0].prompt)
        // 150 与 100 两组；100 组内按服务商顺序（DEEPSEEK 在 DOUBAO 前）。
        assertEquals("deepseek-flash", top[1].model)
        assertEquals(150, top[1].prompt + top[1].completion)
        assertEquals(listOf("deepseek-v4-pro", "glm-5-2-260617", "nvidia/nemotron-3.5-lightning:free"), top.drop(2).map { it.model })
    }

    @Test
    fun topModelsMergesDuplicateRowsAndRespectsLimit() {
        val rows = listOf(
            UsageModelRow(ProviderId.DEEPSEEK.name, "deepseek-flash", 10, 5, 1),
            UsageModelRow(ProviderId.DEEPSEEK.name, "deepseek-flash", 20, 5, 1),
        )
        val top = UsageAgg.topModels(rows, cost = fakeCost, limit = 1)
        assertEquals(1, top.size)
        assertEquals(30, top.single().prompt)
        assertEquals(10, top.single().completion)
        assertEquals("Flash", top.single().label)
    }

    @Test
    fun unknownProviderAndModelFallBackToId() {
        val rows = listOf(
            UsageModelRow("SOMETHING_NEW", "mystery-model", 10, 10, 1),
        )
        val top = UsageAgg.topModels(rows, cost = fakeCost)
        assertEquals(ProviderId.CUSTOM, top.single().provider)
        assertEquals("mystery-model", top.single().label)
    }

    @Test
    fun totalsIgnoreNegativeValues() {
        val bars = listOf(
            DailyBar("2026-01-01", 10, 5, 0.5),
            DailyBar("2026-01-02", -5, 2, -1.0),
            DailyBar("2026-01-03", 0, 0, 0.25),
        )
        val (tokens, cost) = UsageAgg.totals(bars)
        assertEquals(17, tokens)
        assertEquals(0.75, cost, 1e-12)
    }

    @Test
    fun emptyInputIsSafe() {
        assertEquals(emptyList<DailyBar>(), UsageAgg.fillDays(emptyList(), 0, 480, 0L, fakeCost))
        assertEquals(emptyList<Any>(), UsageAgg.topModels(emptyList(), fakeCost))
        assertEquals(0 to 0.0, UsageAgg.totals(emptyList()))
    }

    @Test
    fun negativeTokensAreClampedToZero() {
        val day = UsageAgg.fillDays(
            listOf(UsageDayRow("1970-01-01", ProviderId.FREE.name, "free-model", -120, -80)),
            days = 1,
            zoneOffsetMinutes = 0,
            now = 0L,
            cost = fakeCost,
        ).single()
        assertEquals(0, day.prompt)
        assertEquals(0, day.completion)
        assertEquals(0.0, day.cost, 0.0)

        val model = UsageAgg.topModels(
            listOf(UsageModelRow(ProviderId.FREE.name, "free-model", -120, -80, 3)),
            cost = fakeCost,
        ).single()
        assertEquals(0, model.prompt)
        assertEquals(0, model.completion)
        assertEquals(0.0, model.cost, 0.0)
    }

    @Test
    fun zeroTokenRowsStillGrouped() {
        val top = UsageAgg.topModels(
            listOf(UsageModelRow(ProviderId.DEEPSEEK.name, "deepseek-flash", 0, 0, 4)),
            cost = fakeCost,
        )
        assertEquals(1, top.size)
        assertEquals(0, top.single().prompt)
        assertEquals(0, top.single().completion)
    }
}
