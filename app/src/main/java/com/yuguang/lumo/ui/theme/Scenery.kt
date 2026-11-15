package com.yuguang.lumo.ui.theme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.yuguang.lumo.R

/**
 * 八幅山水。图片资源由素材侧生成到 res/drawable-nodpi，命名固定为 bg_<id>_day / bg_<id>_night，
 * 所以枚举里的资源引用必须与文件名一一对应。
 *
 * [warm] 是「暖度」提示（0 冷青 → 1 暖褐），供蒙版与印章取色微调时参考，
 * 不做自动调色，避免不同机型上偏色。
 */
enum class Scene(
    val id: String,
    @StringRes val titleRes: Int,
    @DrawableRes val dayRes: Int,
    @DrawableRes val nightRes: Int,
    val warm: Float,
) {
    Cloud("cloud", R.string.scene_cloud, R.drawable.bg_cloud_day, R.drawable.bg_cloud_night, 0.30f),
    Stream("stream", R.string.scene_stream, R.drawable.bg_stream_day, R.drawable.bg_stream_night, 0.45f),
    SnowBamboo(
        "snow_bamboo",
        R.string.scene_snow_bamboo,
        R.drawable.bg_snow_bamboo_day,
        R.drawable.bg_snow_bamboo_night,
        0.15f,
    ),
    WindBamboo(
        "wind_bamboo",
        R.string.scene_wind_bamboo,
        R.drawable.bg_wind_bamboo_day,
        R.drawable.bg_wind_bamboo_night,
        0.25f,
    ),
    Falls("falls", R.string.scene_falls, R.drawable.bg_falls_day, R.drawable.bg_falls_night, 0.20f),
    Winter("winter", R.string.scene_winter, R.drawable.bg_winter_day, R.drawable.bg_winter_night, 0.10f),
    Peaks("peaks", R.string.scene_peaks, R.drawable.bg_peaks_day, R.drawable.bg_peaks_night, 0.60f),
    Qinglu("qinglu", R.string.scene_qinglu, R.drawable.bg_qinglu_day, R.drawable.bg_qinglu_night, 0.35f),
}

/**
 * 场景目录：路由映射 + 每日换景。
 *
 * 全部是纯函数，无 Android 依赖，可直接跑 JVM 单测（SceneryCatalogTest）。
 */
object SceneryCatalog {
    val all: List<Scene> = Scene.entries.toList()

    private val fallback = Scene.Cloud

    /**
     * 路由 → 场景。规则按「更具体的在前」排列，避免 settings/stats 被 settings* 抢走。
     */
    fun forRoute(route: String?): Scene {
        val r = route?.trim().orEmpty()
        if (r.isEmpty()) return fallback
        return when {
            r == "chat" || r.startsWith("chat/") || r.startsWith("chat?") -> Scene.Stream
            r == "conversations" -> Scene.Cloud
            r == "translate" -> Scene.WindBamboo
            r == "voice" -> Scene.Falls
            r == "settings/stats" -> Scene.SnowBamboo
            r == "arena" -> Scene.Peaks
            r == "settings" || r.startsWith("settings/") -> Scene.Winter
            else -> fallback
        }
    }

    /** 每日换景：同一 epochDay 永远得到同一幅，跨天轮换覆盖全部 8 幅。 */
    fun daily(epochDay: Long): Scene {
        val size = all.size
        val index = ((epochDay % size) + size) % size
        return all[index.toInt()]
    }

    /** 偏好里存的是字符串 id，读回时要能容忍脏数据。 */
    fun byId(id: String?): Scene? = all.firstOrNull { it.id == id }
}
