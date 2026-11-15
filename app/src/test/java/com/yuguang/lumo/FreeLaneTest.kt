package com.yuguang.lumo

import com.yuguang.lumo.data.SpeechRouter
import com.yuguang.lumo.data.remote.ChunkPiece
import com.yuguang.lumo.data.remote.SseDecoder
import com.yuguang.lumo.domain.model.FreeTtsBackend
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.SpeechPreferences
import com.yuguang.lumo.domain.model.SpeechTier
import com.yuguang.lumo.domain.model.TtsEngine
import com.yuguang.lumo.domain.model.VoiceChoice
import com.yuguang.lumo.domain.model.freeLivePairError
import com.yuguang.lumo.domain.model.freeSpeakError
import com.yuguang.lumo.domain.model.freeForcedReasoning
import com.yuguang.lumo.domain.model.freeModelBlocklist
import com.yuguang.lumo.domain.model.freeTtsSupports
import com.yuguang.lumo.domain.model.livePairError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 免费链路：思考字段别名、档位路由、语种边界。
 *
 * 这些规则决定「收费链路是否被破解」与「免费链路是否乱发请求」，
 * 两边都必须有测试兜住。
 */
class FreeLaneTest {
    // --- 思考字段三形态 ---

    @Test
    fun decodesKiloReasoningField() {
        val pieces = SseDecoder.decode(
            """data: {"choices":[{"delta":{"reasoning":"想一下"}}]}""",
        )
        assertEquals(listOf(ChunkPiece.Reasoning("想一下")), pieces)
    }

    @Test
    fun decodesReasoningDetailsArray() {
        val pieces = SseDecoder.decode(
            """data: {"choices":[{"delta":{"reasoning_details":[{"type":"reasoning.text","text":"分段思考"}]}}]}""",
        )
        assertEquals(listOf(ChunkPiece.Reasoning("分段思考")), pieces)
    }

    @Test
    fun prefersReasoningContentOverReasoningAlias() {
        // 同一段思考同时出现在两个字段时只取一次，避免界面重复。
        val pieces = SseDecoder.decode(
            """data: {"choices":[{"delta":{"reasoning_content":"主字段","reasoning":"别名"}}]}""",
        )
        assertEquals(listOf(ChunkPiece.Reasoning("主字段")), pieces)
    }

    @Test
    fun decodesUsageCost() {
        val pieces = SseDecoder.decode(
            """data: {"choices":[],"usage":{"prompt_tokens":11,"completion_tokens":32,"cost":0}}""",
        )
        val usage = pieces.filterIsInstance<ChunkPiece.Usage>().single()
        assertEquals(11, usage.promptTokens)
        assertEquals(32, usage.completionTokens)
        assertEquals(0.0, usage.costUsd!!, 0.0001)
    }

    @Test
    fun marksRateLimitAsRetryable() {
        val pieces = SseDecoder.decode(
            """data: {"error":{"message":"Rate limit exceeded, try again later"}}""",
        )
        assertTrue(pieces.filterIsInstance<ChunkPiece.Invalid>().single().retryable)
    }

    @Test
    fun keepsAuthFailureNotRetryable() {
        val pieces = SseDecoder.decode(
            """data: {"error":{"message":"Invalid API key"}}""",
        )
        assertFalse(pieces.filterIsInstance<ChunkPiece.Invalid>().single().retryable)
    }

    // --- 档位路由 ---

    @Test
    fun paidTierNeverRoutesToFreeClients() {
        val preferences = SpeechPreferences(tier = SpeechTier.PAID, engine = TtsEngine.DOUBAO)
        assertEquals(SpeechRouter.TtsRoute.DOUBAO, SpeechRouter.tts(preferences))
        assertEquals(SpeechRouter.AsrRoute.DOUBAO, SpeechRouter.asr(preferences))
        assertEquals(SpeechRouter.TranslateRoute.DOUBAO_SEED, SpeechRouter.translate(preferences))
        assertEquals(SpeechRouter.LiveRoute.DOUBAO_AST, SpeechRouter.live(preferences))
    }

    @Test
    fun paidLocalEngineStillBypassesCloud() {
        val preferences = SpeechPreferences(tier = SpeechTier.PAID, engine = TtsEngine.LOCAL)
        assertEquals(SpeechRouter.TtsRoute.LOCAL, SpeechRouter.tts(preferences))
    }

    @Test
    fun freeTierRoutesToFreeClients() {
        val preferences = SpeechPreferences(tier = SpeechTier.FREE)
        assertEquals(SpeechRouter.TtsRoute.FREE_CLOUD, SpeechRouter.tts(preferences))
        assertEquals(SpeechRouter.AsrRoute.FREE, SpeechRouter.asr(preferences))
        assertEquals(SpeechRouter.TranslateRoute.FREE, SpeechRouter.translate(preferences))
        assertEquals(SpeechRouter.LiveRoute.FREE_PIPELINE, SpeechRouter.live(preferences))
        assertFalse(SpeechRouter.realtimeVoice(preferences))
        assertFalse(SpeechRouter.imageGeneration(preferences))
    }

    @Test
    fun freeSystemBackendFallsBackToLocal() {
        val preferences = SpeechPreferences(tier = SpeechTier.FREE, freeTtsBackend = FreeTtsBackend.SYSTEM)
        assertEquals(SpeechRouter.TtsRoute.LOCAL, SpeechRouter.tts(preferences))
    }

    @Test
    fun forceLocalOverridesAnyTier() {
        val preferences = SpeechPreferences(tier = SpeechTier.FREE)
        assertEquals(SpeechRouter.TtsRoute.LOCAL, SpeechRouter.tts(preferences, forceLocal = true))
    }

    // --- 语种边界（实测只有五种） ---

    @Test
    fun freeTtsSupportsOnlyFiveLanguages() {
        listOf("zh", "en", "ja", "ko", "ru").forEach { assertTrue(it, freeTtsSupports(it)) }
        listOf("fr", "de", "es", "it", "pt", "th", "vi", "ar", "yue-CN").forEach {
            assertFalse(it, freeTtsSupports(it))
        }
    }

    @Test
    fun unsupportedLanguageReportsHint() {
        assertNotNull(freeSpeakError("fr"))
        assertNull(freeSpeakError("en"))
    }

    @Test
    fun freeLivePairRejectsCloneAndWideLanguages() {
        assertNotNull(freeLivePairError("zh", "en", VoiceChoice.CLONE))
        assertNull(freeLivePairError("zh", "en", VoiceChoice.FEMALE))
        // 收费链路允许 中文↔日语 但免费链路只在中/英之间转换目标语；
        // 至少其中一个端点必须在五种语言内才会被放行。
        assertNull(freeLivePairError("zh", "en", VoiceChoice.MALE))
    }

    @Test
    fun paidPairRuleStillApplies() {
        // 免费规则不能放宽收费规则：同语种互译仍要被挡。
        assertNotNull(livePairError("zh", "zh", VoiceChoice.FEMALE))
        assertNotNull(freeLivePairError("zh", "zh", VoiceChoice.FEMALE))
    }

    // --- 免费车道的模型声明 ---

    @Test
    fun freeProviderNeedsNoKey() {
        assertFalse(ProviderId.FREE.requiresKey)
        assertTrue(ProviderId.DEEPSEEK.requiresKey)
        assertTrue(ProviderId.DOUBAO.requiresKey)
    }

    @Test
    fun freeProviderShipsStaticFallbackModels() {
        assertTrue(ProviderId.FREE.models.isNotEmpty())
        // 默认模型必须是实测最快且稳定的那个：自动路由 kilo-auto 对匿名用户长期 429。
        assertEquals("nvidia/nemotron-3.5-lightning:free", ProviderId.FREE.defaultModel)
        assertFalse(ProviderId.FREE.models.any { it.id == "inclusionai/ling-3.2-flash:free" })
        assertFalse(ProviderId.FREE.models.any { it.id == "nvidia/nemotron-3-ultra-550b-a55b:free" })
    }

    @Test
    fun forcedReasoningModelsAreNotBlocklistedButListed() {
        // 强制思考 ≠ 不可用：它们只是不能接受 reasoning.enabled=false。
        freeForcedReasoning.forEach { id ->
            assertFalse("$id 不该同时进黑名单", id in freeModelBlocklist)
        }
        assertTrue("baidu/ernie-5.5-flash-preview:free" in freeForcedReasoning)
    }

    @Test
    fun blocklistedModelsNeverShipInStaticList() {
        // 黑名单是「远端目录兜底过滤」，静态清单也不该再列出它们。
        freeModelBlocklist.forEach { blocked ->
            assertTrue(blocked, ProviderId.FREE.models.none { it.id == blocked })
        }
        assertTrue(freeModelBlocklist.isNotEmpty())
    }
}
