package com.yuguang.lumo

import com.yuguang.lumo.ui.theme.LumoMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动效规格的守门测试。
 *
 * 两个目的：
 * 1. 关动效必须是「真的 0ms」，而不是 1ms 的伪零（否则 still 会调度一帧动画）。
 * 2. 数值之间的相对关系（背景慢、界面快）不能在某次随手改值里被破坏。
 */
class MotionSpecTest {

    @Test
    fun `关动效时一切时长归零`() {
        val specs = listOf(
            LumoMotion.sceneFadeMillis,
            LumoMotion.kenBurnsMillis,
            LumoMotion.mistMillisMin,
            LumoMotion.mistMillisMax,
            LumoMotion.navEnterMillis,
            LumoMotion.navExitMillis,
            LumoMotion.staggerMillis,
            LumoMotion.inkRippleMillis,
            LumoMotion.thinkingMillis,
            LumoMotion.sealBreathMillis,
            LumoMotion.chartBarMillis,
            LumoMotion.chartBarStagger,
        )
        specs.forEach { base ->
            assertEquals("duration($base, false) 必须是 0", 0, LumoMotion.duration(base, false))
            assertEquals("duration($base, true) 应原样返回", base, LumoMotion.duration(base, true))
        }
        assertEquals(0, LumoMotion.duration(0, true))
        assertEquals(0, LumoMotion.duration(0, false))
    }

    @Test
    fun `背景极慢而界面极快`() {
        assertTrue("换场景 1400ms", LumoMotion.sceneFadeMillis == 1400)
        assertTrue("推镜 26s", LumoMotion.kenBurnsMillis == 26000)
        assertTrue("云雾 40–70s", LumoMotion.mistMillisMin == 40000 && LumoMotion.mistMillisMax == 70000)
        assertTrue("一层云雾不该比换场更快", LumoMotion.mistMillisMin > LumoMotion.sceneFadeMillis)
        assertTrue("推镜该比换场长得多", LumoMotion.kenBurnsMillis > LumoMotion.sceneFadeMillis * 10)

        assertTrue("导航进场 220ms", LumoMotion.navEnterMillis == 220)
        assertTrue("导航退场 180ms", LumoMotion.navExitMillis == 180)
        assertTrue("出场必须比进场快", LumoMotion.navExitMillis < LumoMotion.navEnterMillis)
        assertTrue("界面时长不该超过 400ms", LumoMotion.navEnterMillis <= 400)
        assertTrue("逐条间隔小于单条入场", LumoMotion.staggerMillis < LumoMotion.navEnterMillis)
        assertTrue(LumoMotion.staggerMillis == 40)
    }

    @Test
    fun `推镜幅度温和且在合理区间`() {
        assertEquals(1f, LumoMotion.kenBurnsScaleFrom, 0f)
        assertEquals(1.09f, LumoMotion.kenBurnsScaleTo, 0f)
        assertTrue("推镜必须放大", LumoMotion.kenBurnsScaleTo > LumoMotion.kenBurnsScaleFrom)
        assertTrue("放大超过 12% 会有明显画质损失", LumoMotion.kenBurnsScaleTo <= 1.12f)
        assertTrue("漂移幅度应有实感", LumoMotion.kenBurnsDriftDp in 6..24)
    }

    @Test
    fun `内容型动效时长在合理区间`() {
        assertTrue(LumoMotion.inkRippleMillis in 280..600)
        assertTrue(LumoMotion.thinkingMillis in 800..1600)
        assertTrue(LumoMotion.sealBreathMillis in 1600..3200)
        assertTrue("呼吸要比墨滴慢", LumoMotion.sealBreathMillis > LumoMotion.thinkingMillis)
        assertTrue(LumoMotion.chartBarMillis in 300..900)
        assertTrue("柱间错峰小于单柱生长", LumoMotion.chartBarStagger < LumoMotion.chartBarMillis)
    }

    @Test
    fun `缓动曲线是快出慢收且端点归位`() {
        val ease = LumoMotion.ease
        assertEquals(0f, ease.transform(0f), 0.01f)
        assertEquals(1f, ease.transform(1f), 0.01f)
        // cubic-bezier(0.22, 1, 0.36, 1)：半程已经走完大半，手感是「落纸即定」。
        val mid = ease.transform(0.5f)
        assertTrue("半程进度应显著超前，实际 $mid", mid > 0.6f)
        assertTrue("曲线必须单调", ease.transform(0.25f) <= ease.transform(0.5f))
        assertTrue(ease.transform(0.5f) <= ease.transform(0.75f))
    }
}
