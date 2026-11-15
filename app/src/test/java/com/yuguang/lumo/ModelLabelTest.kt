package com.yuguang.lumo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自检里的模型短名规则。
 *
 * 列表要能「一眼分辨」：`kilo-auto/free` 与 `openrouter/free` 曾经的短名都是 `free`，
 * 两行一模一样，看着像重复探测。
 */
class ModelLabelTest {
    @Test
    fun stripsFreeSuffixAndVendorPrefix() {
        assertEquals("nemotron-3.5-lightning", shortLabel("nvidia/nemotron-3.5-lightning:free"))
        assertEquals("particle-1", shortLabel("particle-ai/particle-1:free"))
        assertEquals("dots-3-note-preview", shortLabel("dots-studio/dots-3-note-preview:free"))
    }

    @Test
    fun fallsBackToFirstSegmentWhenTailIsFree() {
        assertEquals("kilo-auto", shortLabel("kilo-auto/free"))
        assertEquals("openrouter", shortLabel("openrouter/free"))
    }

    @Test
    fun handlesModelsWithoutSlash() {
        assertEquals("some-model", shortLabel("some-model:free"))
    }

    @Test
    fun labelsAreDistinctForTheDefaultChains() {
        val chain = listOf(
            "nvidia/nemotron-3.5-lightning:free",
            "nvidia/nemotron-3.5-super-vl:free",
            "baidu/ernie-5.5-flash-preview:free",
            "particle-ai/particle-1:free",
            "reflection/beam-free",
            "kilo-auto/free",
            "dots-studio/dots-3-note-preview:free",
            "openrouter/free",
        )
        val labels = chain.map { shortLabel(it) }
        assertEquals(labels.size, labels.distinct().size)
        assertTrue(labels.none { it.isBlank() })
    }

    /** 与 FreeLaneSelfTest.shortModelLabel 同规则（私有方法，这里复刻一份用于回归）。 */
    private fun shortLabel(model: String): String {
        val withoutTag = model.removeSuffix(":free")
        val tail = withoutTag.substringAfter('/')
        return if (tail.equals("free", ignoreCase = true) || tail.isBlank()) {
            withoutTag.substringBefore('/')
        } else {
            tail
        }
    }
}
