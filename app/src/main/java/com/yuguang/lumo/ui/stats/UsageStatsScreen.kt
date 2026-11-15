package com.yuguang.lumo.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuguang.lumo.R
import com.yuguang.lumo.data.DailyBar
import com.yuguang.lumo.data.ModelUsage
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.knownModelLabel
import com.yuguang.lumo.ui.chat.formatCost
import com.yuguang.lumo.ui.components.InkDivider
import com.yuguang.lumo.ui.components.glass
import com.yuguang.lumo.ui.settings.SettingPageScaffold
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LocalMotionEnabled
import com.yuguang.lumo.ui.theme.LumoMotion
import com.yuguang.lumo.ui.settings.SettingsGroup
import kotlin.math.min

/**
 * 用量统计：总览卡 + 近 14 天柱状图 + 按模型排行。
 *
 * 只读页面：所有数字来自 StatsDao 的聚合流，任何异常数据都在 [com.yuguang.lumo.data.UsageAgg]
 * 里归零，界面不做二次兜底，也不需要 loading 之外的错误态。
 */
@Composable
fun UsageStatsScreen(
    onBack: () -> Unit,
    viewModel: UsageStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingPageScaffold(
        title = stringResource(R.string.stats_title),
        onBack = onBack,
    ) { padding ->
        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp)
                    .size(24.dp),
                strokeWidth = 2.dp,
            )
            return@SettingPageScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SummaryCards(
                tokens = state.totalTokens,
                cost = state.totalCost,
                conversations = state.conversations,
            )
            if (state.empty) {
                Text(
                    stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else {
                SettingsGroup(stringResource(R.string.stats_chart_title), mark = "数") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DailyChart(state.daily)
                    }
                }
                SettingsGroup(stringResource(R.string.stats_models_title), mark = "数") {
                    ModelRanking(state.models)
                }
            }
            // 说明文字压在山水上会掉到 3:1 以下，垫一层 level 1 的纸才守得住 12sp 小字。
            Text(
                stringResource(R.string.stats_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(shape = RoundedCornerShape(16.dp), level = 1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

/** 三张总览卡：等宽排列，数字用同一套缩写，避免长数字把卡片撑破。 */
@Composable
private fun SummaryCards(tokens: Int, cost: Double, conversations: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        SummaryCard(
            label = stringResource(R.string.stats_total_tokens),
            value = formatTokens(tokens),
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            label = stringResource(R.string.stats_total_cost),
            value = formatCost(cost),
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            label = stringResource(R.string.stats_conversations),
            value = conversations.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.glass(shape = RoundedCornerShape(18.dp), level = 0.9f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 手绘柱状图：输入在下、输出在上堆叠。
 *
 * 颜色取主题色（primary / tertiary），深色模式由主题自动换色；
 * Y 轴最大值随当天最高柱自适应，没有数据时整块不画（由调用方隐藏）。
 */
@Composable
private fun DailyChart(daily: List<DailyBar>) {
    val palette = LocalLumoPalette.current
    val motion = LocalMotionEnabled.current
    // 石青=输入、朱砂=输出、鎏金=刻度：矿物颜料三色，夜里也不靠纯色相区分。
    val inputColor = MaterialTheme.colorScheme.primary
    val outputColor = palette.cinnabar
    val maxTotal = daily.maxOfOrNull { it.prompt.coerceAtLeast(0) + it.completion.coerceAtLeast(0) } ?: 0
    // 逐柱自下而上生长：一条进度动画 + 每柱 60ms 错峰；关闭动效时直接到终态。
    val growMillis = LumoMotion.chartBarMillis
    val staggerMillis = LumoMotion.chartBarStagger
    val totalMillis = growMillis + staggerMillis * (daily.size - 1).coerceAtLeast(0)
    val progress = remember(daily.size) { Animatable(0f) }
    LaunchedEffect(daily, motion, totalMillis) {
        if (!motion || totalMillis <= 0) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = totalMillis, easing = LumoMotion.ease),
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegendDot(inputColor, stringResource(R.string.stats_chart_input))
            LegendDot(outputColor, stringResource(R.string.stats_chart_output))
            Spacer(Modifier.weight(1f))
            Text(
                formatTokens(maxTotal),
                style = MaterialTheme.typography.labelSmall,
                color = palette.gold,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            if (daily.isEmpty()) return@Canvas
            val slot = size.width / daily.size
            val gap = slot * 0.34f
            val barWidth = (slot - gap).coerceAtLeast(1f)
            val radius = min(3.dp.toPx(), barWidth / 2)
            val elapsed = progress.value * totalMillis
            daily.forEachIndexed { index, bar ->
                val prompt = bar.prompt.coerceAtLeast(0).toFloat()
                val completion = bar.completion.coerceAtLeast(0).toFloat()
                val total = prompt + completion
                if (total <= 0f || maxTotal <= 0) return@forEachIndexed
                // 第 index 根柱子从 index*60ms 开始长，500ms 长满；底边贴基线，所以只缩高度。
                val grow = if (!motion || growMillis <= 0) {
                    1f
                } else {
                    ((elapsed - index * staggerMillis) / growMillis).coerceIn(0f, 1f)
                }
                if (grow <= 0f) return@forEachIndexed
                val fullHeight = size.height * (total / maxTotal) * grow
                val promptHeight = fullHeight * (prompt / total)
                val completionHeight = fullHeight - promptHeight
                val left = index * slot + gap / 2
                // 输出段先画（下缘多画一个圆角高度），再由输入段盖住接缝。
                if (completionHeight > 0f) {
                    drawTopRoundedBar(
                        color = outputColor,
                        left = left,
                        top = size.height - fullHeight,
                        width = barWidth,
                        height = completionHeight,
                        radius = radius,
                    )
                }
                if (promptHeight > 0f) {
                    drawRect(
                        color = inputColor,
                        topLeft = Offset(left, size.height - promptHeight),
                        size = Size(barWidth, promptHeight),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DateLabel(daily.firstOrNull()?.day, TextAlign.Start)
            DateLabel(daily.getOrNull(daily.size / 2)?.day, TextAlign.Center)
            DateLabel(daily.lastOrNull()?.day, TextAlign.End)
        }
    }
}

@Composable
private fun DateLabel(day: String?, align: TextAlign) {
    Text(
        text = day?.takeLast(5).orEmpty(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 只圆上沿：柱子底边贴基线，圆角在底部会显得浮空。 */
private fun DrawScope.drawTopRoundedBar(
    color: Color,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float,
) {
    val r = min(radius, min(height, width / 2))
    if (r <= 0f) {
        drawRect(color, Offset(left, top), Size(width, height))
        return
    }
    val path = Path().apply {
        moveTo(left, top + height)
        lineTo(left, top + r)
        quadraticTo(left, top, left + r, top)
        lineTo(left + width - r, top)
        quadraticTo(left + width, top, left + width, top + r)
        lineTo(left + width, top + height)
        close()
    }
    drawPath(path, color)
}

/** 按模型排行：只显示 token 与费用，点击无动作（纯只读页）。 */
@Composable
private fun ModelRanking(models: List<ModelUsage>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        models.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(item.provider.nameRes) + " · " +
                            formatTokens(item.prompt.coerceAtLeast(0) + item.completion.coerceAtLeast(0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    costLabel(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (index < models.lastIndex) {
                InkDivider(alpha = 0.45f)
            }
        }
    }
}

/**
 * Token 缩写：列表和卡片共用一套规则，避免「12,345」和「1.2 万」混在一起。
 * 只做展示，不参与计算。
 */
private fun formatTokens(tokens: Int): String {
    val safe = tokens.coerceAtLeast(0)
    return when {
        safe < 1_000 -> safe.toString()
        safe < 1_000_000 -> String.format(java.util.Locale.US, "%.1fK", safe / 1_000.0)
        else -> String.format(java.util.Locale.US, "%.2fM", safe / 1_000_000.0)
    }
}

/**
 * 费用文案：免费档恒 ¥0.00；目录里没有的模型价格未知，显示「—」而不是 ¥0.00。
 */
private fun costLabel(item: ModelUsage): String = when {
    item.provider == ProviderId.FREE -> "¥0.00"
    // 目录与价格表都没有的模型：没有可信价格，用「—」而不是 ¥0.00。
    item.provider.knownModelLabel(item.model) == null -> "—"
    else -> formatCost(item.cost)
}
