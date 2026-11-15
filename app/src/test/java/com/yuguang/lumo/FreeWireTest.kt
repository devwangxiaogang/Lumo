package com.yuguang.lumo

import com.yuguang.lumo.data.remote.toolSchemaOf
import com.yuguang.lumo.ui.chat.estimateCost
import com.yuguang.lumo.ui.chat.formatCost
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.domain.model.ToolCallRequest
import com.yuguang.lumo.domain.model.WireMessage
import com.yuguang.lumo.domain.model.freeFallbackChain
import com.yuguang.lumo.domain.model.freeTranslateChain
import com.yuguang.lumo.domain.model.freeVisionChain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 免费车道的协议细节与用量估算。
 *
 * 这些规则一旦错，界面上不会报错，只会「静默地不工作」——
 * 例如思考开关没下发、工具轮次漏了 tool_call_id、免费模型被算成收费。
 */
class FreeWireTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun toolSchemaMatchesOpenAiShape() {
        val schema = toolSchemaOf(
            name = "get_time",
            description = "Get current time",
            parameters = buildJsonObject { },
        )
        val fn = schema["function"]!!.jsonObject
        assertEquals("get_time", fn["name"]!!.jsonPrimitive.content)
        assertEquals("function", schema["type"]!!.jsonPrimitive.content)
        assertTrue(fn.containsKey("parameters"))
    }

    @Test
    fun fallbackChainStartsWithFastStableModel() {
        // 自动路由对匿名用户长期 429，不能再当首个兜底。
        assertEquals("nvidia/nemotron-3.5-lightning:free", freeFallbackChain.first())
        // 链里不能有重复，否则降级会白跑一轮。
        assertEquals(freeFallbackChain.size, freeFallbackChain.distinct().size)
    }

    @Test
    fun fallbackChainSkipsModelsThatAlwaysFail() {
        listOf(
            "inclusionai/ling-3.2-flash:free",    // 整日额度
            "liquid/lfm-3-1.7b:free",             // 1.7B 质量不足
            "nvidia/nemotron-3-ultra-550b-a55b:free", // 偶发 60 秒首字
        ).forEach { assertTrue(it, it !in freeFallbackChain) }
    }

    @Test
    fun translateChainUsesSameFreeModels() {
        assertTrue(freeTranslateChain.isNotEmpty())
        assertTrue(freeTranslateChain.all { it.endsWith(":free") || it == "kilo-auto/free" })
    }

    @Test
    fun visionChainContainsOnlyVisionModels() {
        assertTrue(freeVisionChain.isNotEmpty())
        // 视觉链里的每个模型都必须被静态能力表标成可识图，否则图片请求会直接 4xx。
        freeVisionChain.forEach { id ->
            val option = ProviderId.FREE.models.firstOrNull { it.id == id }
            assertTrue("$id 未在静态清单中声明", option != null)
            assertTrue("$id 未标记 vision", option!!.vision)
        }
    }

    @Test
    fun toolMessageCarriesCallId() {
        val message = WireMessage(
            role = Role.TOOL,
            text = "12:00",
            toolCallId = "call_1",
            toolName = "get_time",
        )
        assertEquals(Role.TOOL, message.role)
        assertEquals("call_1", message.toolCallId)
        assertEquals("get_time", message.toolName)
    }

    @Test
    fun assistantTurnHoldsToolRequests() {
        val message = WireMessage(
            role = Role.ASSISTANT,
            text = "",
            toolCalls = listOf(ToolCallRequest("call_1", "get_time", "{}")),
        )
        assertEquals(1, message.toolCalls.size)
        assertEquals("call_1", message.toolCalls.single().id)
    }

    @Test
    fun freeTierCostIsAlwaysZero() {
        assertEquals(
            0.0,
            estimateCost(ProviderId.FREE, "kilo-auto/free", 100_000, 50_000),
            0.0000001,
        )
        assertEquals("¥0.00", formatCost(0.0))
    }

    @Test
    fun paidModelCostUsesPriceTable() {
        // deepseek-flash：输入 0.10 / 输出 0.30 每百万 token。
        val cost = estimateCost(ProviderId.DEEPSEEK, "deepseek-flash", 1_000_000, 0)
        assertEquals(0.10, cost, 0.0001)
    }

    @Test
    fun unknownModelCostsNothingRatherThanGuessing() {
        assertEquals(0.0, estimateCost(ProviderId.DEEPSEEK, "some-unknown-model", 1000, 1000), 0.0)
    }

    @Test
    fun costDisplayKeepsSubCentPrecision() {
        // 不足一美分时不显示成 ¥0.00，否则用户会以为免费。
        assertTrue(formatCost(0.0001).startsWith("¥0.000"))
    }
}
