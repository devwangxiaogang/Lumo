package com.yuguang.lumo

import com.yuguang.lumo.ui.chat.normalizeMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 块级记号收敛：模型常见的 `### 标题` / `> 引用` / `---` / `- 列表` 不能以原文出现在气泡里。
 *
 * 只测纯函数的输出文本，不涉及 Compose 渲染。
 */
class MarkdownNormalizeTest {

    @Test
    fun `heading markers become bold text`() {
        assertEquals("**五、主流工具**", normalizeMarkdown("### 五、主流工具"))
        // 行中间的井号不是标题，保持原样。
        assertEquals("第 1 点 # 说明", normalizeMarkdown("第 1 点 # 说明"))
    }

    @Test
    fun `heading keeps its body and drops trailing hashes`() {
        assertEquals("**要点清单**", normalizeMarkdown("#### 要点清单 ####").trim())
    }

    @Test
    fun `blockquote marker is removed`() {
        assertEquals("引用一句", normalizeMarkdown("> 引用一句"))
        assertEquals("两行", normalizeMarkdown(">两行"))
    }

    @Test
    fun `horizontal rule becomes three dots`() {
        assertEquals("· · ·", normalizeMarkdown("---"))
        assertEquals("· · ·", normalizeMarkdown("***"))
        assertEquals("· · ·", normalizeMarkdown("___"))
    }

    @Test
    fun `bullets are normalized and keep nested indent`() {
        val out = normalizeMarkdown("- 第一项\n  * 第二项\n+ 第三项")
        assertEquals("• 第一项\n• 第二项\n• 第三项", out)
    }

    @Test
    fun `bold and code markers are left for the inline parser`() {
        val source = "普通 **重点** 与 `code` 行"
        val out = normalizeMarkdown(source)
        assertEquals(source, out)
        assertTrue(out.contains("**重点**"))
    }

    @Test
    fun `plain text is untouched`() {
        val source = "复利：利息也能生利息。\n第 1 年：100 → 110"
        assertEquals(source, normalizeMarkdown(source))
        assertFalse(normalizeMarkdown(source).contains("· · ·"))
    }
}
