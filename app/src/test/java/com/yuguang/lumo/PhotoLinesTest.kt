package com.yuguang.lumo

import com.yuguang.lumo.data.PhotoLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLinesTest {
    @Test
    fun `parses fenced json with lines array`() {
        val raw = """
            ```json
            {"lines":[{"text":"营业时间","translation":"Opening hours"},{"text":"09:00","translation":"09:00"}]}
            ```
        """.trimIndent()
        val result = PhotoLines.parse(raw)
        assertEquals(2, result.lines.size)
        assertEquals("营业时间", result.lines[0].source)
        assertEquals("Opening hours", result.lines[0].target)
        assertFalse(result.fallback)
    }

    @Test
    fun `parses bare array with alias keys`() {
        val raw = "[{\"original\":\"出口\",\"translated\":\"Exit\"}]"
        val result = PhotoLines.parse(raw)
        assertEquals(1, result.lines.size)
        assertEquals("出口", result.lines[0].source)
        assertEquals("Exit", result.lines[0].target)
    }

    @Test
    fun `parses nested data object`() {
        val raw = "{\"data\":[{\"source\":\"a\",\"target\":\"b\"}]}"
        val result = PhotoLines.parse(raw)
        assertEquals(1, result.lines.size)
        assertEquals("b", result.lines[0].target)
    }

    @Test
    fun `falls back to plain text lines`() {
        val result = PhotoLines.parse("第一行\n第二行")
        assertEquals(2, result.lines.size)
        assertTrue(result.fallback)
        assertEquals("第一行", result.lines[0].source)
    }

    @Test
    fun `empty input yields nothing`() {
        assertTrue(PhotoLines.parse("   ").lines.isEmpty())
    }

    @Test
    fun `keeps newlines inside values`() {
        val raw = "{\"lines\":[{\"text\":\"a\\nb\",\"translation\":\"c\"}]}"
        val result = PhotoLines.parse(raw)
        assertEquals("a\nb", result.lines[0].source)
    }

    @Test
    fun `broken json degrades to text`() {
        val result = PhotoLines.parse("{\"lines\": [{\"text\": ")
        assertTrue(result.lines.isNotEmpty())
        assertTrue(result.fallback)
    }
}
