package com.yuguang.lumo.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 桌面图标切换。
 *
 * 做法是 Android 官方那条路：`MainActivity` 本身不挂 LAUNCHER，
 * 由两个 `activity-alias`（`.LauncherLumos` / `.LauncherArcane`）各带一个图标，
 * 切换时「启用目标 + 禁用另一个」。三个要点：
 *
 * 1. **DONT_KILL_APP**：不加这个标志系统会杀掉进程，用户正看到一半的对话就没了。
 * 2. **先启后禁**：反过来会出现「一个瞬间两个入口都没有」的窗口，某些桌面会把应用从
 *    最近使用里抹掉。哪怕中间崩了，也只是两个入口同时在，比消失好。
 * 3. **ensure**：进程被杀在「启用了新的但还没禁用旧的」之间时，两个入口都在；
 *    被杀在「禁用了旧的但没启用新的」之前时，桌面就空了 —— 所以每次启动都自检一遍，
 *    把当前皮肤对应的入口补回来。
 */
object IconSwitcher {
    private const val TAG = "IconSwitcher"
    private const val PKG = "com.yuguang.lumo"
    private const val LUMOS = "$PKG.LauncherLumos"
    private const val ARCANE = "$PKG.LauncherArcane"

    /**
     * 立刻把桌面图标切到 [skin] 对应入口。
     *
     * 部分国产桌面会缓存图标到下一次回到桌面，所以调用方仍应提示用户「滑一下桌面」。
     */
    fun apply(context: Context, skin: Skin) {
        val wanted = aliasFor(skin)
        val other = if (wanted == LUMOS) ARCANE else LUMOS
        val pm = context.packageManager
        setEnabled(pm, wanted, true)
        setEnabled(pm, other, false)
    }

    /**
     * 保证当前皮肤对应的入口是启用的，多于的入口关掉。
     *
     * 只在两者状态都不对时才动系统，避免每次启动都写一次 PackageManager。
     */
    fun ensure(context: Context, skin: Skin) {
        val wanted = aliasFor(skin)
        val other = if (wanted == LUMOS) ARCANE else LUMOS
        val pm = context.packageManager
        val wantedOn = isEnabled(pm, wanted)
        val otherOn = isEnabled(pm, other)
        if (wantedOn && !otherOn) return
        apply(context, skin)
    }

    /** 挂起版本：给 Compose 侧调用，写 PackageManager 在 IO 线程。 */
    suspend fun applyAsync(context: Context, skin: Skin) = withContext(Dispatchers.IO) {
        apply(context.applicationContext, skin)
    }

    private fun aliasFor(skin: Skin): String = when (skin) {
        Skin.Lumos -> LUMOS
        Skin.Arcane -> ARCANE
    }

    private fun isEnabled(pm: PackageManager, alias: String): Boolean = runCatching {
        val name = ComponentName(PKG, alias)
        val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getComponentEnabledSetting(name)
        } else {
            @Suppress("DEPRECATION")
            pm.getComponentEnabledSetting(name)
        }
        state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            // DEFAULT 表示没被显式改过 —— 只有 manifest 里 enabled=true 的那个才当启用。
            (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && alias == LUMOS)
    }.onFailure { Log.w(TAG, "读图标入口状态失败: $alias", it) }.getOrDefault(false)

    private fun setEnabled(pm: PackageManager, alias: String, enabled: Boolean) {
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(PKG, alias),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                // 不杀进程：图标切换是「桌面上的事」，没必要把用户正在看的页面打断。
                PackageManager.DONT_KILL_APP,
            )
        }.onFailure { Log.w(TAG, "切换图标入口失败: $alias -> $enabled", it) }
    }
}
