package com.yuguang.lumo.data

import com.yuguang.lumo.data.local.StatsDao
import com.yuguang.lumo.data.local.UsageDayRow
import com.yuguang.lumo.data.local.UsageModelRow
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.knownModelLabel
import com.yuguang.lumo.ui.chat.estimateCost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** 柱状图上的一天：输入与输出分开堆叠，费用是当天所有模型的合计。 */
data class DailyBar(
    val day: String,
    val prompt: Int,
    val completion: Int,
    val cost: Double,
)

/** 按模型聚合的一行；label 只给界面看，未知模型直接用 id。 */
data class ModelUsage(
    val provider: ProviderId,
    val model: String,
    val label: String,
    val prompt: Int,
    val completion: Int,
    val cost: Double,
)

/**
 * 统计聚合的纯逻辑：不碰 Android、不读价格表，费用由调用方以 lambda 注入。
 *
 * 所有输入都按「脏数据也要能显示」处理：负 token 归零、无法解析的日期桶丢弃，
 * 空输入返回补零的骨架，界面因此不需要再兜底。
 */
object UsageAgg {
    /** Top 列表默认条数：再多一屏放不下，也失去扫读意义。 */
    const val TOP_MODELS = 8

    private const val DAY_MS = 86_400_000L

    /**
     * 把 DAO 的「UTC 日期桶」补齐成连续 [days] 天，按日期升序。
     *
     * SQLite 只能按 UTC 日期分组（见 StatsDao），这里按桶中点（12:00 UTC）折算到
     * 本地日：总额永远准确，跨零点的少量消息可能把某天的柱子挪到相邻一天。
     *
     * @param cost 价格计算，服务层传 estimateCost，单测可传假价格。
     */
    fun fillDays(
        rows: List<UsageDayRow>,
        days: Int,
        zoneOffsetMinutes: Int,
        now: Long,
        cost: (ProviderId, String, Int, Int) -> Double,
    ): List<DailyBar> {
        if (days <= 0) return emptyList()
        val offsetMs = zoneOffsetMinutes * 60_000L
        val today = Math.floorDiv(now + offsetMs, DAY_MS)
        val start = today - (days - 1)
        val prompts = HashMap<Long, Long>()
        val completions = HashMap<Long, Long>()
        val costs = HashMap<Long, Double>()
        rows.forEach { row ->
            val day = localDayOf(row.day, offsetMs) ?: return@forEach
            if (day < start || day > today) return@forEach
            val prompt = row.promptTokens.coerceAtLeast(0)
            val completion = row.completionTokens.coerceAtLeast(0)
            prompts[day] = (prompts[day] ?: 0L) + prompt
            completions[day] = (completions[day] ?: 0L) + completion
            val add = cost(providerOf(row.provider), row.model, prompt, completion)
            if (add.isFinite()) costs[day] = (costs[day] ?: 0.0) + add
        }
        return (0 until days).map { index ->
            val day = start + index
            DailyBar(
                day = formatDay(day),
                prompt = (prompts[day] ?: 0L).clampTokens(),
                completion = (completions[day] ?: 0L).clampTokens(),
                cost = costs[day] ?: 0.0,
            )
        }
    }

    /**
     * 按模型汇总并按 token 降序排行。
     *
     * 同 token 时先按服务商顺序、再按模型 id 排，保证界面顺序稳定；
     * 同一 (provider, model) 出现多行时合并，不丢数据。
     */
    fun topModels(
        rows: List<UsageModelRow>,
        cost: (ProviderId, String, Int, Int) -> Double,
        limit: Int = TOP_MODELS,
    ): List<ModelUsage> {
        if (limit <= 0) return emptyList()
        val merged = LinkedHashMap<Pair<ProviderId, String>, ModelUsage>()
        rows.forEach { row ->
            val provider = providerOf(row.provider)
            val prompt = row.promptTokens.coerceAtLeast(0)
            val completion = row.completionTokens.coerceAtLeast(0)
            val add = cost(provider, row.model, prompt, completion).takeIf { it.isFinite() } ?: 0.0
            val key = provider to row.model
            val current = merged[key]
            merged[key] = if (current == null) {
                ModelUsage(
                    provider = provider,
                    model = row.model,
                    label = provider.knownModelLabel(row.model) ?: row.model,
                    prompt = prompt,
                    completion = completion,
                    cost = add,
                )
            } else {
                current.copy(
                    prompt = (current.prompt.toLong() + prompt).clampTokens(),
                    completion = (current.completion.toLong() + completion).clampTokens(),
                    cost = current.cost + add,
                )
            }
        }
        return merged.values
            .sortedWith(
                compareByDescending<ModelUsage> { it.prompt.toLong() + it.completion }
                    .thenBy { it.provider.ordinal }
                    .thenBy { it.model },
            )
            .take(limit)
    }

    /** 顶部卡片的总量：token 与费用；负费用（脏数据）不计入。 */
    fun totals(daily: List<DailyBar>): Pair<Int, Double> {
        var tokens = 0L
        var cost = 0.0
        daily.forEach { bar ->
            tokens += bar.prompt.coerceAtLeast(0)
            tokens += bar.completion.coerceAtLeast(0)
            if (bar.cost.isFinite() && bar.cost > 0) cost += bar.cost
        }
        return tokens.clampTokens() to cost
    }

    /** DAO 只给 UTC 日期串，按桶中点折算成本地日序号。 */
    private fun localDayOf(day: String, offsetMs: Long): Long? {
        val parts = day.split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val date = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || date !in 1..31) return null
        return Math.floorDiv(
            epochDay(year, month, date) * DAY_MS + DAY_MS / 2 + offsetMs,
            DAY_MS,
        )
    }

    /** Howard Hinnant 的 days_from_civil：纯整数算法，绕开 minSdk 24 上不可用的 java.time。 */
    private fun epochDay(year: Int, month: Int, day: Int): Long {
        val y = year - if (month <= 2) 1 else 0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146_097L + doe - 719_468L
    }

    /** days_from_civil 的逆运算：把本地日序号还原成 yyyy-MM-dd。 */
    private fun formatDay(epochDay: Long): String {
        val z = epochDay + 719_468L
        val era = (if (z >= 0) z else z - 146_096L) / 146_097L
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
        val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
        val mp = (5L * doy + 2L) / 153L
        val d = doy - (153L * mp + 2L) / 5L + 1L
        val m = if (mp < 10L) mp + 3L else mp - 9L
        var y = yoe + era * 400L
        if (m <= 2L) y += 1L
        return y.toString().padStart(4, '0') + "-" +
            m.toString().padStart(2, '0') + "-" +
            d.toString().padStart(2, '0')
    }

    private fun providerOf(value: String): ProviderId =
        ProviderId.entries.firstOrNull { it.name == value } ?: ProviderId.CUSTOM

    private fun Long.clampTokens(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * 用量统计的只读服务。
 *
 * 价格表只有一份（ui/chat/UsageCost.kt），这里只负责调用，避免两处价格漂移；
 * DAO 的 UTC 日分组由 [UsageAgg] 折算成本地日。
 */
@Singleton
class UsageStats @Inject constructor(
    private val statsDao: StatsDao,
) {
    fun daily(days: Int): Flow<List<DailyBar>> = statsDao.observeDaily(since(days)).map { rows ->
        UsageAgg.fillDays(
            rows = rows,
            days = days,
            zoneOffsetMinutes = zoneOffsetMinutes(),
            now = System.currentTimeMillis(),
            cost = ::estimateCostFor,
        )
    }

    fun models(): Flow<List<ModelUsage>> = statsDao.observeModels(MODEL_SCAN_LIMIT).map { rows ->
        UsageAgg.topModels(rows = rows, cost = ::estimateCostFor, limit = MODEL_SCAN_LIMIT)
    }

    fun totalTokens(): Flow<Int> = statsDao.observeTotalTokens().map { it.coerceAtLeast(0) }

    fun conversations(): Flow<Int> = statsDao.observeConversationCount().map { it.coerceAtLeast(0) }

    /** 查询窗口：本地最近 [days] 天，再向过去多留一天给 UTC 桶的边界。 */
    private fun since(days: Int): Long {
        val span = days.coerceAtLeast(1).toLong()
        val offsetMs = zoneOffsetMinutes() * 60_000L
        val today = Math.floorDiv(System.currentTimeMillis() + offsetMs, DAY_MS)
        return (today - (span - 1)) * DAY_MS - offsetMs - DAY_MS
    }

    private companion object {
        const val DAY_MS = 86_400_000L

        /** 排行只展示 8 条，但费用合计要覆盖更多模型，所以多扫一些。 */
        const val MODEL_SCAN_LIMIT = 64

        fun zoneOffsetMinutes(): Int =
            TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000

        fun estimateCostFor(provider: ProviderId, model: String, prompt: Int, completion: Int): Double =
            estimateCost(provider, model, prompt, completion)
    }
}
