package com.yuguang.lumo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 对比度守门：不依赖 Android，自己实现 WCAG 的 sRGB 相对亮度与 alpha 合成。
 *
 * 为什么要在 JVM 里算：玻璃卡片的实际底色是「纸色 × 透明度 + 山水色调 × (1-透明度)」，
 * 山水是照片，没法在测试里真的采样，所以取素材色调的两个极端（最亮 #E8DCC4 / 最暗 #1A211D）
 * 当作最坏情形，再叠上真实的蒙版与玻璃参数（浅色 α0.76 / 深色 α0.62 是 level 0 的下限）。
 */
class ContrastTest {

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** sRGB 相对亮度（WCAG 2.x）。 */
    private fun luminance(color: Long): Double {
        val r = channel(((color shr 16) and 0xFF).toInt())
        val g = channel(((color shr 8) and 0xFF).toInt())
        val b = channel((color and 0xFF).toInt())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** 把 fg 以 alpha 叠在 bg 上，返回合成后的不透明色。 */
    private fun blend(fg: Long, alpha: Double, bg: Long): Long {
        fun mix(shift: Int): Int {
            val f = ((fg shr shift) and 0xFF).toDouble()
            val b = ((bg shr shift) and 0xFF).toDouble()
            return (alpha * f + (1.0 - alpha) * b).roundToInt().coerceIn(0, 255)
        }
        return (0xFFL shl 24) or (mix(16).toLong() shl 16) or (mix(8).toLong() shl 8) or mix(0).toLong()
    }

    private fun assertAtLeast(min: Double, actual: Double, what: String) {
        assertTrue("$what 对比度 ${"%.2f".format(actual)} < $min", actual >= min)
    }

    @Test
    fun `相对亮度实现自检`() {
        // 纯黑 / 纯白的对比度是 21:1，这是 WCAG 公式的固定校验点。
        assertEquals(21.0, contrast(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, contrast(0xFF777777, 0xFF777777), 0.0001)
    }

    @Test
    fun `浅色 墨色文字在宣纸卡片上达标`() {
        assertAtLeast(4.5, contrast(0xFF232A26, 0xFFFAF5EA), "浅色 墨/纸")
        // 次级文字与最淡的辅助文字也过 4.5（LightFaint 只用于图标，不用于正文）。
        assertAtLeast(4.5, contrast(0xFF4C574F, 0xFFFAF5EA), "浅色 次级/纸")
    }

    @Test
    fun `深色 月白文字在夜墨卡片上达标`() {
        assertAtLeast(4.5, contrast(0xFFEFE7D6, 0xFF16201C), "深色 月白/夜墨")
        assertAtLeast(4.5, contrast(0xFFA9B5AC, 0xFF16201C), "深色 次级/夜墨")
    }

    // ---- 玻璃卡片叠在山水的两端色调上（含当前蒙版） ----

    /**
     * 场景色调 → 中部蒙版 → 玻璃卡片 的三层合成。
     *
     * 蒙版取 `LumoPalette.scrimMid`：全屏最薄的一层，也就是最不利于文字的位置
     * （浅色 #F2EADA α0.08、深色 #0F1513 α0.10，见 AiTheme.kt）。
     *
     * 玻璃底色（见 Glass.kt 的 glassAlpha / base）：
     * - 浅色 Paper：paperGlass 再向白 lerp 0.22 → #FBF7EF，α = 0.76 + 0.06 * level（0.76 / 0.82）
     * - 深色 Paper：#17221E，α = 0.62 + 0.18 * level（0.62 / 0.80）
     */
    private fun cardOverScene(
        glass: Long,
        glassAlpha: Double,
        scrim: Long,
        scrimAlpha: Double,
        sceneTone: Long,
    ): Long = blend(glass, glassAlpha, blend(scrim, scrimAlpha, sceneTone))

    private val brightestSceneTone = 0xFFE8DCC4 // 宣纸山水的高光纸色
    private val darkestSceneTone = 0xFF1A211D // 夜色山水的暗部
    private val lightScrim = 0xFFF2EADA
    private val darkScrim = 0xFF0F1513
    private val lightScrimMid = 0.08
    private val darkScrimMid = 0.10
    private val lightGlassFill = 0xFFFBF7EF
    private val darkGlassFill = 0xFF17221E
    private val lightGlassAlphaMin = 0.76
    private val lightGlassAlphaFull = 0.82
    private val darkGlassAlphaMin = 0.62
    private val darkGlassAlphaFull = 0.80

    @Test
    fun `正文墨色在最薄蒙版的最坏场景上仍达标`() {
        val lightOnBright =
            cardOverScene(lightGlassFill, lightGlassAlphaMin, lightScrim, lightScrimMid, brightestSceneTone)
        val lightOnDark =
            cardOverScene(lightGlassFill, lightGlassAlphaMin, lightScrim, lightScrimMid, darkestSceneTone)
        assertEquals("浅色墨/最亮场景", 13.05, contrast(0xFF232A26, lightOnBright), 0.05)
        assertEquals("浅色墨/最暗场景", 8.68, contrast(0xFF232A26, lightOnDark), 0.05)
        assertAtLeast(4.5, contrast(0xFF232A26, lightOnBright), "浅色墨/最亮场景上的卡片")
        assertAtLeast(4.5, contrast(0xFF232A26, lightOnDark), "浅色墨/最暗场景上的卡片")

        val darkOnBright =
            cardOverScene(darkGlassFill, darkGlassAlphaMin, darkScrim, darkScrimMid, brightestSceneTone)
        val darkOnDark =
            cardOverScene(darkGlassFill, darkGlassAlphaMin, darkScrim, darkScrimMid, darkestSceneTone)
        assertEquals("深色月白/最亮场景", 5.14, contrast(0xFFEFE7D6, darkOnBright), 0.05)
        assertEquals("深色月白/最暗场景", 13.40, contrast(0xFFEFE7D6, darkOnDark), 0.05)
        assertAtLeast(4.5, contrast(0xFFEFE7D6, darkOnBright), "深色月白/最亮场景上的卡片")
        assertAtLeast(4.5, contrast(0xFFEFE7D6, darkOnDark), "深色月白/最暗场景上的卡片")
    }

    @Test
    fun `次级文字需要更实的玻璃 level 1 才达标`() {
        // onSurfaceVariant 级别的文案在 level 0 的薄玻璃上仍不达标（浅 4.46 / 深 2.98，一个擦线一个明显不够），
        // 所以约定：次级小字必须落在 level >= 0.8 的卡片上。level 1 时两边都过 4.5。
        val lightSubOnThin = contrast(
            0xFF4C574F,
            cardOverScene(lightGlassFill, lightGlassAlphaMin, lightScrim, lightScrimMid, darkestSceneTone),
        )
        val darkSubOnThin = contrast(
            0xFFA9B5AC,
            cardOverScene(darkGlassFill, darkGlassAlphaMin, darkScrim, darkScrimMid, brightestSceneTone),
        )
        assertTrue("浅色次级文字在最薄玻璃上不该被当成达标：${"%.2f".format(lightSubOnThin)}", lightSubOnThin < 4.5)
        assertTrue("深色次级文字在最薄玻璃上不该被当成达标：${"%.2f".format(darkSubOnThin)}", darkSubOnThin < 4.5)

        val lightSub = cardOverScene(lightGlassFill, lightGlassAlphaFull, lightScrim, lightScrimMid, darkestSceneTone)
        val darkSub = cardOverScene(darkGlassFill, darkGlassAlphaFull, darkScrim, darkScrimMid, brightestSceneTone)
        assertEquals("浅色次级/level 1", 5.05, contrast(0xFF4C574F, lightSub), 0.05)
        assertEquals("深色次级/level 1", 4.80, contrast(0xFFA9B5AC, darkSub), 0.05)
        assertAtLeast(4.5, contrast(0xFF4C574F, lightSub), "浅色次级/level 1 卡片")
        assertAtLeast(4.5, contrast(0xFFA9B5AC, darkSub), "深色次级/level 1 卡片")
    }

    // ---- 弧光学院皮肤（magic skin）：同一套三层合成模型再算一遍 ----

    /**
     * 弧光的两套纸面与玻璃参数（见 AiTheme.kt 的 LightArcanePalette / DarkArcanePalette）：
     * - 浅色「晨雾羊皮纸」：paper #EDE4D0、paperGlass #F8F2E3（玻璃填充再向白 lerp 0.22 → #FAF5E9，α0.76/0.82）
     * - 深色「午夜星穹」：paper #0B1020、paperGlass #1A2138，α0.62/0.80
     * 蒙版用 scrimMid（浅 0.08 / 深 0.10），场景色调仍取古画素材的最亮/最暗两端。
     */
    private val arcaneLightPaper = 0xFFEDE4D0
    private val arcaneLightInk = 0xFF1E2230
    private val arcaneLightSub = 0xFF4C5060
    private val arcaneLightGlassFill = 0xFFFAF5E9
    private val arcaneDarkPaper = 0xFF0B1020
    private val arcaneDarkInk = 0xFFEFE9D8
    private val arcaneDarkSub = 0xFFB0B3CC
    private val arcaneDarkGlassFill = 0xFF1A2138
    private val arcaneScrimMid = 0.08
    private val arcaneDarkScrimMid = 0.10
    private val arcaneAccentLight = 0xFFA8402C
    private val arcaneAccentDark = 0xFFD2643F
    private val arcaneGoldLight = 0xFFB8892F
    private val arcaneGoldDark = 0xFFD6AE5C

    @Test
    fun `弧光浅色 墨色与次级文字在羊皮纸上达标`() {
        assertAtLeast(4.5, contrast(arcaneLightInk, arcaneLightPaper), "弧光浅色 墨/纸")
        assertAtLeast(4.5, contrast(arcaneLightSub, arcaneLightPaper), "弧光浅色 次级/纸")
        assertEquals("弧光浅色 墨/纸", 12.52, contrast(arcaneLightInk, arcaneLightPaper), 0.05)
        assertEquals("弧光浅色 次级/纸", 6.33, contrast(arcaneLightSub, arcaneLightPaper), 0.05)
    }

    @Test
    fun `弧光深色 月白与次级文字在星穹上达标`() {
        assertAtLeast(4.5, contrast(arcaneDarkInk, arcaneDarkPaper), "弧光深色 月白/星穹")
        assertAtLeast(4.5, contrast(arcaneDarkSub, arcaneDarkPaper), "弧光深色 次级/星穹")
        assertEquals("弧光深色 月白/星穹", 15.61, contrast(arcaneDarkInk, arcaneDarkPaper), 0.05)
        assertEquals("弧光深色 次级/星穹", 9.17, contrast(arcaneDarkSub, arcaneDarkPaper), 0.05)
    }

    @Test
    fun `弧光正文叠在最亮与最暗场景上都过 4_5`() {
        val lightOnBright = cardOverScene(
            arcaneLightGlassFill, lightGlassAlphaMin, arcaneLightPaper, arcaneScrimMid, brightestSceneTone,
        )
        val lightOnDark = cardOverScene(
            arcaneLightGlassFill, lightGlassAlphaMin, arcaneLightPaper, arcaneScrimMid, darkestSceneTone,
        )
        assertEquals("弧光浅色墨/最亮场景", 13.82, contrast(arcaneLightInk, lightOnBright), 0.05)
        assertEquals("弧光浅色墨/最暗场景", 9.23, contrast(arcaneLightInk, lightOnDark), 0.05)

        val darkOnBright = cardOverScene(
            arcaneDarkGlassFill, darkGlassAlphaMin, arcaneDarkPaper, arcaneDarkScrimMid, brightestSceneTone,
        )
        val darkOnDark = cardOverScene(
            arcaneDarkGlassFill, darkGlassAlphaMin, arcaneDarkPaper, arcaneDarkScrimMid, darkestSceneTone,
        )
        assertEquals("弧光深色月白/最亮场景", 5.14, contrast(arcaneDarkInk, darkOnBright), 0.05)
        assertEquals("弧光深色月白/最暗场景", 13.42, contrast(arcaneDarkInk, darkOnDark), 0.05)
        assertAtLeast(4.5, contrast(arcaneDarkInk, darkOnBright), "弧光深色月白/最亮场景上的卡片")
    }

    @Test
    fun `弧光次级文字同样需要 level 0_8 以上的玻璃`() {
        // 浅色：level 0 已经 4.67，最低一档也过线；深色 level 0 只有 3.02，必须落在 level 1。
        val lightThin = cardOverScene(
            arcaneLightGlassFill, lightGlassAlphaMin, arcaneLightPaper, arcaneScrimMid, darkestSceneTone,
        )
        val darkThin = cardOverScene(
            arcaneDarkGlassFill, darkGlassAlphaMin, arcaneDarkPaper, arcaneDarkScrimMid, brightestSceneTone,
        )
        assertAtLeast(4.5, contrast(arcaneLightSub, lightThin), "弧光浅色次级/第一档玻璃")
        assertTrue(
            "弧光深色次级在最薄玻璃上不该被当成达标：${"%.2f".format(contrast(arcaneDarkSub, darkThin))}",
            contrast(arcaneDarkSub, darkThin) < 4.5,
        )
        val lightFull = cardOverScene(
            arcaneLightGlassFill, lightGlassAlphaFull, arcaneLightPaper, arcaneScrimMid, darkestSceneTone,
        )
        val darkFull = cardOverScene(
            arcaneDarkGlassFill, darkGlassAlphaFull, arcaneDarkPaper, arcaneDarkScrimMid, brightestSceneTone,
        )
        assertEquals("弧光浅色次级/level 1", 5.29, contrast(arcaneLightSub, lightFull), 0.05)
        assertEquals("弧光深色次级/level 1", 4.82, contrast(arcaneDarkSub, darkFull), 0.05)
        assertAtLeast(4.5, contrast(arcaneDarkSub, darkFull), "弧光深色次级/level 1 卡片")
    }

    @Test
    fun `弧光余烬与古金的用法边界`() {
        // 余烬（tertiary）两套皮肤都能当正文色。
        assertAtLeast(4.5, contrast(arcaneAccentLight, arcaneLightPaper), "余烬/羊皮纸")
        assertAtLeast(4.5, contrast(arcaneAccentDark, arcaneDarkPaper), "余烬/星穹")
        // 古金在深色下够亮（可做正文/图标），浅色下只有 2.49 —— 只做图形（菱徽、星点、细线），不做正文。
        assertAtLeast(4.5, contrast(arcaneGoldDark, arcaneDarkPaper), "古金/星穹")
        assertTrue(
            "古金在浅色下不应被当成正文色：${"%.2f".format(contrast(arcaneGoldLight, arcaneLightPaper))}",
            contrast(arcaneGoldLight, arcaneLightPaper) < 3.0,
        )
    }

    // ---- 玻璃卡「浮起」的结构断言（r2 真机灰阶实测反推） ----

    /** 灰度均值：和真机截图统计口径一致（三通道等权）。 */
    private fun gray(color: Long): Double =
        (((color shr 16) and 0xFF) + ((color shr 8) and 0xFF) + (color and 0xFF)) / 3.0

    @Test
    fun `浅色卡片比山水背景更亮且至少高六个灰阶`() {
        // r2 实测：旧参数（填充 #FAF5EA α0.62）卡均值 202.0，背景均值 211.3 → 卡比背景暗 9。
        // 由 202.0 = 0.62 * 243 + 0.38 * B 反推出卡片局部背景灰度 B ≈ 135.1。
        val localBackdropGray = 135.1
        val globalBackdropGray = 211.3
        val bg = localBackdropGray.roundToInt().toLong()
        val backdrop = (0xFFL shl 24) or (bg shl 16) or (bg shl 8) or bg
        val card = blend(lightGlassFill, lightGlassAlphaMin, backdrop)
        val cardGray = gray(card)
        // 局部：卡片必须比它脚下那片山水亮（否则就是「陷进去」）。
        assertTrue(
            "浅色卡片 ${"%.1f".format(cardGray)} 未高过局部背景 ${"%.1f".format(localBackdropGray)} + 6",
            cardGray >= localBackdropGray + 6,
        )
        // 全局：对着整页背景均值，也必须是「更亮」的那一方（旧参数是 -9）。
        assertTrue(
            "浅色卡片 ${"%.1f".format(cardGray)} 未高过整页背景 ${"%.1f".format(globalBackdropGray)} + 6",
            cardGray >= globalBackdropGray + 6,
        )
    }

    @Test
    fun `夜间卡片相对背景亮度比不低于一点二五`() {
        // r2 实测：旧参数（填充 #16201C α0.62）卡均值 54.1，背景均值 39.5。
        // 由 54.1 = 0.62 * 27 + 0.38 * B 反推出卡片局部背景灰度 B ≈ 97.8。
        val localBackdropGray = 97.8
        val backgroundGray = 39.5
        val backdropGray = localBackdropGray.roundToInt().toLong()
        val backdrop = (0xFFL shl 24) or (backdropGray shl 16) or (backdropGray shl 8) or backdropGray

        val cardGray = gray(blend(darkGlassFill, darkGlassAlphaMin, backdrop))
        val ratio = cardGray / backgroundGray
        assertTrue("夜间卡/背景亮度比 ${"%.3f".format(ratio)} < 1.25", ratio >= 1.25)

        // 提亮幅度：深色纸面灰度 27 → 29，约 +6%（通道 1.045–1.071）。
        assertEquals("深色纸面提亮后灰度", 29.0, gray(darkGlassFill), 0.5)
        assertTrue(
            "深色纸面提亮倍数 ${"%.3f".format(gray(darkGlassFill) / 27.0)} 不在 1.04–1.08",
            (gray(darkGlassFill) / 27.0) in 1.04..1.08,
        )
        // 材质的结构性保证：卡片底色本身比页面纸色亮一档（1.25 以上），换了背景图也不会失效。
        val ratioToPaper = luminance(darkGlassFill) / luminance(0xFF0F1513)
        assertTrue("夜间卡片底色 / 纸色 亮度比 ${"%.2f".format(ratioToPaper)} < 1.25", ratioToPaper >= 1.25)
    }

    @Test
    fun `配色方案的 on 与 container 配对全部达标`() {
        val lightPairs = listOf(
            "primary" to (0xFF35626E to 0xFFFFF9EF),
            "primaryContainer" to (0xFFDDE8E2 to 0xFF123A40),
            "secondary" to (0xFFB23A2E to 0xFFFFF6EF),
            "secondaryContainer" to (0xFFF3DCD4 to 0xFF5A1C14),
            "tertiary" to (0xFFB08A4A to 0xFF2A2010),
            "tertiaryContainer" to (0xFFF0E4CB to 0xFF40320F),
            "error" to (0xFFA93226 to 0xFFFFF6EF),
            "errorContainer" to (0xFFF6DDD6 to 0xFF5A1A12),
        )
        lightPairs.forEach { (name, pair) ->
            assertAtLeast(4.5, contrast(pair.first, pair.second), "浅色 $name 的 on 色")
        }

        val darkPairs = listOf(
            "primary" to (0xFF8FBFB2 to 0xFF0E2622),
            "primaryContainer" to (0xFF2A4A46 to 0xFFCFE6DE),
            "secondary" to (0xFFC8A46A to 0xFF2A2010),
            "secondaryContainer" to (0xFF4A3A1E to 0xFFEFDCB8),
            "tertiary" to (0xFFCE8A78 to 0xFF33120C),
            "tertiaryContainer" to (0xFF4A231B to 0xFFF5D5CB),
            "error" to (0xFFE58A7E to 0xFF3A0F0A),
            "errorContainer" to (0xFF4A1C16 to 0xFFF6DCD4),
        )
        darkPairs.forEach { (name, pair) ->
            assertAtLeast(4.5, contrast(pair.first, pair.second), "深色 $name 的 on 色")
        }
    }

    @Test
    fun `朱砂印章上的白字达标`() {
        assertAtLeast(4.5, contrast(0xFFB23A2E, 0xFFFFF6E9), "朱砂/印泥白")
    }
}
