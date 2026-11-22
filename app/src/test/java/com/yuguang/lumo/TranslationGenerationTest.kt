package com.yuguang.lumo

import com.yuguang.lumo.ui.translate.TranslationGeneration
import com.yuguang.lumo.ui.translate.TranslationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 翻译竞态的核心契约。
 *
 * 用户快速改输入时，旧请求的结果绝不能写进新输入对应的界面。
 * 这条规则与协程无关，可以在纯 JVM 上验证。
 */
class TranslationGenerationTest {

    private fun request(generation: Long, text: String = "hello") = TranslationRequest(
        text = text,
        source = "en",
        target = "zh",
        model = "seed-translation",
        generation = generation,
        save = false,
    )

    @Test
    fun firstRequestIsCurrent() {
        val generation = TranslationGeneration()
        val first = generation.next()
        assertTrue(generation.isCurrent(first))
    }

    @Test
    fun secondRequestSupersedesTheFirst() {
        val generation = TranslationGeneration()
        val a = generation.next()
        val b = generation.next()

        assertFalse(generation.isCurrent(a))
        assertTrue(generation.isCurrent(b))
    }

    @Test
    fun invalidateDropsInFlightResult() {
        val generation = TranslationGeneration()
        val a = generation.next()
        generation.invalidate()

        assertFalse(generation.isCurrent(a))
    }

    @Test
    fun invalidateWithoutRequestStillAdvances() {
        val generation = TranslationGeneration()
        assertEquals(0L, generation.now())
        generation.invalidate()
        assertEquals(1L, generation.now())
    }

    @Test
    fun requestCarriesWhatWasTranslated() {
        // 请求必须自带文本与语种：回写时不能再读 state（那时用户可能已经改过）。
        val value = request(7, text = "原始输入")
        assertEquals("原始输入", value.text)
        assertEquals("en", value.source)
        assertEquals("zh", value.target)
        assertEquals(7L, value.generation)
        assertFalse(value.save)
    }

    @Test
    fun monotonicAcrossManyEdits() {
        val generation = TranslationGeneration()
        val issued = (1..50).map { generation.next() }
        assertEquals(50, issued.distinct().size)
        assertEquals(issued.last(), generation.now())
        // 只有最后一次允许回写。
        assertEquals(1, issued.count { generation.isCurrent(it) })
    }
}
