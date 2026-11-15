package com.yuguang.lumo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yuguang.lumo.ui.theme.LocalMotionEnabled
import com.yuguang.lumo.ui.theme.Skin
import com.yuguang.lumo.ui.theme.LocalSkin
import com.yuguang.lumo.ui.theme.LumoMotion

/**
 * 逐条入场：淡入 + 向上 16dp，第 index 条再延迟 index×40ms。
 *
 * 列表里这样写：
 * ```
 * items(list) { i, item -> AnimatedEntrance(index = i) { Row { ... } } }
 * ```
 * 关动效时延迟与位移都归零，内容立刻在终态出现（不会「看不见」）。
 */
@Composable
fun AnimatedEntrance(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val motion = LocalMotionEnabled.current
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { state.targetState = true }

    val duration = LumoMotion.duration(LumoMotion.navEnterMillis, motion)
    // 上限 400ms：长列表一屏 30 条时，不让最后一条等到 1.2s 才出现。
    val delay = if (motion) (index * LumoMotion.staggerMillis).coerceAtMost(400) else 0
    val risePx = with(LocalDensity.current) { 16.dp.roundToPx() }

    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = duration, delayMillis = delay, easing = LumoMotion.ease)) +
            slideInVertically(
                animationSpec = tween(durationMillis = duration, delayMillis = delay, easing = LumoMotion.ease),
                initialOffsetY = { if (motion) risePx else 0 },
            ),
        exit = ExitTransition.None,
        content = content,
    )
}

/**
 * 按压缩放（0.96，spring dampingRatio=0.55）。
 *
 * 用 Initial 阶段的「旁观式」指针监听：只读事件、不消费，所以可以直接包住 ImageButton / Card，
 * 里面原有的点击照样生效。
 */
@Composable
fun PressScale(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = LocalMotionEnabled.current
    val pressed = remember { androidx.compose.runtime.mutableStateOf(false) }
    val scale = animateFloatAsState(
        targetValue = if (pressed.value && motion && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = scale.value
                scaleX = s
                scaleY = s
            }
            .pointerInput(enabled, motion) {
                if (!enabled || !motion) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressed.value = true
                    try {
                        waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    } finally {
                        pressed.value = false
                    }
                }
            },
        content = content,
    )
}

/** 一趟导航动画的四条规格。 */
data class LumoNavSpec(
    val enter: EnterTransition,
    val exit: ExitTransition,
    val popEnter: EnterTransition,
    val popExit: ExitTransition,
)

/**
 * NavHost 的转场规格：前进时新页从右侧极轻地推入（1/16 屏宽），返回时旧页退 1/24 屏宽。
 * 幅度刻意小 —— 背景山水不能跟着大幅位移，否则会「晃」。
 *
 * 用法：
 * ```
 * val spec = rememberNavSpec()
 * NavHost(nav, startDestination = ..., enterTransition = { spec.enter }, ...)
 * ```
 */
object LumoNavTransitions {
    /**
     * [skin] 只影响「进入的手感」：山水是横向推入（像翻页），
     * 弧光是原地放大 + 淡入（像从雾里显形）。时长与缓动两边一致 —— 换皮肤不该换节奏。
     */
    fun specs(motionEnabled: Boolean, skin: Skin = Skin.Default): LumoNavSpec {
        if (skin == Skin.Arcane) return arcaneSpecs(motionEnabled)
        val inMillis = LumoMotion.duration(LumoMotion.navEnterMillis, motionEnabled)
        val outMillis = LumoMotion.duration(LumoMotion.navExitMillis, motionEnabled)

        return LumoNavSpec(
            enter = fadeIn(tween(durationMillis = inMillis, easing = LumoMotion.ease)) +
                slideInHorizontally(
                    animationSpec = tween(durationMillis = inMillis, easing = LumoMotion.ease),
                    initialOffsetX = { if (motionEnabled) it / 16 else 0 },
                ),
            exit = fadeOut(tween(durationMillis = outMillis, easing = LumoMotion.ease)) +
                slideOutHorizontally(
                    animationSpec = tween(durationMillis = outMillis, easing = LumoMotion.ease),
                    targetOffsetX = { if (motionEnabled) -it / 24 else 0 },
                ),
            popEnter = fadeIn(tween(durationMillis = inMillis, easing = LumoMotion.ease)) +
                slideInHorizontally(
                    animationSpec = tween(durationMillis = inMillis, easing = LumoMotion.ease),
                    initialOffsetX = { if (motionEnabled) -it / 16 else 0 },
                ),
            popExit = fadeOut(tween(durationMillis = outMillis, easing = LumoMotion.ease)) +
                slideOutHorizontally(
                    animationSpec = tween(durationMillis = outMillis, easing = LumoMotion.ease),
                    targetOffsetX = { if (motionEnabled) it / 24 else 0 },
                ),
        )
    }

    private fun arcaneSpecs(motionEnabled: Boolean): LumoNavSpec {
        val inMillis = LumoMotion.duration(LumoMotion.navEnterMillis, motionEnabled)
        val outMillis = LumoMotion.duration(LumoMotion.navExitMillis, motionEnabled)
        val enter = fadeIn(tween(durationMillis = inMillis, easing = LumoMotion.ease)) +
            scaleIn(
                animationSpec = tween(durationMillis = inMillis, easing = LumoMotion.ease),
                initialScale = if (motionEnabled) 1.012f else 1f,
            )
        val exit = fadeOut(tween(durationMillis = outMillis, easing = LumoMotion.ease)) +
            scaleOut(
                animationSpec = tween(durationMillis = outMillis, easing = LumoMotion.ease),
                targetScale = if (motionEnabled) 0.994f else 1f,
            )
        return LumoNavSpec(enter = enter, exit = exit, popEnter = enter, popExit = exit)
    }

    /** 开动效的默认规格，供非 Composable 场景直接引用。 */
    val default: LumoNavSpec = specs(true)

    /** 关动效规格：时长 0、位移 0，一次性到位。 */
    val none: LumoNavSpec = specs(false)
}

/** 读 [LocalMotionEnabled] 之后再取规格，避免页面自己判断开关。 */
@Composable
fun rememberNavSpec(): LumoNavSpec {
    val motion = LocalMotionEnabled.current
    val skin = LocalSkin.current
    return remember(motion, skin) { LumoNavTransitions.specs(motion, skin) }
}
