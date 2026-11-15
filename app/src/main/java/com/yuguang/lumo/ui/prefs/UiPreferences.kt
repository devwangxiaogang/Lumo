package com.yuguang.lumo.ui.prefs

import android.content.Context
import androidx.annotation.StringRes
import com.yuguang.lumo.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 主题模式。 */
enum class ThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}

/**
 * 界面相关的本地偏好：主题、首次引导、山水场景与动效。
 *
 * 全部是 `StateFlow`：改一次值，所有在读它的页面同时刷新（不需要回来问 SharedPreferences）。
 */
@Singleton
class UiPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name).orEmpty()) }
            .getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _onboarded = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    /** 指定常驻场景（场景 id）；null 表示「随页面」——每页按路由自己挑一幅。 */
    private val _scene = MutableStateFlow(prefs.getString(KEY_SCENE, null))
    val scene: StateFlow<String?> = _scene.asStateFlow()

    /** 每日换景：开着时「随页面」的页面按日期轮换，同一天不会变。 */
    private val _dailyScene = MutableStateFlow(prefs.getBoolean(KEY_DAILY_SCENE, true))
    val dailyScene: StateFlow<Boolean> = _dailyScene.asStateFlow()

    /** 行云流水：关闭后背景静止、转场归零，更省电。 */
    private val _motion = MutableStateFlow(prefs.getBoolean(KEY_MOTION, true))
    val motion: StateFlow<Boolean> = _motion.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun markOnboarded() {
        _onboarded.value = true
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    /** 传 null 写回「随页面」。 */
    fun setScene(id: String?) {
        _scene.value = id
        prefs.edit().apply {
            if (id == null) remove(KEY_SCENE) else putString(KEY_SCENE, id)
        }.apply()
    }

    fun setDailyScene(enabled: Boolean) {
        _dailyScene.value = enabled
        prefs.edit().putBoolean(KEY_DAILY_SCENE, enabled).apply()
    }

    fun setMotion(enabled: Boolean) {
        _motion.value = enabled
        prefs.edit().putBoolean(KEY_MOTION, enabled).apply()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_SCENE = "scene_id"
        const val KEY_DAILY_SCENE = "scene_daily"
        const val KEY_MOTION = "motion"
    }
}
