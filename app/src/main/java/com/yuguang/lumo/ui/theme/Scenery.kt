package com.yuguang.lumo.ui.theme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.yuguang.lumo.R

/**
 * 背景场景。两套皮肤各有八幅，图片资源由素材侧生成到 res/drawable-nodpi，
 * 命名固定为 bg_<id>_day / bg_<id>_night，所以枚举里的资源引用必须与文件名一一对应。
 *
 * [skin] 决定这幅景属于哪套皮肤：切皮肤时页面不会「换一幅画」，而是整组景一起换掉，
 * 因此同一路由在两套皮肤下必须各有一幅（见 [SceneryCatalog.forRoute]）。
 *
 * [warm] 是「暖度」提示（0 冷 → 1 暖），供蒙版与印章取色微调时参考，
 * 不做自动调色，避免不同机型上偏色。
 */
enum class Scene(
    val id: String,
    val skin: Skin,
    @StringRes val titleRes: Int,
    @DrawableRes val dayRes: Int,
    @DrawableRes val nightRes: Int,
    val warm: Float,
) {
    // ---- 山水：宣纸上的八幅公版古画 ----
    Cloud(
        "cloud", Skin.Lumos, R.string.scene_cloud,
        R.drawable.bg_cloud_day, R.drawable.bg_cloud_night, 0.30f,
    ),
    Stream(
        "stream", Skin.Lumos, R.string.scene_stream,
        R.drawable.bg_stream_day, R.drawable.bg_stream_night, 0.45f,
    ),
    SnowBamboo(
        "snow_bamboo", Skin.Lumos, R.string.scene_snow_bamboo,
        R.drawable.bg_snow_bamboo_day, R.drawable.bg_snow_bamboo_night, 0.15f,
    ),
    WindBamboo(
        "wind_bamboo", Skin.Lumos, R.string.scene_wind_bamboo,
        R.drawable.bg_wind_bamboo_day, R.drawable.bg_wind_bamboo_night, 0.25f,
    ),
    Falls(
        "falls", Skin.Lumos, R.string.scene_falls,
        R.drawable.bg_falls_day, R.drawable.bg_falls_night, 0.20f,
    ),
    Winter(
        "winter", Skin.Lumos, R.string.scene_winter,
        R.drawable.bg_winter_day, R.drawable.bg_winter_night, 0.10f,
    ),
    Peaks(
        "peaks", Skin.Lumos, R.string.scene_peaks,
        R.drawable.bg_peaks_day, R.drawable.bg_peaks_night, 0.60f,
    ),
    Qinglu(
        "qinglu", Skin.Lumos, R.string.scene_qinglu,
        R.drawable.bg_qinglu_day, R.drawable.bg_qinglu_night, 0.35f,
    ),

    // ---- 弧光学院：八幅程序化生成的魔法场景 ----
    ArcaneCastle(
        "arcane_castle", Skin.Arcane, R.string.scene_arcane_castle,
        R.drawable.bg_arcane_castle_day, R.drawable.bg_arcane_castle_night, 0.35f,
    ),
    ArcaneHall(
        "arcane_hall", Skin.Arcane, R.string.scene_arcane_hall,
        R.drawable.bg_arcane_hall_day, R.drawable.bg_arcane_hall_night, 0.75f,
    ),
    ArcaneForest(
        "arcane_forest", Skin.Arcane, R.string.scene_arcane_forest,
        R.drawable.bg_arcane_forest_day, R.drawable.bg_arcane_forest_night, 0.30f,
    ),
    ArcaneLake(
        "arcane_lake", Skin.Arcane, R.string.scene_arcane_lake,
        R.drawable.bg_arcane_lake_day, R.drawable.bg_arcane_lake_night, 0.20f,
    ),
    ArcaneTower(
        "arcane_tower", Skin.Arcane, R.string.scene_arcane_tower,
        R.drawable.bg_arcane_tower_day, R.drawable.bg_arcane_tower_night, 0.25f,
    ),
    ArcanePotions(
        "arcane_potions", Skin.Arcane, R.string.scene_arcane_potions,
        R.drawable.bg_arcane_potions_day, R.drawable.bg_arcane_potions_night, 0.40f,
    ),
    ArcaneLibrary(
        "arcane_library", Skin.Arcane, R.string.scene_arcane_library,
        R.drawable.bg_arcane_library_day, R.drawable.bg_arcane_library_night, 0.55f,
    ),
    ArcaneNebula(
        "arcane_nebula", Skin.Arcane, R.string.scene_arcane_nebula,
        R.drawable.bg_arcane_nebula_day, R.drawable.bg_arcane_nebula_night, 0.15f,
    ),
}

/**
 * 场景目录：路由映射 + 每日换景。全部是纯函数，无 Android 依赖，可直接跑 JVM 单测。
 *
 * 所有取景函数都带 [skin] 参数（默认山水），因此新增皮肤时不用改动既有调用点语义：
 * 传了皮肤就按那套皮肤取景，没传就是老行为。
 */
object SceneryCatalog {
    /** 全部景（两套皮肤合起来），仅用于反查 id；按皮肤过滤请用 [allFor]。 */
    val all: List<Scene> = Scene.entries.toList()

    /** 某套皮肤的全部景，顺序即画廊顺序。 */
    fun allFor(skin: Skin): List<Scene> = all.filter { it.skin == skin }

    /** 两套皮肤的默认景：山水用云山，弧光用星云。 */
    private val fallbacks: Map<Skin, Scene> = mapOf(
        Skin.Lumos to Scene.Cloud,
        Skin.Arcane to Scene.ArcaneNebula,
    )

    fun fallback(skin: Skin): Scene = fallbacks[skin] ?: allFor(skin).first()

    /**
     * 路由 → 场景。规则按「更具体的在前」排列，避免 settings/stats 被 settings* 抢走。
     * 同一路由在两套皮肤下各有一幅，切换皮肤时整页背景会跟着换。
     */
    fun forRoute(route: String?, skin: Skin = Skin.Default): Scene {
        val r = route?.trim().orEmpty()
        val lumos = skin == Skin.Lumos
        if (r.isEmpty()) return fallback(skin)
        return when {
            r == "chat" || r.startsWith("chat/") || r.startsWith("chat?") ->
                if (lumos) Scene.Stream else Scene.ArcaneCastle
            r == "conversations" ->
                if (lumos) Scene.Cloud else Scene.ArcaneNebula
            r == "translate" ->
                if (lumos) Scene.WindBamboo else Scene.ArcaneForest
            r == "voice" ->
                if (lumos) Scene.Falls else Scene.ArcaneTower
            r == "settings/stats" ->
                if (lumos) Scene.SnowBamboo else Scene.ArcaneLibrary
            r == "arena" ->
                if (lumos) Scene.Peaks else Scene.ArcaneHall
            r == "settings" || r.startsWith("settings/") ->
                if (lumos) Scene.Winter else Scene.ArcanePotions
            else -> fallback(skin)
        }
    }

    /** 每日换景：同一 epochDay 永远得到同一幅，跨天轮换覆盖该皮肤全部 8 幅。 */
    fun daily(epochDay: Long, skin: Skin = Skin.Default): Scene {
        val pool = allFor(skin)
        val size = pool.size
        val index = ((epochDay % size) + size) % size
        return pool[index.toInt()]
    }

    /**
     * 首页四张艺术卡（新对话 / 翻译 / 语音 / 擂台）的底图，顺序固定。
     * 与 [forRoute] 分开是因为首页要的是「四个入口各一幅」，不是「当前页面的背景」。
     */
    fun primary(skin: Skin): List<Scene> = when (skin) {
        Skin.Lumos -> listOf(Scene.Stream, Scene.WindBamboo, Scene.Falls, Scene.Peaks)
        Skin.Arcane -> listOf(
            Scene.ArcaneCastle,
            Scene.ArcaneForest,
            Scene.ArcaneTower,
            Scene.ArcaneHall,
        )
    }

    /**
     * 语音页固定用一幅最有「沉浸感」的景，不跟路由走：
     * 山水用青绿，弧光用天文塔（塔顶看星星那一幕）。
     */
    fun heroFor(route: String, skin: Skin): Scene = when (skin) {
        Skin.Lumos -> if (route == "voice") Scene.Qinglu else forRoute(route, skin)
        Skin.Arcane -> if (route == "voice") Scene.ArcaneTower else forRoute(route, skin)
    }

    /** 会话缩略图：按会话 id 在该皮肤的场景里轮换，保证同一条会话永远同一幅。 */
    fun forConversation(id: Long, skin: Skin): Scene {
        val pool = allFor(skin)
        val index = ((id % pool.size) + pool.size) % pool.size
        return pool[index.toInt()]
    }

    /** 偏好里存的是字符串 id，读回时要能容忍脏数据（跨皮肤残留的 id 也要当没选）。 */
    fun byId(id: String?, skin: Skin? = null): Scene? =
        all.firstOrNull { it.id == id && (skin == null || it.skin == skin) }
}
