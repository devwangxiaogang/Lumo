package com.yuguang.lumo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuguang.lumo.data.IncomingShareBus
import com.yuguang.lumo.ui.AiApp
import com.yuguang.lumo.ui.onboarding.OnboardingScreen
import com.yuguang.lumo.ui.prefs.ThemeMode
import com.yuguang.lumo.ui.prefs.UiPreferences
import com.yuguang.lumo.ui.theme.AiTheme
import com.yuguang.lumo.ui.theme.IconSwitcher
import com.yuguang.lumo.ui.theme.LocalMotionEnabled
import com.yuguang.lumo.ui.theme.rememberMotionEnabled
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var uiPreferences: UiPreferences

    @Inject
    lateinit var shareBus: IncomingShareBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 冷启动的分享/文字处理也要喂进同一条通道。
        shareBus.accept(intent)
        // 内容延伸到系统栏，输入框再单独避让键盘和导航栏。
        enableEdgeToEdge()
        // 桌面图标跟着皮肤走。这里只是「自愈」：万一上次切换把两个入口都关掉了，
        // 用户从桌面点不进来，只能靠别处打开应用时补回默认皮肤对应的入口。
        IconSwitcher.ensure(this, uiPreferences.skin.value)
        setContent {
            val mode by uiPreferences.themeMode.collectAsStateWithLifecycle()
            val skin by uiPreferences.skin.collectAsStateWithLifecycle()
            val onboarded by uiPreferences.onboarded.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // 状态栏 / 导航栏图标明暗跟着主题走：夜色纸面下用浅色图标，宣纸下用深色图标。
            // 不新增依赖（WindowCompat 属于已有的 core），也不改系统栏的透明/边到边行为。
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            // 「行云流水」开关只在根部接线一次：云雾、推镜、转场、逐条入境都读这个 Local。
            val motionEnabled = rememberMotionEnabled(uiPreferences)
            AiTheme(darkTheme = dark, skin = skin) {
                CompositionLocalProvider(LocalMotionEnabled provides motionEnabled) {
                    if (onboarded) AiApp() else OnboardingScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shareBus.accept(intent)
    }
}
