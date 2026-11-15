package com.yuguang.lumo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计令牌：宣纸 / 石青 / 朱砂 / 鎏金。
 *
 * 字段名是外部契约（页面直接引用），只允许换值、不允许改名。
 * 配对关系：Light* 走浅色纸面，Dark* 走夜里墨绿石面；on* 色与容器色均已按 WCAG AA（≥4.5）校准。
 */
object AiColors {
    /** 石青：主色。取自青绿山水里的矿物蓝绿。 */
    val Indigo = Color(0xFF35626E)
    val IndigoLight = Color(0xFF4E8791)

    /** 淡石青：主色容器。 */
    val IndigoSoft = Color(0xFFDDE8E2)

    /** 朱砂：警示 / 印章 / 强调。 */
    val Coral = Color(0xFFB23A2E)
    val Success = Color(0xFF4E8C6A)
    val Warning = Color(0xFFB08A4A)

    // 浅色：宣纸
    val LightBackground = Color(0xFFF2EADA)
    val LightSurface = Color(0xFFFAF5EA)
    val LightSurfaceVariant = Color(0xFFEDE6D6)
    val LightInk = Color(0xFF232A26)
    val LightSub = Color(0xFF4C574F)
    val LightFaint = Color(0xFF7C8A81)
    val LightOutline = Color(0xFFD9D2C0)

    // 深色：夜
    val DarkBackground = Color(0xFF0F1513)
    val DarkSurface = Color(0xFF16201C)
    val DarkSurfaceVariant = Color(0xFF1E2B26)
    val DarkInk = Color(0xFFEFE7D6)
    val DarkSub = Color(0xFFA9B5AC)
    val DarkOutline = Color(0xFF2B3A34)
}

private val LightColors = lightColorScheme(
    primary = AiColors.Indigo,
    onPrimary = Color(0xFFFFF9EF),
    primaryContainer = AiColors.IndigoSoft,
    onPrimaryContainer = Color(0xFF123A40),
    secondary = AiColors.Coral,
    onSecondary = Color(0xFFFFF6EF),
    secondaryContainer = Color(0xFFF3DCD4),
    onSecondaryContainer = Color(0xFF5A1C14),
    tertiary = AiColors.Warning,
    onTertiary = Color(0xFF2A2010),
    tertiaryContainer = Color(0xFFF0E4CB),
    onTertiaryContainer = Color(0xFF40320F),
    background = AiColors.LightBackground,
    onBackground = AiColors.LightInk,
    surface = AiColors.LightSurface,
    onSurface = AiColors.LightInk,
    surfaceVariant = AiColors.LightSurfaceVariant,
    onSurfaceVariant = AiColors.LightSub,
    outline = AiColors.LightOutline,
    outlineVariant = Color(0xFFE7E0CE),
    surfaceDim = Color(0xFFE6DECC),
    surfaceBright = Color(0xFFFFFCF4),
    surfaceContainerLowest = Color(0xFFFFFCF5),
    surfaceContainerLow = Color(0xFFF8F2E6),
    surfaceContainer = Color(0xFFF4EDDF),
    surfaceContainerHigh = Color(0xFFEFE7D7),
    surfaceContainerHighest = Color(0xFFE9E1CF),
    inverseSurface = Color(0xFF232A26),
    inverseOnSurface = Color(0xFFF2EADA),
    inversePrimary = Color(0xFF7FA9B0),
    error = Color(0xFFA93226),
    onError = Color(0xFFFFF6EF),
    errorContainer = Color(0xFFF6DDD6),
    onErrorContainer = Color(0xFF5A1A12),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FA9B0),
    onPrimary = Color(0xFF05201F),
    primaryContainer = Color(0xFF284A4E),
    onPrimaryContainer = Color(0xFFC6E0E3),
    secondary = Color(0xFFC8A46A),
    onSecondary = Color(0xFF2A2010),
    secondaryContainer = Color(0xFF4A3A1E),
    onSecondaryContainer = Color(0xFFEFDCB8),
    tertiary = Color(0xFFCE8A78),
    onTertiary = Color(0xFF33120C),
    tertiaryContainer = Color(0xFF4A231B),
    onTertiaryContainer = Color(0xFFF5D5CB),
    background = AiColors.DarkBackground,
    onBackground = AiColors.DarkInk,
    surface = AiColors.DarkSurface,
    onSurface = AiColors.DarkInk,
    surfaceVariant = AiColors.DarkSurfaceVariant,
    onSurfaceVariant = AiColors.DarkSub,
    outline = AiColors.DarkOutline,
    outlineVariant = Color(0xFF23302B),
    surfaceDim = Color(0xFF0B100E),
    surfaceBright = Color(0xFF24332C),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF121A17),
    surfaceContainer = Color(0xFF18231E),
    surfaceContainerHigh = Color(0xFF1D2A24),
    surfaceContainerHighest = Color(0xFF233029),
    inverseSurface = Color(0xFFEFE7D6),
    inverseOnSurface = Color(0xFF16201C),
    inversePrimary = Color(0xFF35626E),
    error = Color(0xFFE58A7E),
    onError = Color(0xFF3A0F0A),
    errorContainer = Color(0xFF4A1C16),
    onErrorContainer = Color(0xFFF6DCD4),
)

/** 弧光学院 · 浅色：月长石靛做主色，古金做次级，余烬朱做警示。 */
private val ArcaneLightColors = lightColorScheme(
    primary = Color(0xFF4B4E7A),
    onPrimary = Color(0xFFFFFBF2),
    primaryContainer = Color(0xFFDCDCF0),
    onPrimaryContainer = Color(0xFF1B1E3A),
    secondary = Color(0xFF8A6A1E),
    onSecondary = Color(0xFFFFFBF0),
    secondaryContainer = Color(0xFFF0E2BF),
    onSecondaryContainer = Color(0xFF3A2C08),
    tertiary = Color(0xFFA8402C),
    onTertiary = Color(0xFFFFF6EF),
    tertiaryContainer = Color(0xFFF3D9D0),
    onTertiaryContainer = Color(0xFF521A10),
    background = Color(0xFFEDE4D0),
    onBackground = Color(0xFF1E2230),
    surface = Color(0xFFF6EFDD),
    onSurface = Color(0xFF1E2230),
    surfaceVariant = Color(0xFFE4DAC3),
    onSurfaceVariant = Color(0xFF4C5060),
    outline = Color(0xFFD6C9AE),
    outlineVariant = Color(0xFFE6DCC6),
    surfaceDim = Color(0xFFE0D6BF),
    surfaceBright = Color(0xFFFDF8EA),
    surfaceContainerLowest = Color(0xFFFFFBEF),
    surfaceContainerLow = Color(0xFFF4EDDC),
    surfaceContainer = Color(0xFFEFE7D4),
    surfaceContainerHigh = Color(0xFFE9E0CB),
    surfaceContainerHighest = Color(0xFFE2D8C1),
    inverseSurface = Color(0xFF1E2230),
    inverseOnSurface = Color(0xFFEDE4D0),
    inversePrimary = Color(0xFF9AA6DE),
    error = Color(0xFF9E2F22),
    onError = Color(0xFFFFF6EF),
    errorContainer = Color(0xFFF3D9D0),
    onErrorContainer = Color(0xFF521A10),
)

/** 弧光学院 · 深色：午夜底 + 月长石高光 + 烛火金。 */
private val ArcaneDarkColors = darkColorScheme(
    primary = Color(0xFF9AA6DE),
    onPrimary = Color(0xFF141A2E),
    primaryContainer = Color(0xFF2A3050),
    onPrimaryContainer = Color(0xFFDCDCF0),
    secondary = Color(0xFFD6AE5C),
    onSecondary = Color(0xFF2A2008),
    secondaryContainer = Color(0xFF413211),
    onSecondaryContainer = Color(0xFFF0E2BF),
    tertiary = Color(0xFFD2643F),
    onTertiary = Color(0xFF2E1009),
    tertiaryContainer = Color(0xFF4A2216),
    onTertiaryContainer = Color(0xFFF3D9D0),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFEFE9D8),
    surface = Color(0xFF141A2E),
    onSurface = Color(0xFFEFE9D8),
    surfaceVariant = Color(0xFF1E263C),
    onSurfaceVariant = Color(0xFFB0B3CC),
    outline = Color(0xFF3A4468),
    outlineVariant = Color(0xFF232B44),
    surfaceDim = Color(0xFF080C18),
    surfaceBright = Color(0xFF232B44),
    surfaceContainerLowest = Color(0xFF070B14),
    surfaceContainerLow = Color(0xFF10152A),
    surfaceContainer = Color(0xFF151B31),
    surfaceContainerHigh = Color(0xFF1A2138),
    surfaceContainerHighest = Color(0xFF202841),
    inverseSurface = Color(0xFFEFE9D8),
    inverseOnSurface = Color(0xFF141A2E),
    inversePrimary = Color(0xFF4B4E7A),
    error = Color(0xFFE58A7E),
    onError = Color(0xFF3A0F0A),
    errorContainer = Color(0xFF4A1C16),
    onErrorContainer = Color(0xFFF6DCD4),
)

/**
 * 山水层专用色板：Material 色板表达不了「宣纸 / 磨砂玻璃 / 蒙版」这类材质色，单独一份。
 *
 * 取色说明（对比度见 ContrastTest）：
 * - paperGlass 是玻璃卡片的不透明底，实际绘制时会按 level 叠 0.62–0.94 的透明度，
 *   所以最坏情形是 0.62 叠到场景最亮/最暗色调上，仍保证正文墨色 ≥ 4.5。
 * - scrim* 自带 alpha，直接交给 Canvas 画竖直渐变，不要再改透明度。
 */
data class LumoPalette(
    /** 宣纸底色：整页兜底色，图片加载前的占位。 */
    val paper: Color,
    /** 玻璃卡片底色（绘制时按 level 叠透明度）。 */
    val paperGlass: Color,
    /** 玻璃亮边（1dp 描边）。 */
    val glassBorder: Color,
    /** 朱砂：印章、错误、强调。 */
    val cinnabar: Color,
    /** 鎏金：次级强调、分隔装饰。 */
    val gold: Color,
    /** 顶部蒙版（自带 alpha）：状态栏 + TopBar 区，重一点压住图标。 */
    val scrimTop: Color,
    /** 中部蒙版（自带 alpha）：正文区的「轻全局」薄纱，不能把山吃掉。 */
    val scrimMid: Color,
    /** 底部蒙版（自带 alpha）：输入条 / 底部导航区。 */
    val scrimBottom: Color,
    /** 印章文字色。 */
    val sealInk: Color,
    /** 光晕：弧光皮肤用它画星辉 / 极光；山水皮肤用它画顶栏那一团暖光。 */
    val glow: Color,
    /** 星火 / 符文亮色：只有弧光皮肤会用，山水皮肤保留同字段以免调用点分叉。 */
    val spark: Color,
)

private val LightLumoPalette = LumoPalette(
    paper = Color(0xFFF2EADA),
    paperGlass = Color(0xFFFAF5EA),
    glassBorder = Color(0xFFFFFDF7),
    cinnabar = Color(0xFFB23A2E),
    gold = Color(0xFFB08A4A),
    // 轻全局 + 局部加重：顶部 40% 压住状态栏图标，中部 8% 只加一层薄纱（山水要看得见），底部 50% 托住输入条。
    // 真机截图（docs/smoke/ui-day-*.png）证明上一版 56/19/60 把山水压死了，这里整体减淡约 30%。
    scrimTop = Color(0x66F2EADA),
    scrimMid = Color(0x14F2EADA),
    scrimBottom = Color(0x80F2EADA),
    sealInk = Color(0xFFFFF6E9),
    glow = Color(0xFFFFF1C9),
    spark = Color(0xFFE8C97A),
)

private val DarkLumoPalette = LumoPalette(
    paper = Color(0xFF0F1513),
    // 夜间纸面在纸色基础上提亮约 6%（#16201C → #17221E）：
    // r2 真机实测夜间卡片灰度 54.1 / 背景 39.5，卡片几乎融进夜山，靠「更亮的纸」比靠加厚遮罩更不伤风景。
    paperGlass = Color(0xFF17221E),
    glassBorder = Color(0xFF3C4F47),
    cinnabar = Color(0xFFC8564A),
    gold = Color(0xFFC8A46A),
    // 轻全局 + 局部加重：顶部 40%，中部 10%（夜景本身偏暗，薄纱略重于浅色的 8%），底部 52%。
    scrimTop = Color(0x660F1513),
    scrimMid = Color(0x1A0F1513),
    scrimBottom = Color(0x850F1513),
    sealInk = Color(0xFFF6EEE0),
    glow = Color(0xFFA9C4FF),
    spark = Color(0xFFF0D793),
)

/** 当前山水色板；未包在 [AiTheme] 里时回落到浅色纸面。 */
/**
 * 弧光学院 · 浅色「晨雾羊皮纸」：白天在院校里，纸色偏冷、金是旧金。
 *
 * 与山水皮肤同一套字段语义：cinnabar 槽在这里是「余烬朱」（烛火 / 警示），
 * gold 槽是装饰金；两者都不参与正文，正文一律走 onSurface。
 */
private val LightArcanePalette = LumoPalette(
    paper = Color(0xFFEDE4D0),
    paperGlass = Color(0xFFF8F2E3),
    glassBorder = Color(0xFFFFF8E7),
    cinnabar = Color(0xFFA8402C),
    gold = Color(0xFFB8892F),
    scrimTop = Color(0x66EDE4D0),
    scrimMid = Color(0x14EDE4D0),
    scrimBottom = Color(0x80EDE4D0),
    sealInk = Color(0xFFFFF6E0),
    glow = Color(0xFFFFE9AE),
    spark = Color(0xFFB0801F),
)

/** 弧光学院 · 深色「午夜星穹」：夜色压得更深，靠金与月白提亮，不靠加白雾。 */
private val DarkArcanePalette = LumoPalette(
    paper = Color(0xFF0B1020),
    paperGlass = Color(0xFF1A2138),
    glassBorder = Color(0xFF3A4468),
    cinnabar = Color(0xFFD2643F),
    gold = Color(0xFFD6AE5C),
    scrimTop = Color(0x660B1020),
    scrimMid = Color(0x1A0B1020),
    scrimBottom = Color(0x850B1020),
    sealInk = Color(0xFFF6F0DE),
    glow = Color(0xFFA9C4FF),
    spark = Color(0xFFF0D793),
)

val LocalLumoPalette = staticCompositionLocalOf { LightLumoPalette }

/**
 * 字体：标题族走衬线（宋刻感），正文保持系统无衬线保证小字可读。
 * 标题 letterSpacing 1–2sp，是「题字」与「正文」的呼吸差。
 */
private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = 1.6.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = 1.4.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 1.sp,
    ),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)

/** 散落在页面里的特殊字形：诗句与印章。 */
object LumoType {
    /** 诗句：大字距，行高刻意放大，像宣纸上的题跋。 */
    val poem = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 18.sp,
        lineHeight = 32.sp,
        letterSpacing = 4.sp,
    )

    /** 印章 / 小字题款。 */
    val seal = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 2.sp,
    )
}

/** 圆角更松、更接近纸片与石面。 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * 主题入口：明暗 + 皮肤两层。
 *
 * 皮肤决定的是「材质与画风」——色板、背景场景集合、装饰记号、动效风格；
 * 明暗决定同一套皮肤下的日夜两面。两者正交，所以 2×2 一共四套色板。
 */
@Composable
fun AiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    skin: Skin = Skin.Default,
    content: @Composable () -> Unit,
) {
    val palette = when (skin) {
        Skin.Lumos -> if (darkTheme) DarkLumoPalette else LightLumoPalette
        Skin.Arcane -> if (darkTheme) DarkArcanePalette else LightArcanePalette
    }
    val scheme = when (skin) {
        Skin.Lumos -> if (darkTheme) DarkColors else LightColors
        Skin.Arcane -> if (darkTheme) ArcaneDarkColors else ArcaneLightColors
    }
    CompositionLocalProvider(
        LocalLumoPalette provides palette,
        LocalSkin provides skin,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
