package com.yuguang.lumo.domain.model

import androidx.annotation.StringRes
import com.yuguang.lumo.R

/**
 * 语音链路档位。
 *
 * - [PAID]：豆包 / 方舟，需要密钥，行为与 1.0 完全一致。
 * - [FREE]：全链路免费（Kilo 公共池 + 公开网页语音端点 + 本机 TTS 兜底），无需任何密钥。
 *
 * 两套链路用显式档位隔开，互不影响；默认 PAID，升级后老用户体验不变。
 */
enum class SpeechTier(@StringRes val labelRes: Int, val free: Boolean) {
    PAID(R.string.tier_paid, false),
    FREE(R.string.tier_free, true),
}

/** 免费朗读的云端后端。AUTO 按 百度 → 有道 → 本机 顺序尝试。 */
enum class FreeTtsBackend(@StringRes val labelRes: Int) {
    AUTO(R.string.free_tts_auto),
    BAIDU(R.string.free_tts_baidu),
    YOUDAO(R.string.free_tts_youdao),
    SYSTEM(R.string.free_tts_system),
}

/** 朗读引擎。对话、文字翻译和同传定稿句共用这一项。 */
enum class TtsEngine(@StringRes val labelRes: Int) {
    DOUBAO(R.string.engine_doubao),
    LOCAL(R.string.engine_local),
}

/** 豆包播报音色。复刻原声只用于同声翻译，不用于朗读文字。 */
enum class VoiceChoice(
    @StringRes val labelRes: Int,
    val speakerId: String?,
    val ttsSpeakerId: String,
) {
    FEMALE(R.string.voice_female, "zh_female_vv_uranus_bigtts", "zh_female_vv_uranus_bigtts"),
    MALE(R.string.voice_male, "zh_male_jingqiangkanye_emo_mars_bigtts", "zh_male_m191_uranus_bigtts"),
    CLONE(R.string.voice_clone, null, "zh_female_vv_uranus_bigtts"),
}

data class SpeechLanguage(val code: String, @StringRes val labelRes: Int)

data class SpeechPreferences(
    val tier: SpeechTier = SpeechTier.PAID,
    val engine: TtsEngine = TtsEngine.DOUBAO,
    val speaker: VoiceChoice = VoiceChoice.FEMALE,
    val freeTtsBackend: FreeTtsBackend = FreeTtsBackend.AUTO,
    val translationModel: String = DEFAULT_TRANSLATION_MODEL,
    val textSource: String = "zh",
    val textTarget: String = "en",
    val liveSource: String = "zh",
    val liveTarget: String = "en",
    val liveVoice: VoiceChoice = VoiceChoice.FEMALE,
    /** 边生成边朗读：回复流式输出时按句合成播报。默认关闭，升级用户行为不变。 */
    val autoRead: Boolean = false,
)

data class TranslationModelOption(val id: String, @StringRes val labelRes: Int)

const val DEFAULT_TRANSLATION_MODEL = "doubao-seed-translation-250915"

val translationModels = listOf(
    TranslationModelOption(DEFAULT_TRANSLATION_MODEL, R.string.model_seed_translation),
)

/** 文字翻译语种。空 code 表示让服务自动检测源语言。 */
val textLanguages = listOf(
    SpeechLanguage("zh", R.string.lang_zh_hans),
    SpeechLanguage("zh-Hant", R.string.lang_zh_hant),
    SpeechLanguage("en", R.string.lang_en),
    SpeechLanguage("ja", R.string.lang_ja),
    SpeechLanguage("ko", R.string.lang_ko),
    SpeechLanguage("de", R.string.lang_de),
    SpeechLanguage("fr", R.string.lang_fr),
    SpeechLanguage("es", R.string.lang_es),
    SpeechLanguage("it", R.string.lang_it),
    SpeechLanguage("pt", R.string.lang_pt),
    SpeechLanguage("ru", R.string.lang_ru),
    SpeechLanguage("th", R.string.lang_th),
    SpeechLanguage("vi", R.string.lang_vi),
    SpeechLanguage("ar", R.string.lang_ar),
    SpeechLanguage("id", R.string.lang_id),
    SpeechLanguage("ms", R.string.lang_ms),
    SpeechLanguage("nl", R.string.lang_nl),
    SpeechLanguage("pl", R.string.lang_pl),
    SpeechLanguage("cs", R.string.lang_cs),
    SpeechLanguage("tr", R.string.lang_tr),
    SpeechLanguage("uk", R.string.lang_uk),
)

private val liveEight = setOf("zh", "en", "de", "fr", "es", "id", "ja", "pt")

val liveLanguages = listOf(
    SpeechLanguage("zh", R.string.lang_zh),
    SpeechLanguage("en", R.string.lang_en),
    SpeechLanguage("de", R.string.lang_de),
    SpeechLanguage("fr", R.string.lang_fr),
    SpeechLanguage("es", R.string.lang_es),
    SpeechLanguage("id", R.string.lang_id),
    SpeechLanguage("ja", R.string.lang_ja),
    SpeechLanguage("pt", R.string.lang_pt),
    SpeechLanguage("ko", R.string.lang_ko),
    SpeechLanguage("ru", R.string.lang_ru),
    SpeechLanguage("it", R.string.lang_it),
    SpeechLanguage("ar", R.string.lang_ar),
    SpeechLanguage("tr", R.string.lang_tr),
    SpeechLanguage("ms", R.string.lang_ms),
    SpeechLanguage("vi", R.string.lang_vi),
    SpeechLanguage("th", R.string.lang_th),
    SpeechLanguage("nl", R.string.lang_nl),
    SpeechLanguage("ro", R.string.lang_ro),
    SpeechLanguage("pl", R.string.lang_pl),
    SpeechLanguage("cs", R.string.lang_cs),
    SpeechLanguage("yue-CN", R.string.lang_yue),
    SpeechLanguage("sh-CN", R.string.lang_sh),
)

/**
 * 同声传译 2.0 的语种限制：一边必须是中文或英文。
 * 指定女声或男声时目标语言只能是中文或英文；复刻原声两边都限制在八种语言。
 */
fun livePairError(source: String, target: String, voice: VoiceChoice): Int? {
    if (source == target) return R.string.pair_same
    if (source == "yue-CN" || source == "sh-CN") {
        if (target != "zh" && target != "en") return R.string.pair_dialect
    }
    val zhEn = source == "zh" || source == "en" || target == "zh" || target == "en"
    if (!zhEn) return R.string.pair_zh_en
    if (voice != VoiceChoice.CLONE && target != "zh" && target != "en") {
        return R.string.pair_named_voice
    }
    if (voice == VoiceChoice.CLONE && (source !in liveEight || target !in liveEight)) {
        return R.string.pair_clone
    }
    return null
}

/**
 * 免费链路的语种边界。
 *
 * 实测（2026-11）：百度的 `lan` 与有道的 `le` 只放行中文、英文、日语、韩语、俄语，
 * 其余语种返回 403 / 500。这里作为单点守卫，避免发出注定失败的请求。
 */
val freeTtsLanguages: Set<String> = setOf("zh", "en", "ja", "ko", "ru")

fun freeTtsSupports(code: String): Boolean = code in freeTtsLanguages

/** 免费链路下朗读不支持的语种：返回提示资源，null 表示可用。 */
fun freeSpeakError(code: String): Int? =
    if (freeTtsSupports(code)) null else R.string.free_lang_unsupported

/**
 * 免费同传的语言对校验：先过收费链路的组合规则，再过免费语种边界。
 * 复刻原声在免费链路不可用。
 */
fun freeLivePairError(source: String, target: String, voice: VoiceChoice): Int? {
    if (voice == VoiceChoice.CLONE) return R.string.free_no_clone
    livePairError(source, target, VoiceChoice.FEMALE)?.let { return it }
    if (!freeTtsSupports(source) || !freeTtsSupports(target)) {
        return R.string.free_pair_unsupported
    }
    return null
}
/** 面对面模式里的一句发言：谁说的、原文、译文、语对与时间。 */
data class FaceTurn(
    /** "ME" 或 "OTHER"：判定依据是这一句的语言，而不是谁按的按钮。 */
    val side: String,
    val sourceText: String,
    val targetText: String,
    val from: String,
    val to: String,
    val createdAt: Long,
    /** 这一句还在识别/翻译中（译文未定稿）。 */
    val pending: Boolean = false,
)

/**
 * 面对面双向同传的方向判定。
 *
 * 只做中英自动判定：同传链路本身也只保证「一边是中文或英文」，
 * 其它语种一律按用户当前选的方向走，不猜测。
 */
object FaceDirection {
    const val ME = "ME"
    const val OTHER = "OTHER"

    /** 中文字符（含全角标点里的汉字区）与拉丁字母的粗分界。 */
    private fun isCjk(ch: Char): Boolean = ch.code in 0x4E00..0x9FFF

    private fun isLatin(ch: Char): Boolean = ch in 'a'..'z' || ch in 'A'..'Z'

    /**
     * 依据文本猜语种。
     *
     * 两边按**信息单元**计数：汉字逐字算，拉丁按「词」（连续字母串）算。
     * 若按字母数比，「我去了 Starbucks 买咖啡」会被判成英文——品牌名占了 9 个字母，
     * 而中文只有 6 个字；按词算则是 6 : 1，符合直觉。
     *
     * 两者都为 0（纯数字、标点、空白）时返回 null，让调用方保持当前方向：
     * 一句话里只有「123」不该把画面翻过去。
     */
    fun detect(text: String): String? {
        var cjk = 0
        var words = 0
        var inWord = false
        text.forEach { ch ->
            if (isCjk(ch)) {
                cjk++
                inWord = false
            } else if (isLatin(ch)) {
                if (!inWord) words++
                inWord = true
            } else {
                inWord = false
            }
        }
        if (cjk == 0 && words == 0) return null
        return if (cjk >= words) "zh" else "en"
    }

    /**
     * 这句发言属于哪一侧。
     *
     * [myLang] 是「我方语言」：检测结果等于它就是 ME，否则 OTHER（无信号时归到 OTHER，
     * 因为按住说话的人通常是对方，误判代价更小）。
     */
    fun sideOf(text: String, myLang: String): String {
        val detected = detect(text) ?: return OTHER
        return if (detected == myLang) ME else OTHER
    }

    /**
     * 未锁定时，目标语言就是「另一边」：中文进、英文出，反之亦然。
     * 锁定（[locked]）时保留用户手选的 [manualTarget]。
     */
    fun autoTarget(from: String, locked: Boolean, manualTarget: String): String = when {
        locked -> manualTarget
        from == "zh" -> "en"
        from == "en" -> "zh"
        else -> manualTarget
    }

    /** 方向互换：中英互换，其它语种原样返回（同传不支持时才不会做无意义的翻转）。 */
    fun swap(lang: String): String = when (lang) {
        "zh" -> "en"
        "en" -> "zh"
        else -> lang
    }

    /** 把一句发言并入列表：同一侧连续发言合并成一段，避免每句都建卡片。 */
    fun append(turns: List<FaceTurn>, turn: FaceTurn, max: Int = 60): List<FaceTurn> {
        val last = turns.lastOrNull()
        val merged = if (last != null && last.side == turn.side && last.pending && turn.pending) {
            turns.dropLast(1) + last.copy(
                sourceText = listOf(last.sourceText, turn.sourceText).filter { it.isNotBlank() }.joinToString("\n"),
                targetText = listOf(last.targetText, turn.targetText).filter { it.isNotBlank() }.joinToString("\n"),
            )
        } else {
            turns + turn
        }
        return if (merged.size > max) merged.takeLast(max) else merged
    }
}
