package com.yuguang.lumo.ui.backdrop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LocalMotionEnabled
import com.yuguang.lumo.ui.theme.LumoMotion
import com.yuguang.lumo.ui.theme.Scene

/** 当前页面所在的山水场景；页面内想做「同场景」装饰时读它，不必层层传参。 */
val LocalScene = compositionLocalOf { Scene.Cloud }

/**
 * 页面底：山水背景 + 蒙版 + 内容容器。
 *
 * 页面骨架写法（W3/W4 直接照抄）：
 * ```
 * BackdropHost(scene = SceneryCatalog.forRoute(route)) {
 *     Column(Modifier.fillMaxSize().statusBarsPadding()) { ... }
 * }
 * ```
 * 内容画在 z5（见 SceneBackdrop.kt 顶部层序注释），本身不需要再铺背景色；
 * 需要「浮在磨砂宣纸上」的区块套 `Modifier.glass()` 或 `GlassCard`。
 */
@Composable
fun BackdropHost(
    scene: Scene,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    // 内容默认色：透明 Scaffold 不会把 onBackground 塞进 LocalContentColor，
    // 不给就会被 Compose 兜成 Color.Black —— 浅色主题下碰巧看不出来，深色主题会变成黑字压夜色。
    CompositionLocalProvider(
        LocalScene provides scene,
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            CrossfadeScene(scene = scene)
            // 内容层单独一层 Box：内容自己决定是否 statusBarsPadding / navigationBarsPadding。
            Box(modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}

/**
 * 只有背景、没有内容的山水底，切换场景时 1400ms 交叉淡入。
 *
 * 自己再包一层 `BackdropHost` 也能用（会叠加蒙版），但正常用法是：
 * 一次性放进 `BackdropHost`，或做「设置页预览小图」时单独调用。
 *
 * 交叉淡入只作用在位图层：云雾/光晕/蒙版是场景无关的常驻层，放在 Crossfade 外面，
 * 既省一次图层拷贝，也避免淡入中途蒙版叠两层导致忽明忽暗。
 */
@Composable
fun CrossfadeScene(
    scene: Scene,
    durationMillis: Int = LumoMotion.sceneFadeMillis,
) {
    val motionEnabled = LocalMotionEnabled.current
    val millis = LumoMotion.duration(durationMillis, motionEnabled)

    Box(modifier = Modifier.fillMaxSize().background(LocalLumoPalette.current.paper)) {
        if (millis <= 0) {
            // 关动效：直接呈现终态，不创建任何过渡容器。
            SceneImageLayer(scene = scene, motionEnabled = false)
        } else {
            Crossfade(
                targetState = scene,
                animationSpec = tween(durationMillis = millis, easing = LumoMotion.ease),
                label = "sceneCrossfade",
            ) { target ->
                // 旧场景在淡出结束后离开组合，Coil 的位图随之可回收（内存缓存仍受限）。
                SceneImageLayer(scene = target, motionEnabled = true)
            }
        }
        AtmosphericLayers(motionEnabled = motionEnabled)
    }
}
