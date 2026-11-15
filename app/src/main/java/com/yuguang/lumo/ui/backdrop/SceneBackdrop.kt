package com.yuguang.lumo.ui.backdrop

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LumoMotion
import com.yuguang.lumo.ui.theme.LumoPalette
import com.yuguang.lumo.ui.theme.Scene

/*
 * 山水背景的层序（自下而上，括号内是 SceneBackdrop 里的实现）：
 *
 *  z0  兜底纸色      Box.background(palette.paper)                       不透明度 100%
 *  z1  位图层        AsyncImage(day/night, ContentScale.Crop)           100%，Ken Burns 1.00→1.09
 *  z2  云雾层        MistLayer: 3 条水平线性渐变带                      0.07 / 0.04 / 0.05（规格 0.05–0.12）
 *  z3  光晕层        HaloLayer: 1 个左上径向渐变                        peak 0.16（规格 0.10–0.18）
 *  z4  蒙版层        ScrimLayer: 竖直 6 停靠点渐变，轻全局 + 上下加重      浅色 40%/8%/50% 宣纸
 *                                                                       深色 40%/10%/52% 夜色
 *                                                                       （顶 0–18%、中部 38–62% 最轻、底部 80–100%）
 *  z5  内容          BackdropHost 的 content
 *
 * 微调位置：只改 this 文件里的 MIST_* / HALO_* 常量；蒙版色在 AiTheme.kt 的 LumoPalette.scrim*。
 * 位图用 Coil 加载（downsample 到 1080×2376 + 硬件位图 + 有界缓存）：8 场景 × 日夜 16 张图，
 * 若按原尺寸常驻会吃掉几十 MB，切页频繁时会 OOM。
 */

/** 云雾带的固定参数：y 位置、高度、浓度、单程时长。三条错开才不会看出循环。 */
private val MIST_BANDS = listOf(
    MistBand(topFraction = 0.14f, heightFraction = 0.11f, alpha = 0.07f, millis = 52000, drift = 0.26f),
    MistBand(topFraction = 0.40f, heightFraction = 0.07f, alpha = 0.04f, millis = 68000, drift = -0.18f),
    MistBand(topFraction = 0.64f, heightFraction = 0.10f, alpha = 0.05f, millis = 44000, drift = 0.32f),
)

private data class MistBand(
    val topFraction: Float,
    val heightFraction: Float,
    val alpha: Float,
    val millis: Int,
    /** 一个周期内水平漂移的屏宽比例，正负决定方向。 */
    val drift: Float,
)

/**
 * 蒙版竖直渐变的停靠点（占屏高比例）：
 * 0 → 0.18 顶部加重（状态栏 + TopBar 区），0.18 → 0.38 过渡到「轻全局」,
 * 0.38 → 0.62 中部保持最轻（这一段是正文/卡片区，靠玻璃卡片自己的底保证可读），
 * 0.62 → 1.0 底部渐重（输入条 / 底部导航）。
 * 6 个停靠点 + 相邻色插值，避免「三段硬切」在山水上留下可见的横线。
 */
private val SCRIM_STOPS = floatArrayOf(0.0f, 0.18f, 0.38f, 0.62f, 0.80f, 1.0f)

/**
 * z4 蒙版层：轻全局 + 上下局部加重，平滑过渡，不整屏重压。
 *
 * 数值来自 LumoPalette.scrim*（自带 alpha，这里不再二次乘透明度）：
 * 浅色 顶部 40% 宣纸 / 中部 8% / 底部 50%；深色 顶部 40% / 中部 10% / 底部 52%。
 * 2026-11 真机回归后整体减淡（上一版 56/19/60 与 52/32/62 把山水压死了），
 * 中部最薄，正文可读性交给玻璃卡片，见 ContrastTest 的「最薄蒙版最坏场景」用例。
 */
@Composable
internal fun ScrimLayer(modifier: Modifier = Modifier) {
    val palette: LumoPalette = LocalLumoPalette.current
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    SCRIM_STOPS[0] to palette.scrimTop,
                    SCRIM_STOPS[1] to lerp(palette.scrimTop, palette.scrimMid, 0.55f),
                    SCRIM_STOPS[2] to palette.scrimMid,
                    SCRIM_STOPS[3] to palette.scrimMid,
                    SCRIM_STOPS[4] to lerp(palette.scrimMid, palette.scrimBottom, 0.55f),
                    SCRIM_STOPS[5] to palette.scrimBottom,
                ),
            ),
        )
    }
}

/**
 * z1 位图层。动效关闭时不建 `rememberInfiniteTransition`（避免空转），直接用静态 1.0。
 */
@Composable
internal fun SceneImageLayer(scene: Scene, motionEnabled: Boolean, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(if (dark) scene.nightRes else scene.dayRes)
        .size(1080, 2376)
        .allowHardware(true)
        // 交叉淡入交给 CrossfadeScene 统一处理，这里淡入两次会看到「闪一下」。
        .crossfade(false)
        .build()

    val kenBurns = rememberKenBurns(motionEnabled)
    val driftPx = with(LocalDensity.current) { LumoMotion.kenBurnsDriftDp.dp.toPx() }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = kenBurns?.value
                val scale = if (p == null) {
                    LumoMotion.kenBurnsScaleFrom
                } else {
                    LumoMotion.kenBurnsScaleFrom + (LumoMotion.kenBurnsScaleTo - LumoMotion.kenBurnsScaleFrom) * p
                }
                scaleX = scale
                scaleY = scale
                translationY = if (p == null) 0f else -driftPx * p
                transformOrigin = TransformOrigin(0.5f, 0.45f)
            },
    )
}

/** 动效关闭时返回 null，调用处据此跳过每一次帧读值。 */
@Composable
private fun rememberKenBurns(motionEnabled: Boolean): State<Float>? {
    if (!motionEnabled) return null
    val transition = rememberInfiniteTransition(label = "kenBurns")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LumoMotion.kenBurnsMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kenBurnsProgress",
    )
}

/**
 * z2 云雾层：三条横向渐变带，用 draw 阶段读取动画值，不触发整页重组。
 */
@Composable
internal fun MistLayer(motionEnabled: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalLumoPalette.current
    val mistColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        palette.paperGlass
    } else {
        Color(0xFFFFFDF6)
    }
    val drifts = rememberMistDrifts(motionEnabled)

    Canvas(modifier = modifier.fillMaxSize()) {
        MIST_BANDS.forEachIndexed { index, band ->
            val phase = drifts?.getOrNull(index)?.value ?: 0f
            val bandHeight = size.height * band.heightFraction
            val top = size.height * band.topFraction
            // 带本身比屏幕宽，左右各留出漂移余量，平移时两端不会露出空档。
            val bandWidth = size.width * 1.5f
            val left = -size.width * 0.25f + size.width * band.drift * phase
            val alpha = if (band.alpha <= 0f) 0f else band.alpha
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        mistColor.copy(alpha = 0f),
                        mistColor.copy(alpha = alpha),
                        mistColor.copy(alpha = alpha * 0.55f),
                        mistColor.copy(alpha = 0f),
                    ),
                    startX = left,
                    endX = left + bandWidth,
                ),
                topLeft = Offset(left, top),
                size = Size(bandWidth, bandHeight),
            )
        }
    }
}

@Composable
private fun rememberMistDrifts(motionEnabled: Boolean): List<State<Float>>? {
    if (!motionEnabled) return null
    val transition = rememberInfiniteTransition(label = "mist")
    return MIST_BANDS.mapIndexed { index, band ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = band.millis, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "mistDrift$index",
        )
    }
}

/**
 * z3 光晕层：左上角一枚月白径向渐变，给画面一个光源方向。
 */
@Composable
internal fun HaloLayer(modifier: Modifier = Modifier) {
    val palette = LocalLumoPalette.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glow = if (dark) palette.gold else Color(0xFFFFF6E2)
    val peak = if (dark) 0.10f else 0.16f

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.20f, size.height * 0.08f)
        val radius = size.width * 0.85f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow.copy(alpha = peak), glow.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

/** 三层可动背景的合体（不含位图层，位图层由 CrossfadeScene 负责交叉淡入）。 */
@Composable
internal fun AtmosphericLayers(motionEnabled: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        MistLayer(motionEnabled = motionEnabled)
        HaloLayer()
        ScrimLayer()
    }
}
