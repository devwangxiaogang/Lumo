package com.yuguang.lumo

import com.yuguang.lumo.ui.theme.Scene
import com.yuguang.lumo.ui.theme.SceneryCatalog
import com.yuguang.lumo.ui.theme.Skin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 皮肤：枚举契约 + 两套皮肤的取景隔离。
 *
 * 这里守的是「换皮肤不能串味」——弧光皮肤不该取到山水那八幅，
 * 手选的场景 id 也不该跨皮肤生效（否则会看到山水底图配金色菱徽）。
 */
class SkinTest {

    @Test
    fun `皮肤 id 唯一且稳定`() {
        val ids = Skin.entries.map { it.id }
        assertEquals(listOf("lumo", "arcane"), ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `每个皮肤都有名字与说明`() {
        Skin.entries.forEach { skin ->
            assertTrue("${skin.id} 缺名字", skin.labelRes != 0)
            assertTrue("${skin.id} 缺说明", skin.blurbRes != 0)
        }
    }

    @Test
    fun `默认皮肤是山水`() {
        assertSame(Skin.Lumos, Skin.Default)
    }

    @Test
    fun `byId 容忍空值与脏数据`() {
        assertNull(Skin.byId(null))
        assertNull(Skin.byId(""))
        assertNull(Skin.byId("harry-potter"))
        assertSame(Skin.Arcane, Skin.byId("arcane"))
        assertSame(Skin.Lumos, Skin.byId("lumo"))
    }

    @Test
    fun `两套皮肤各八幅景 且互不重叠`() {
        val lumos = SceneryCatalog.allFor(Skin.Lumos)
        val arcane = SceneryCatalog.allFor(Skin.Arcane)
        assertEquals(8, lumos.size)
        assertEquals(8, arcane.size)
        assertTrue("两套皮肤混用了同一幅景", lumos.intersect(arcane.toSet()).isEmpty())
        assertEquals(16, SceneryCatalog.all.size)
        assertEquals(16, SceneryCatalog.all.map { it.id }.toSet().size)
    }

    @Test
    fun `弧光八幅都有日夜两张 且暖度合法`() {
        val arcane = SceneryCatalog.allFor(Skin.Arcane)
        val days = arcane.map { it.dayRes }
        val nights = arcane.map { it.nightRes }
        assertEquals("白天图不应重复", 8, days.toSet().size)
        assertEquals("夜晚图不应重复", 8, nights.toSet().size)
        arcane.forEach { scene ->
            assertTrue("${scene.id} 的白天资源为空", scene.dayRes != 0)
            assertTrue("${scene.id} 的夜晚资源为空", scene.nightRes != 0)
            assertNotEquals("${scene.id} 日夜用同一张图", scene.dayRes, scene.nightRes)
            assertTrue("${scene.id} 归属皮肤错", scene.skin == Skin.Arcane)
            assertTrue("${scene.id} 的暖度越界", scene.warm in 0f..1f)
        }
    }

    @Test
    fun `同一路由在两套皮肤下取到的景不同`() {
        // 这是「换皮肤整页背景跟着换」的最小保证：任一条常见路由都不该两边一样。
        listOf("conversations", "chat", "translate", "voice", "settings", "settings/stats", "arena")
            .forEach { route ->
                val lumos = SceneryCatalog.forRoute(route, Skin.Lumos)
                val arcane = SceneryCatalog.forRoute(route, Skin.Arcane)
                assertNotEquals("$route 在两套皮肤下取了同一幅景", lumos, arcane)
                assertEquals(Skin.Lumos, lumos.skin)
                assertEquals(Skin.Arcane, arcane.skin)
            }
    }

    @Test
    fun `每日换景只在所属皮肤内轮换`() {
        for (day in 0L..15L) {
            assertEquals(Skin.Lumos, SceneryCatalog.daily(day, Skin.Lumos).skin)
            assertEquals(Skin.Arcane, SceneryCatalog.daily(day, Skin.Arcane).skin)
        }
        // 同一 epochDay 在两套皮肤下各有一幅，且各自稳定。
        assertEquals(
            SceneryCatalog.daily(100L, Skin.Arcane),
            SceneryCatalog.daily(100L, Skin.Arcane),
        )
    }

    @Test
    fun `首页四张艺术卡按皮肤各取四幅且互不相同`() {
        Skin.entries.forEach { skin ->
            val cards = SceneryCatalog.primary(skin)
            assertEquals(4, cards.size)
            cards.forEach { assertEquals(skin, it.skin) }
            assertEquals("${skin.id} 的四张卡有重复", 4, cards.toSet().size)
        }
    }

    @Test
    fun `语音页固定景两套皮肤各有其选`() {
        assertEquals(Scene.Qinglu, SceneryCatalog.heroFor("voice", Skin.Lumos))
        assertEquals(Scene.ArcaneTower, SceneryCatalog.heroFor("voice", Skin.Arcane))
    }

    @Test
    fun `byId 可以按皮肤过滤 跨皮肤 id 当没选`() {
        assertSame(Scene.ArcaneCastle, SceneryCatalog.byId("arcane_castle"))
        assertSame(Scene.ArcaneCastle, SceneryCatalog.byId("arcane_castle", Skin.Arcane))
        assertNull("山水皮肤不该认得弧光的 id", SceneryCatalog.byId("arcane_castle", Skin.Lumos))
        assertNull(SceneryCatalog.byId("nope"))
    }

    @Test
    fun `会话缩略图在同一皮肤内稳定轮换`() {
        repeat(8) { index ->
            val id = index.toLong() * 37
            val a = SceneryCatalog.forConversation(id, Skin.Arcane)
            val b = SceneryCatalog.forConversation(id, Skin.Arcane)
            assertSame(a, b)
            assertEquals(Skin.Arcane, a.skin)
        }
    }
}
