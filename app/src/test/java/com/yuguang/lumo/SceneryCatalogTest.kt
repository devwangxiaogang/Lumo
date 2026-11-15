package com.yuguang.lumo

import com.yuguang.lumo.ui.theme.Scene
import com.yuguang.lumo.ui.theme.SceneryCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 山水场景目录：8 幅唯一、路由映射、每日换景的确定性。
 *
 * 这些规则一旦被改坏，用户会看到「设置页跑出溪山」「同一天每刷新一次换一幅」，
 * 所以把映射表逐条钉在测试里。
 */
class SceneryCatalogTest {

    @Test
    fun `八个场景的 id 与顺序固定`() {
        assertEquals(8, SceneryCatalog.all.size)
        assertEquals(
            listOf("cloud", "stream", "snow_bamboo", "wind_bamboo", "falls", "winter", "peaks", "qinglu"),
            SceneryCatalog.all.map { it.id },
        )
        assertEquals(8, SceneryCatalog.all.map { it.id }.toSet().size)
    }

    @Test
    fun `每个场景都有日夜两张不同资源`() {
        val days = SceneryCatalog.all.map { it.dayRes }
        val nights = SceneryCatalog.all.map { it.nightRes }
        assertEquals("白天图不应重复", 8, days.toSet().size)
        assertEquals("夜晚图不应重复", 8, nights.toSet().size)
        SceneryCatalog.all.forEach { scene ->
            assertTrue("${scene.id} 的白天资源为空", scene.dayRes != 0)
            assertTrue("${scene.id} 的夜晚资源为空", scene.nightRes != 0)
            assertNotEquals("${scene.id} 日夜用同一张图", scene.dayRes, scene.nightRes)
            assertTrue("${scene.id} 缺标题", scene.titleRes != 0)
            assertTrue("${scene.id} 的暖度越界", scene.warm in 0f..1f)
        }
    }

    @Test
    fun `路由映射逐条正确`() {
        val expected = mapOf(
            "conversations" to Scene.Cloud,
            "chat/{id}" to Scene.Stream,
            "chat/42" to Scene.Stream,
            "translate" to Scene.WindBamboo,
            "voice" to Scene.Falls,
            "settings" to Scene.Winter,
            "settings/appearance" to Scene.Winter,
            "settings/provider/deepseek" to Scene.Winter,
            "arena" to Scene.Peaks,
            // 更具体的 settings/stats 必须赢过 settings/*，否则用量页会画成寒林。
            "settings/stats" to Scene.SnowBamboo,
        )
        expected.forEach { (route, scene) ->
            assertEquals("路由 $route", scene, SceneryCatalog.forRoute(route))
        }
    }

    @Test
    fun `未知道路与空路由回落到云山`() {
        assertEquals(Scene.Cloud, SceneryCatalog.forRoute(null))
        assertEquals(Scene.Cloud, SceneryCatalog.forRoute(""))
        assertEquals(Scene.Cloud, SceneryCatalog.forRoute("   "))
        assertEquals(Scene.Cloud, SceneryCatalog.forRoute("something/else"))
    }

    @Test
    fun `每日换景对同一天确定`() {
        listOf(0L, 1L, 19_000L, 20_000L, 999_999L).forEach { day ->
            assertEquals("第 $day 天应稳定", SceneryCatalog.daily(day), SceneryCatalog.daily(day))
            assertNotEquals("相邻两天不该是同一幅", SceneryCatalog.daily(day), SceneryCatalog.daily(day + 1))
        }
    }

    @Test
    fun `连续八天覆盖全部场景`() {
        val week = (0L until 8L).map { SceneryCatalog.daily(it) }
        assertEquals(8, week.toSet().size)
        assertEquals(0L, SceneryCatalog.daily(0L).let { Scene.entries.indexOf(it).toLong() })
    }

    @Test
    fun `负 epochDay 也不越界`() {
        assertEquals(Scene.Qinglu, SceneryCatalog.daily(-1L))
        assertEquals(Scene.Cloud, SceneryCatalog.daily(-8L))
        assertTrue(SceneryCatalog.all.contains(SceneryCatalog.daily(-123_456L)))
    }

    @Test
    fun `byId 容忍脏数据`() {
        assertEquals(Scene.WindBamboo, SceneryCatalog.byId("wind_bamboo"))
        assertEquals(Scene.Cloud, SceneryCatalog.byId("cloud"))
        assertNull(SceneryCatalog.byId("nope"))
        assertNull(SceneryCatalog.byId(null))
        assertNull(SceneryCatalog.byId(""))
    }
}
