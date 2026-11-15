package com.yuguang.lumo.ui.theme

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.yuguang.lumo.ui.prefs.UiPreferences

/**
 * 全应用动效规格表：数值集中在常量里，页面只引用不写魔法数字。
 *
 * 时长取向：山水层极慢（云雾 40–70s、推镜 26s），内容层极快（导航 180–220ms、逐条 40ms），
 * 让「背景像自然」而「界面像响应」，两者不抢注意力。
 */
object LumoMotion {
    /** 换场景时背景交叉淡入。 */
    const val sceneFadeMillis = 1400

    /** Ken Burns：一次推镜的完整往返时长。 */
    const val kenBurnsMillis = 26000
    const val kenBurnsScaleFrom = 1f
    const val kenBurnsScaleTo = 1.09f

    /** 推镜同时向下漂移的幅度（dp）。 */
    const val kenBurnsDriftDp = 12

    /** 云雾漂移一轮的时长区间（每个云雾带各自随机取）。 */
    const val mistMillisMin = 40000
    const val mistMillisMax = 70000

    /** 页面进入 / 退出。 */
    const val navEnterMillis = 220
    const val navExitMillis = 180

    /** 列表逐条入场间隔。 */
    const val staggerMillis = 40

    /** 墨点涟漪扩散。 */
    const val inkRippleMillis = 400

    /** 「正在落笔」墨滴循环。 */
    const val thinkingMillis = 1200

    /** 印章呼吸。 */
    const val sealBreathMillis = 2400

    /** 图表柱生长与间隔。 */
    const val chartBarMillis = 500
    const val chartBarStagger = 60

    /** 统一缓动：快出慢收，接近毛笔落纸的手感。 */
    val ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** 关动效时统一归零，避免各页面各写一份 if。 */
    fun duration(base: Int, enabled: Boolean): Int = if (enabled) base else 0
}

/**
 * 当前是否允许动效。默认 true，由 [rememberMotionEnabled] 或页面自行覆写。
 */
val LocalMotionEnabled = compositionLocalOf { true }

/**
 * 综合「用户开关 && 系统动效开关」。API < 26 没有 [ValueAnimator.areAnimatorsEnabled]，
 * 只取用户开关（minSdk 24，必须做版本判断）。
 */
@Composable
fun rememberMotionEnabled(uiPrefs: UiPreferences): Boolean {
    val userWants by uiPrefs.motion.collectAsState()
    val systemAllows = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            true
        }
    }
    return userWants && systemAllows
}
