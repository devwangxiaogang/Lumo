package com.yuguang.lumo.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.yuguang.lumo.R

/**
 * 皮肤：整套「配色 + 背景画风 + 装饰记号 + 动效风格」的打包。
 *
 * 两套皮肤共享同一份页面结构与组件，只有 [LumoPalette]、[Scene] 集合、
 * 装饰（印章 / 符文）与动效曲线不同 —— 所以切换皮肤不会让任何页面走另一条代码分支。
 *
 * [id] 是写进 SharedPreferences 的稳定值，不要为了好看去改它。
 */
enum class Skin(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val blurbRes: Int,
) {
    /** 山水：宣纸 + 石青 + 朱砂，八个页面各挂一幅公版古画。 */
    Lumos("lumo", R.string.skin_lumo, R.string.skin_lumo_blurb),

    /** 弧光学院：午夜星穹 + 烛火金 + 符文，意象取自魔法院校（原创，非官方素材）。 */
    Arcane("arcane", R.string.skin_arcane, R.string.skin_arcane_blurb),
    ;

    companion object {
        val Default: Skin = Lumos

        /** 偏好里存的是字符串 id，读回时要能容忍脏数据与老版本缺键。 */
        fun byId(id: String?): Skin? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 当前皮肤；未包在 [AiTheme] 里时回落山水。
 *
 * 需要画「皮肤相关」的东西时读它，不要层层传参 —— 与 [LocalLumoPalette] 同一套思路。
 */
val LocalSkin = staticCompositionLocalOf { Skin.Default }
