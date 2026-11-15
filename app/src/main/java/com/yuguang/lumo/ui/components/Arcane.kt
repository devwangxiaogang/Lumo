package com.yuguang.lumo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuguang.lumo.ui.theme.LocalMotionEnabled
import com.yuguang.lumo.ui.theme.LumoMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 弧光学院皮肤专用的装饰件。
 *
 * 与「山水」那套的关系不是「另一套组件」，而是**同一个组件的另一种材质**：
 * 墨点变星火、方印变菱徽、墨线变金线、纸纹变星图。所以这里的函数都只做「画」，
 * 由 `Ink.kt` 里的公开组件按皮肤分叉调用，页面看到的签名一个字都没变。
 */

/** 把整数散列到 0..1：确定、无分配、跨设备一致（星图与星尘共用）。 */
internal fun hashedUnit(seed: Int): Float {
    var x = seed * 374761393 + 668265263
    x = (x xor (x shr 13)) * 1274126177
    x = x xor (x shr 16)
    return ((x and 0x7FFFFFFF) % 100000) / 100000f
}

/** 一次按压的星火：一圈金环 + 8–12 颗向外飞的火星。 */
fun DrawScope.drawSparkBurst(
    progress: Float,
    spark: Color,
    ring: Color,
    sparks: Int = 10,
) {
    if (progress <= 0.001f || progress >= 0.999f) return
    val fade = 1f - progress
    val maxRadius = hypot(size.width, size.height) * 0.5f * 0.92f
    val center = Offset(size.width / 2f, size.height / 2f)

    drawCircle(
        color = ring.copy(alpha = 0.34f * fade),
        radius = maxRadius * progress,
        style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
    )

    repeat(sparks) { index ->
        // 用下标推角度，不用随机数：同一控件每次按下的形状要一致，否则像在抖。
        val angle = (index * (360f / sparks) + 12f) * (PI / 180f).toFloat()
        val Travel = 0.62f + 0.16f * ((index * 7) % 3) / 2f
        val distance = maxRadius * progress * Travel
        val dotRadius = 1.7.dp.toPx() * (1f - progress * 0.7f)
        val tailRadius = dotRadius * 2.6f
        val dotCenter = center + Offset(cos(angle) * distance, sin(angle) * distance)
        drawCircle(
            color = spark.copy(alpha = 0.22f * fade),
            radius = tailRadius,
            center = dotCenter,
        )
        drawCircle(
            color = spark.copy(alpha = 0.95f * fade),
            radius = dotRadius,
            center = dotCenter,
        )
    }
}

/** 菱徽：旋转 45° 的方，用金色渐变填充；[inner] 用来放四芒星。 */
fun DrawScope.drawRuneDiamond(
    brush: Brush,
    glowBrush: Brush,
    inset: Float = 0f,
) {
    val half = (minOf(size.width, size.height) / 2f) - inset
    val center = Offset(size.width / 2f, size.height / 2f)
    val path = Path().apply {
        moveTo(center.x, center.y - half)
        lineTo(center.x + half, center.y)
        lineTo(center.x, center.y + half)
        lineTo(center.x - half, center.y)
        close()
    }
    // 先铺一层弱光晕，让菱徽「发光」而不是「贴纸」。
    drawPath(path, brush = glowBrush)
    drawPath(path, brush = brush)
}

/** 四芒星（菱形星）：长短两对尖，尖端收在圆角里。 */
fun DrawScope.drawFourPointStar(
    center: Offset,
    outerRadius: Float,
    color: Color,
    waistRatio: Float = 0.26f,
    rotateDeg: Float = 0f,
) {
    val waist = outerRadius * waistRatio
    val path = Path().apply {
        moveTo(center.x, center.y - outerRadius)
        lineTo(center.x + waist, center.y - waist)
        lineTo(center.x + outerRadius, center.y)
        lineTo(center.x + waist, center.y + waist)
        lineTo(center.x, center.y + outerRadius)
        lineTo(center.x - waist, center.y + waist)
        lineTo(center.x - outerRadius, center.y)
        lineTo(center.x - waist, center.y - waist)
        close()
    }
    rotate(rotateDeg, pivot = center) {
        drawPath(path, color)
    }
}

/** 符文环：一圈断续弧，整体缓慢旋转。 */
fun DrawScope.drawRuneRing(
    rotationDeg: Float,
    color: Color,
    radius: Float,
    segments: Int = 12,
    alpha: Float = 0.55f,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val sweep = 360f / segments
    rotate(rotationDeg, pivot = center) {
        repeat(segments) { index ->
            drawArc(
                color = color.copy(alpha = alpha),
                startAngle = index * sweep,
                sweepAngle = sweep * 0.46f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/** 三点星火：不位移，只呼吸亮度（山水那边是上下浮动）。 */
@Composable
internal fun TwinkleDots(
    modifier: Modifier = Modifier,
    color: Color,
    dotSize: Dp,
    gap: Dp,
) {
    val motion = LocalMotionEnabled.current
    val progress = rememberTwinkleProgress(motion)
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val phase = index * 0.29f
            Canvas(modifier = Modifier.size(dotSize)) {
                val p = progress?.value
                val wave = if (p == null) 0.7f else abs(sin(((p + phase) % 1f) * PI)).toFloat()
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(color.copy(alpha = 0.16f + 0.26f * wave), r * 1.5f, center)
                drawCircle(color.copy(alpha = 0.42f + 0.58f * wave), r * 0.62f, center)
            }
        }
    }
}

@Composable
private fun rememberTwinkleProgress(motion: Boolean): State<Float>? {
    if (!motion) return null
    val transition = rememberInfiniteTransition(label = "arcTwinkle")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = LumoMotion.starTwinkleMaxMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "arcTwinkleProgress",
    )
}

/** 符文环的旋转进度（0..1 一圈）。 */
@Composable
internal fun rememberRuneRotation(motion: Boolean): State<Float>? {
    if (!motion) return null
    val transition = rememberInfiniteTransition(label = "arcRune")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = LumoMotion.runeRotateMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "arcRuneProgress",
    )
}

/** 菱徽用的金色渐变（左上亮、右下暗，像被一支蜡烛从左上照到）。 */
internal fun goldBrush(light: Color, deep: Color): Brush = Brush.linearGradient(
    colors = listOf(light, deep),
)

/** 菱徽外那层弱光。 */
internal fun glowBrush(color: Color): Brush = Brush.radialGradient(
    colors = listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0f)),
)

/** 给菱形徽记留一个中心盒：徽记本身用 Canvas 画，内容（字/图标）叠在上面。 */
@Composable
internal fun DiamondBadge(
    size: Dp,
    brush: Brush,
    glow: Brush,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            drawRuneDiamond(brush = brush, glowBrush = glow)
        }
        content()
    }
}

/**
 * 换皮肤的过场：一片星尘从左下扫到右上（600ms）。
 *
 * 为什么要有：皮肤一换，色板、八幅景、图标同时变，如果只做背景交叉淡入，
 * 前景的文字颜色是「瞬间跳变」的，看起来像闪了一下。盖一层短暂的星尘能把这次跳变
 * 变成一次「施法」，观感上从「故障」变成「仪式」。动效关闭时它整个不出现。
 */
@Composable
fun SkinSwitchVeil(modifier: Modifier = Modifier) {
    val skin = com.yuguang.lumo.ui.theme.LocalSkin.current
    val motion = LocalMotionEnabled.current
    if (!motion) return
    val palette = com.yuguang.lumo.ui.theme.LocalLumoPalette.current
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    // 首次组合不播：启动时皮肤从默认值恢复也会触发一次变化，
    // 那一层星尘看起来像闪了一下而不是「刚换皮肤」。
    val primed = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(skin) {
        if (!primed.value) {
            primed.value = true
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = LumoMotion.veilSweepMillis,
                easing = LinearEasing,
            ),
        )
    }
    val dp1 = with(LocalDensity.current) { 1.dp.toPx() }
    Canvas(modifier = modifier.fillMaxSize()) {
        val p = progress.value
        if (p >= 0.999f) return@Canvas
        val fade = sin(p * PI).toFloat().coerceIn(0f, 1f)
        // 40 颗尘埃沿对角线推进：位置 = 起点 + p * 位移，超过就绕回起点。
        for (index in 0 until 40) {
            val seedX = hashedUnit(index * 3 + 11)
            val seedY = hashedUnit(index * 3 + 12)
            val travel = 0.55f + 0.45f * hashedUnit(index * 3 + 13)
            val fx = ((seedX + p * travel) % 1f + 1f) % 1f
            val fy = ((seedY - p * travel * 0.6f) % 1f + 1f) % 1f
            val radius = (0.8f + 1.8f * hashedUnit(index * 7 + 5)) * dp1
            drawCircle(
                color = palette.spark.copy(alpha = 0.55f * fade),
                radius = radius,
                center = Offset(size.width * fx, size.height * fy),
            )
        }
    }
}
