package com.yuguang.lumo.data

import android.content.Context
import android.net.Uri
import com.yuguang.lumo.R
import com.yuguang.lumo.data.local.ChatMediaStore
import com.yuguang.lumo.domain.LlmProvider
import com.yuguang.lumo.domain.ModelCatalogRepository
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.ChatRequest
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.domain.model.SpeechTier
import com.yuguang.lumo.domain.model.StreamEvent
import com.yuguang.lumo.domain.model.WireMessage
import com.yuguang.lumo.domain.model.freeVisionChain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 一次图片翻译的结果。model 会展示在界面上，方便用户判断可信度。 */
data class PhotoTranslation(
    val lines: List<PhotoLine>,
    val model: String,
    val elapsedMs: Long,
    val fallback: Boolean,
) {
    val sourceText: String get() = lines.map { it.source }.filter { it.isNotBlank() }.joinToString("\n")
    val targetText: String get() = lines.map { it.target }.filter { it.isNotBlank() }.joinToString("\n")
}

/**
 * 相册图片的文字翻译。
 *
 * 只用一次视觉模型请求：图片 + 「逐行识别并翻译，只返回 JSON」的指令。
 * 选模型遵循档位：免费档走 Kilo 公共池的视觉模型，收费档优先豆包、其次 DeepSeek。
 * 失败原因一律返回中文可读文本，由界面直接展示。
 */
@Singleton
class PhotoTranslator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStore: ChatMediaStore,
    private val settingsRepository: SettingsRepository,
    private val modelCatalog: ModelCatalogRepository,
    private val providers: Map<ProviderId, @JvmSuppressWildcards LlmProvider>,
    private val json: Json,
) {
    suspend fun translate(
        uri: Uri,
        targetLanguage: String,
        languageLabel: String,
    ): Result<PhotoTranslation> = try {
        Result.success(translateOnce(uri, languageLabel))
    } catch (cancelled: CancellationException) {
        // 用户切页/取消时要让协程正常结束，不能包装成「翻译失败」弹给用户看。
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun translateOnce(uri: Uri, languageLabel: String): PhotoTranslation {
        val image = mediaStore.importUserImage(uri)
            ?: error(context.getString(R.string.photo_image_missing))
        try {
            val pick = pickModel()
            val dataUrl = mediaStore.toDataUrl(image.path, image.mime)
                ?: error(context.getString(R.string.photo_image_missing))
            val started = System.currentTimeMillis()
            val raw = request(pick, dataUrl, languageLabel)
            val parsed = PhotoLines.parse(raw, json)
            if (parsed.lines.isEmpty()) error(context.getString(R.string.photo_translate_failed))
            return PhotoTranslation(
                lines = parsed.lines,
                model = pick.model,
                elapsedMs = System.currentTimeMillis() - started,
                fallback = parsed.fallback,
            )
        } finally {
            mediaStore.deletePaths(listOf(image.path))
        }
    }

    /** 挑一个视觉模型：免费档用公共池，收费档按豆包 → DeepSeek 顺序。 */
    private suspend fun pickModel(): Pick {
        val speech = settingsRepository.speech()
        val freeLane = speech.tier == SpeechTier.FREE
        // 免费档与「没有填写任何收费 Key」时都走免费池，避免让用户卡在申请 Key 上。
        val doubao = settingsRepository.get(ProviderId.DOUBAO)
        val deepseek = settingsRepository.get(ProviderId.DEEPSEEK)
        if (!freeLane && doubao.apiKey.isNotBlank()) {
            val fallback = doubao.model.ifBlank { ProviderId.DOUBAO.defaultModel }
            val chosen = pickVision(ProviderId.DOUBAO, fallback)
            return Pick(ProviderId.DOUBAO, chosen, doubao.apiKey, doubao.baseUrl)
        }
        if (!freeLane && deepseek.apiKey.isNotBlank()) {
            val fallback = deepseek.model.ifBlank { ProviderId.DEEPSEEK.defaultModel }
            val chosen = pickVision(ProviderId.DEEPSEEK, fallback)
            return Pick(ProviderId.DEEPSEEK, chosen, deepseek.apiKey, deepseek.baseUrl)
        }
        val free = settingsRepository.get(ProviderId.FREE)
        // 视觉模型单独一条链：文本模型收到图片会 400/404，用它兜底只会把失败拖长。
        val preferred = freeVisionChain.firstOrNull().orEmpty().ifBlank { FREE_VISION_MODEL }
        return Pick(ProviderId.FREE, preferred, free.apiKey, free.baseUrl)
    }

    private fun pickVision(provider: ProviderId, fallback: String): String =
        if (modelCatalog.supportsVision(provider, fallback)) fallback
        else provider.models.firstOrNull { it.vision }?.id ?: fallback

    private suspend fun request(
        pick: Pick,
        dataUrl: String,
        languageLabel: String,
    ): String {
        val llm = providers[pick.provider] ?: error(context.getString(R.string.photo_translate_failed))
        val wire = listOf(
            WireMessage(Role.SYSTEM, context.getString(R.string.photo_prompt_system)),
            WireMessage(
                Role.USER,
                context.getString(R.string.photo_prompt_user, languageLabel),
                imageDataUrls = listOf(dataUrl),
            ),
        )
        val sink = StringBuilder()
        val collected = withTimeoutOrNull(TIMEOUT_MS) {
            llm.stream(
                apiKey = pick.apiKey,
                baseUrl = pick.baseUrl,
                request = ChatRequest(
                    model = pick.model,
                    messages = wire,
                    thinking = false,
                    fallbackModels = if (pick.provider == ProviderId.FREE) {
                        freeVisionChain.filter { it != pick.model }
                    } else {
                        emptyList()
                    },
                    maxOutputTokens = MAX_OUTPUT,
                ),
            ).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> sink.append(event.text)
                    // 上游报错就整次失败：留下半段 JSON 只会让解析更难读懂。
                    is StreamEvent.Failed -> error(event.message)
                    else -> Unit
                }
            }
            sink.toString()
        } ?: error(context.getString(R.string.photo_translate_failed))
        if (collected.isBlank()) error(context.getString(R.string.photo_translate_failed))
        return collected
    }

    private data class Pick(
        val provider: ProviderId,
        val model: String,
        val apiKey: String,
        val baseUrl: String,
    )

    private companion object {
        const val FREE_VISION_MODEL = "nvidia/nemotron-3.5-super-vl:free"
        const val MAX_OUTPUT = 2000
        const val TIMEOUT_MS = 45_000L
    }
}
