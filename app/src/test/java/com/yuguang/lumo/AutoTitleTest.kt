package com.yuguang.lumo

import com.yuguang.lumo.data.AutoTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自动标题的清洗规则：只去外壳，不改语义；失败一律 null，保住旧标题。 */
class AutoTitleTest {
    @Test
    fun plainTitleKept() {
        assertEquals("周末旅行计划", AutoTitle.clean("周末旅行计划"))
    }

    @Test
    fun markdownFenceRemoved() {
        assertEquals("Kotlin 协程入门", AutoTitle.clean("```\nKotlin 协程入门\n```"))
    }

    @Test
    fun chinesePrefixRemoved() {
        assertEquals("报销流程梳理", AutoTitle.clean("标题：报销流程梳理"))
        assertEquals("报销流程梳理", AutoTitle.clean("标题:报销流程梳理"))
    }

    @Test
    fun englishPrefixRemoved() {
        assertEquals("Weekly Sync Notes", AutoTitle.clean("Title: Weekly Sync Notes"))
    }

    @Test
    fun quotesAndBracketsTrimmed() {
        assertEquals("如何写好周报", AutoTitle.clean("「如何写好周报」"))
        assertEquals("如何写好周报", AutoTitle.clean("\"如何写好周报\""))
        assertEquals("《三体》读后感", AutoTitle.clean("《三体》读后感"))
    }

    @Test
    fun trailingPunctuationDropped() {
        assertEquals("会议纪要", AutoTitle.clean("会议纪要。"))
        assertEquals("Meeting Notes", AutoTitle.clean("Meeting Notes."))
    }

    @Test
    fun firstNonBlankLineWins() {
        assertEquals("模型对比", AutoTitle.clean("好的，我给出标题：\n模型对比\n希望有帮助"))
    }

    @Test
    fun longTitleTruncated() {
        val raw = "这是一个非常非常长的标题用于测试截断行为是否生效并且不超过上限值"
        val cleaned = AutoTitle.clean(raw)
        assertEquals(AutoTitle.MAX_LENGTH, cleaned!!.length)
    }

    @Test
    fun emptyAndSymbolOnlyRejected() {
        assertNull(AutoTitle.clean(""))
        assertNull(AutoTitle.clean("   \n  "))
        assertNull(AutoTitle.clean("——..."))
        assertNull(AutoTitle.clean("标题："))
    }

    @Test
    fun needsTitleRecognizesTruncatedTitleFromFirstMessage() {
        // 实战 bug：生成流程一旦把标题写成「首条消息前 24 字」，只比较空串就再也不会触发自动标题。
        val question = "Recommend three books about distributed systems."
        assertTrue(AutoTitle.needsTitle(AutoTitle.fallback(question), question))
        // 用户自己改过的标题不是派生物，不能被覆盖。
        assertFalse(AutoTitle.needsTitle("我的分布式书单", question))
    }

    @Test
    fun needsTitleOnlyForBlankOrLegacy() {
        assertTrue(AutoTitle.needsTitle(""))
        assertTrue(AutoTitle.needsTitle("   "))
        assertTrue(AutoTitle.needsTitle("新对话"))
        assertTrue(AutoTitle.needsTitle("New chat"))
        assertFalse(AutoTitle.needsTitle("用户自己改的标题"))
    }

    @Test
    fun fallbackUsesFirstLineOfFirstMessage() {
        assertEquals("帮我写周报", AutoTitle.fallback("\n帮我写周报\n第二行不要"))
        assertEquals("", AutoTitle.fallback("   "))
    }

    @Test
    fun promptMentionsLanguageAndLength() {
        val prompt = AutoTitle.prompt("中文")
        assertTrue(prompt.contains("中文"))
        assertTrue(prompt.contains("6-12"))
    }
}
