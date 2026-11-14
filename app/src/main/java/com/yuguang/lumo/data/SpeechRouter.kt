package com.yuguang.lumo.data

import com.yuguang.lumo.domain.model.FreeTtsBackend
import com.yuguang.lumo.domain.model.SpeechPreferences
import com.yuguang.lumo.domain.model.SpeechTier
import com.yuguang.lumo.domain.model.TtsEngine

/**
 * 档位路由：只做「这次该走哪条链路」的判定，不发起任何请求。
 *
 * 判定集中在一处，方便单测覆盖「免费档不碰豆包、收费档不碰免费后端」；
 * 真正的实现由各自的 Client 负责。
 */
object SpeechRouter {
    /** 朗读后端。PAID 档保持 1.0 的语义：DOUBAO 或本机。 */
    enum class TtsRoute {
        DOUBAO,
        FREE_CLOUD,
        LOCAL,
    }

    enum class AsrRoute {
        DOUBAO,
        FREE,
    }

    enum class TranslateRoute {
        DOUBAO_SEED,
        FREE,
    }

    enum class LiveRoute {
        DOUBAO_AST,
        FREE_PIPELINE,
    }

    fun tts(preferences: SpeechPreferences, forceLocal: Boolean = false): TtsRoute {
        if (forceLocal) return TtsRoute.LOCAL
        if (preferences.tier == SpeechTier.FREE) {
            return when (preferences.freeTtsBackend) {
                FreeTtsBackend.SYSTEM -> TtsRoute.LOCAL
                FreeTtsBackend.AUTO,
                FreeTtsBackend.BAIDU,
                FreeTtsBackend.YOUDAO,
                -> TtsRoute.FREE_CLOUD
            }
        }
        return when (preferences.engine) {
            TtsEngine.LOCAL -> TtsRoute.LOCAL
            TtsEngine.DOUBAO -> TtsRoute.DOUBAO
        }
    }

    fun asr(preferences: SpeechPreferences): AsrRoute =
        if (preferences.tier == SpeechTier.FREE) AsrRoute.FREE else AsrRoute.DOUBAO

    fun translate(preferences: SpeechPreferences): TranslateRoute =
        if (preferences.tier == SpeechTier.FREE) TranslateRoute.FREE else TranslateRoute.DOUBAO_SEED

    fun live(preferences: SpeechPreferences): LiveRoute =
        if (preferences.tier == SpeechTier.FREE) LiveRoute.FREE_PIPELINE else LiveRoute.DOUBAO_AST

    /** 免费档不支持 Seeduplex，语音页直接进入逐轮对话。 */
    fun realtimeVoice(preferences: SpeechPreferences): Boolean =
        preferences.tier == SpeechTier.PAID

    /** 免费档不含 Seedream 生图。 */
    fun imageGeneration(preferences: SpeechPreferences): Boolean =
        preferences.tier == SpeechTier.PAID

    /** 免费档不支持复刻原声。 */
    fun voiceCloning(preferences: SpeechPreferences): Boolean =
        preferences.tier == SpeechTier.PAID
}
