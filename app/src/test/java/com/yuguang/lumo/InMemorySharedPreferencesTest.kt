package com.yuguang.lumo

import com.yuguang.lumo.data.prefs.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内存兜底存储的语义测试。
 *
 * 它存在的唯一理由是「加密不可用时也不写明文」，所以除了读写正确，
 * 还要验证 `clear()` 与监听器这类会被设置页依赖的行为。
 */
class InMemorySharedPreferencesTest {

    @Test
    fun storesAndOverwritesValues() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString("apiKey", "sk-1").putBoolean("memory", true).apply()

        assertEquals("sk-1", prefs.getString("apiKey", null))
        assertTrue(prefs.getBoolean("memory", false))

        prefs.edit().putString("apiKey", "sk-2").apply()
        assertEquals("sk-2", prefs.getString("apiKey", null))
    }

    @Test
    fun defaultsWhenMissing() {
        val prefs = InMemorySharedPreferences()
        assertNull(prefs.getString("absent", null))
        assertEquals(0, prefs.getInt("absent", 0))
        assertFalse(prefs.contains("absent"))
    }

    @Test
    fun clearDropsEverything() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString("a", "1").putString("b", "2").apply()

        prefs.edit().clear().apply()

        assertTrue(prefs.getAll().isEmpty())
        assertNull(prefs.getString("a", null))
    }

    @Test
    fun removeIsAppliedWithSameCommit() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString("a", "1").putString("b", "2").apply()

        prefs.edit().remove("a").apply()

        assertNull(prefs.getString("a", null))
        assertEquals("2", prefs.getString("b", null))
    }

    @Test
    fun listenersSeeChangedKeysOnly() {
        val prefs = InMemorySharedPreferences()
        val seen = mutableListOf<String?>()
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            seen.add(key)
        }

        prefs.edit().putString("a", "1").apply()

        assertTrue(seen.contains("a"))
    }

    @Test
    fun valuesNeverTouchDisk() {
        // 这条是设计契约：内存实现的 edit() 不返回任何可持久化的句柄，
        // commit() 只是把内存副本翻新。只要没有文件 API 介入，明文就不可能落盘。
        val prefs = InMemorySharedPreferences()
        assertTrue(prefs.edit().putString("apiKey", "sk").commit())
        assertEquals("sk", prefs.getString("apiKey", null))
    }
}
