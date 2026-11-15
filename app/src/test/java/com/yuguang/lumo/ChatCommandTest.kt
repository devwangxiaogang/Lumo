package com.yuguang.lumo

import com.yuguang.lumo.domain.ChatCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCommandTest {
    @Test
    fun `parses draw with prompt`() {
        assertEquals(ChatCommand.Draw("一只猫"), ChatCommand.parse("/draw 一只猫"))
        assertEquals(ChatCommand.Draw("一只猫"), ChatCommand.parse("  /画图 一只猫  "))
    }

    @Test
    fun `parses translate and summary`() {
        assertEquals(ChatCommand.Translate("hello"), ChatCommand.parse("/translate hello"))
        assertEquals(ChatCommand.Summary(""), ChatCommand.parse("/summary"))
        assertEquals(ChatCommand.Summary("重点"), ChatCommand.parse("/总结 重点"))
    }

    @Test
    fun `parses model with and without argument`() {
        assertEquals(ChatCommand.Model(""), ChatCommand.parse("/model"))
        assertEquals(ChatCommand.Model("flash"), ChatCommand.parse("/model flash"))
    }

    @Test
    fun `parses no-argument commands`() {
        assertEquals(ChatCommand.Voice, ChatCommand.parse("/voice"))
        assertEquals(ChatCommand.NewChat, ChatCommand.parse("/new"))
        assertEquals(ChatCommand.Help, ChatCommand.parse("/help"))
        assertEquals(ChatCommand.Help, ChatCommand.parse("/帮助"))
    }

    @Test
    fun `unknown command is reported not ignored`() {
        assertEquals(ChatCommand.Unknown, ChatCommand.parse("/xyz abc"))
    }

    @Test
    fun `unknown commands keep the original text sendable`() {
        // Unknown 交给发送路径原样发出：粘贴 /usr/bin/env 之类的内容不能被吞掉。
        // 解析结果本身不携带参数，调用方用的是 input 里的原文，这里锁住「返回 Unknown」这个约定。
        assertEquals(ChatCommand.Unknown, ChatCommand.parse("/usr/bin/env bash"))
        assertEquals(ChatCommand.Unknown, ChatCommand.parse("/api/v1/users 是什么"))
    }

    @Test
    fun `plain messages are not commands`() {
        assertNull(ChatCommand.parse("你好"))
        assertNull(ChatCommand.parse("C:/Users/me/file.txt"))
        assertNull(ChatCommand.parse("1/2 + 3/4"))
        assertNull(ChatCommand.parse("/"))
    }

    @Test
    fun `looksLikeCommand only checks prefix`() {
        assertTrue(ChatCommand.looksLikeCommand("  /draw"))
        assertTrue(!ChatCommand.looksLikeCommand("draw /"))
    }

    @Test
    fun `catalog covers every documented trigger`() {
        val triggers = ChatCommand.specs().map { it.trigger }
        assertEquals(listOf("/draw", "/translate", "/summary", "/model", "/voice", "/new", "/help"), triggers)
        assertTrue(ChatCommand.specs().all { it.keywords.isNotEmpty() })
    }
}
