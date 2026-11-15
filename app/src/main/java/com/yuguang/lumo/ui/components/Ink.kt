package com.yuguang.lumo.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LocalMotionEnabled
import com.yuguang.lumo.ui.theme.LumoMotion
import com.yuguang.lumo.ui.theme.LumoType
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 点击反馈：一圈墨在水中扩散（400ms），可包任意内容。
 *
 * 需要内容可点时才用它；已经在 Material 组件里的按钮不必再包（否则两层水波）。
 */
@Composable
fun InkRipple(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Button,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val motion = LocalMotionEnabled.current
    val palette = LocalLumoPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spread = remember { Animatable(0f) }

    LaunchedEffect(pressed, motion) {
        if (!motion) {
            spread.snapTo(0f)
            return@LaunchedEffect
        }
        if (pressed) {
            spread.snapTo(0f)
            spread.animateTo(1f, tween(durationMillis = LumoMotion.inkRippleMillis, easing = LumoMotion.ease))
        } else {
            // 松手即止：墨已经淡到看不见，再补一段收缩动画反而像回弹。
            spread.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = role,
                onClick = onClick,
            )
            .then(
                if (motion) {
                    Modifier.drawBehind {
                        val p = spread.value
                        if (p > 0.001f && p < 0.999f) {
                            val maxRadius = hypot(size.width, size.height) * 0.5f
                            drawCircle(
                                color = palette.cinnabar.copy(alpha = 0.24f * (1f - p)),
                                radius = maxRadius * p,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                    }
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

/**
 * 「正在落笔」：三滴墨上下轻浮、明暗呼吸（1.2s 一轮）。
 * 关动效时是三点静止的淡墨，信息量不变。
 */
@Composable
fun InkDots(
    modifier: Modifier = Modifier,
    color: Color = LocalLumoPalette.current.cinnabar,
    dotSize: Dp = 7.dp,
    gap: Dp = 6.dp,
) {
    val motion = LocalMotionEnabled.current
    val progress = rememberInkDotsProgress(motion)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val phaseOffset = index * 0.27f
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        val p = progress?.value
                        if (p == null) {
                            alpha = 0.55f
                        } else {
                            val wave = sin(((p + phaseOffset) % 1f * PI)).toFloat()
                            translationY = -dotSize.toPx() * 0.55f * wave
                            alpha = 0.32f + 0.68f * wave
                        }
                    }
                    .background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun rememberInkDotsProgress(motion: Boolean): State<Float>? {
    if (!motion) return null
    val transition = rememberInfiniteTransition(label = "inkDots")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LumoMotion.thinkingMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "inkDotsProgress",
    )
}

/**
 * 呼吸的印章（2.4s 一轮）。加载态、品牌位、引导页都用它。
 */
@Composable
fun BreathingSeal(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    text: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val motion = LocalMotionEnabled.current
    val palette = LocalLumoPalette.current
    val breath = rememberBreath(motion)
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val p = breath?.value
                if (p == null) {
                    scaleX = 1f
                    scaleY = 1f
                    alpha = 1f
                } else {
                    val s = 0.97f + 0.05f * p
                    scaleX = s
                    scaleY = s
                    alpha = 0.84f + 0.16f * p
                }
                rotationZ = -4f
            }
            .clip(RoundedCornerShape(12.dp))
            .background(palette.cinnabar)
            .padding(3.dp)
            .border(1.dp, palette.sealInk.copy(alpha = 0.45f), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Text(
                text = text,
                style = LumoType.seal.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                color = palette.sealInk,
            )
        }
        content()
    }
}

@Composable
private fun rememberBreath(motion: Boolean): State<Float>? {
    if (!motion) return null
    val transition = rememberInfiniteTransition(label = "sealBreath")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LumoMotion.sealBreathMillis, easing = LumoMotion.ease),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sealBreathProgress",
    )
}

/**
 * 方印：朱砂底 + 白字衬线，6dp 圆角。用来标记「已选 / 第几 / 一句话结论」。
 */
@Composable
fun SealBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalLumoPalette.current.cinnabar,
    contentColor: Color = LocalLumoPalette.current.sealInk,
    size: Dp = 18.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = LumoType.seal.copy(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.5.sp),
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * 诗句。`vertical = true` 时逐字竖排、按标点分栏、从右往左读（传统题跋的排法）。
 */
@Composable
fun PoemLine(
    @StringRes textRes: Int,
    vertical: Boolean = false,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    val text = stringResource(textRes)
    if (!vertical) {
        Text(
            text = text,
            modifier = modifier,
            style = LumoType.poem,
            color = color,
        )
        return
    }
    val columns = text.split('，', '。', '、', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .reversed()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        columns.forEach { column ->
            Column(horizontalAlignment = horizontalAlignment) {
                column.forEach { ch ->
                    Text(
                        text = ch.toString(),
                        style = LumoType.poem.copy(lineHeight = 22.sp, letterSpacing = 2.sp),
                        color = color,
                    )
                }
            }
        }
    }
}

/** 1dp 渐隐细线：分隔「纸面」而不是切开它。 */
@Composable
fun InkDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline,
    alpha: Float = 0.85f,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        color.copy(alpha = 0f),
                        color.copy(alpha = alpha),
                        color.copy(alpha = 0f),
                    ),
                ),
            ),
    )
}
