package com.yuguang.lumo.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuguang.lumo.data.DailyBar
import com.yuguang.lumo.data.ModelUsage
import com.yuguang.lumo.data.UsageAgg
import com.yuguang.lumo.data.UsageStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 统计页的一次性快照：四个 DAO 流合成一个 StateFlow，界面只读这一份。 */
data class UsageStatsUiState(
    val loading: Boolean = true,
    val totalTokens: Int = 0,
    val totalCost: Double = 0.0,
    val conversations: Int = 0,
    val daily: List<DailyBar> = emptyList(),
    val models: List<ModelUsage> = emptyList(),
) {
    /** 没有任何用量时只显示引导文案，不画空柱状图。 */
    val empty: Boolean get() = totalTokens == 0 && models.isEmpty()
}

/**
 * 用量统计页的状态。
 *
 * 费用在服务层按模型 id 累加，Top 列表仍只取前 8 条：全量模型参与总费用，
 * 避免「排行里没有的模型 = 不计费」的错觉。
 */
@HiltViewModel
class UsageStatsViewModel @Inject constructor(
    usageStats: UsageStats,
) : ViewModel() {
    val state: StateFlow<UsageStatsUiState> = combine(
        usageStats.daily(WINDOW_DAYS),
        usageStats.models(),
        usageStats.totalTokens(),
        usageStats.conversations(),
    ) { daily, models, tokens, conversations ->
        UsageStatsUiState(
            loading = false,
            totalTokens = tokens,
            totalCost = models.sumOf { it.cost },
            conversations = conversations,
            daily = daily,
            models = models.take(UsageAgg.TOP_MODELS),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageStatsUiState())

    companion object {
        /** 柱状图窗口：两周足够看出趋势，又不至于挤成一条线。 */
        const val WINDOW_DAYS = 14
    }
}
