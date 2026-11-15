package com.yuguang.lumo.domain.model

import androidx.annotation.StringRes
import com.yuguang.lumo.R

/** 目录中的一个模型。id 发给接口，label 给用户看，groupRes 用于选择器分组。 */
data class ModelOption(
    val id: String,
    val label: String,
    @StringRes val groupRes: Int,
    val vision: Boolean = false,
    val imageGen: Boolean = false,
    val contextWindow: Int = 0,
    val maxOutput: Int = 0,
    val reasoning: Boolean = false,
)

/** 对话服务商。豆包目录里的 DeepSeek、GLM 走方舟地址和方舟密钥，与官方 DeepSeek 分开。 */
enum class ProviderId(
    @StringRes val nameRes: Int,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val models: List<ModelOption>,
    /** 免密钥车道：不校验 Key，也不发送 Authorization。 */
    val requiresKey: Boolean = true,
) {
    DEEPSEEK(
        nameRes = R.string.provider_deepseek,
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-flash",
        models = listOf(
            ModelOption("deepseek-flash", "Flash", R.string.group_official, vision = true),
            ModelOption("deepseek-v4-pro", "V4 Pro", R.string.group_official),
        ),
    ),
    DOUBAO(
        nameRes = R.string.provider_doubao,
        defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        defaultModel = "doubao-seed-2-1-lite-260915",
        models = listOf(
            ModelOption("doubao-seed-2-1-lite-260915", "Seed 2.1 Lite", R.string.group_seed, vision = true),
            ModelOption("doubao-seed-evolving", "Seed Evolving", R.string.group_seed, vision = true),
            ModelOption("doubao-seed-2-1-turbo-260628", "Seed 2.1 Turbo", R.string.group_seed, vision = true),
            ModelOption("doubao-seed-2-0-mini-260428", "Seed 2.0 Mini", R.string.group_seed, vision = true),
            ModelOption("doubao-seed-2-0-lite-260428", "Seed 2.0 Lite", R.string.group_seed, vision = true),
            ModelOption("doubao-seedream-5-0-flash-260915", "Seedream 5.0 Flash", R.string.group_seedream, imageGen = true),
            ModelOption("deepseek-v4-1-flash-260910", "DeepSeek V4.1 Flash", R.string.group_ark_deepseek),
            ModelOption("deepseek-v4-pro-ga-260813", "DeepSeek V4 Pro", R.string.group_ark_deepseek),
            ModelOption("deepseek-v4-flash-ga-260731", "DeepSeek V4 Flash", R.string.group_ark_deepseek),
            ModelOption("glm-5-3-flash-260828", "GLM 5.3 Flash", R.string.group_ark_glm),
            ModelOption("glm-5-2-260617", "GLM 5.2", R.string.group_ark_glm),
        ),
    ),

    /**
     * 免费车道：Kilo 公共网关的免费池，无需任何密钥。
     *
     * 清单会被远端目录覆盖；这里的静态项是断网 / 未刷新时的兜底。
     * 顺序即「首选 → 兜底」：先给实测最快最稳的模型，再按能力补位，
     * 自动路由 kilo-auto 放在最后——它 2026-11 起对匿名用户长期 429。
     *
     * 2026-11-15 逐个实测后剔除的模型（见 docs/FREE-MODELS.md）：
     * - inclusionai/ling-3.2-flash:free：整日额度 limit_rpd，连续测试 0/N 通过；
     * - baidu/ernie-5.5-flash-preview:free：强制思考，App 默认关思考时直接 400；
     * - liquid/lfm-3-1.7b:free：同上，且 1.7B 中文质量不足；
     * - nvidia/nemotron-3-ultra-550b-a55b:free：503 居多，偶尔首字 60 秒以上。
     */
    FREE(
        nameRes = R.string.provider_free,
        defaultBaseUrl = "https://ai-gateway.kilo.ai/api/v1",
        defaultModel = "nvidia/nemotron-3.5-lightning:free",
        models = listOf(
            ModelOption("nvidia/nemotron-3.5-lightning:free", "Nemotron 3.5 Lightning", R.string.group_free, contextWindow = 1_000_000, maxOutput = 65_536, reasoning = true),
            ModelOption("nvidia/nemotron-3.5-super-vl:free", "Nemotron 3.5 Super VL", R.string.group_free, vision = true, contextWindow = 1_000_000, maxOutput = 131_072, reasoning = true),
            ModelOption("baidu/ernie-5.5-flash-preview:free", "ERNIE 5.5 Flash Preview", R.string.group_free, contextWindow = 1_024_000, maxOutput = 921_600, reasoning = true),
            ModelOption("dots-studio/dots-3-note-preview:free", "Dots3 Note Preview", R.string.group_free, vision = true, contextWindow = 512_000, maxOutput = 460_800, reasoning = true),
            ModelOption("particle-ai/particle-1:free", "Particle-1", R.string.group_free, vision = true, contextWindow = 1_048_576, maxOutput = 131_072, reasoning = true),
            ModelOption("reflection/beam-free", "Beam 1.0", R.string.group_free, contextWindow = 1_048_576, maxOutput = 131_072, reasoning = true),
            ModelOption("nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", "Nemotron 3 Nano Omni", R.string.group_free, vision = true, contextWindow = 256_000, maxOutput = 65_536, reasoning = true),
            ModelOption("stealth/glyph-cluster", "Glyph Cluster", R.string.group_free, contextWindow = 256_000, maxOutput = 256_000, reasoning = true),
            ModelOption("liquid/lfm-3-1.7b:free", "LFM3 1.7B", R.string.group_free, contextWindow = 262_144, maxOutput = 8_192, reasoning = true),
            ModelOption("cohere/north-mini-code:free", "North Mini Code", R.string.group_free, contextWindow = 256_000, maxOutput = 64_000, reasoning = true),
            ModelOption("openrouter/free", "OpenRouter Free Router", R.string.group_free, vision = true, contextWindow = 200_000, maxOutput = 32_768, reasoning = true),
            ModelOption("kilo-auto/free", "Auto (free)", R.string.group_free, vision = true, contextWindow = 256_000, maxOutput = 32_768, reasoning = true),
        ),
        requiresKey = false,
    ),

    /** 用户自填的 OpenAI 兼容端点，可指向局域网里的转发网关。 */
    CUSTOM(
        nameRes = R.string.provider_custom,
        defaultBaseUrl = "",
        defaultModel = "",
        models = emptyList(),
        requiresKey = false,
    ),
    ;

    /** 免密钥车道里，Key 不是能力开关。 */
    val keyOptional: Boolean get() = !requiresKey
}

/**
 * 免费车道的降级顺序：正文出来之前失败才切换。
 *
 * 2026-11 实测：前三个首字都在 1 秒内且连续 3 轮全通；kilo-auto 放最后，
 * 它现在多半直接 429，留在链尾只是为了上游恢复后能自动吃到。
 */
val freeFallbackChain: List<String> = listOf(
    "nvidia/nemotron-3.5-lightning:free",
    "nvidia/nemotron-3.5-super-vl:free",
    "baidu/ernie-5.5-flash-preview:free",
    "particle-ai/particle-1:free",
    "reflection/beam-free",
    "kilo-auto/free",
)

/** 免费车道的首选翻译模型顺序（逐句直译质量实测均可用）。 */
val freeTranslateChain: List<String> = listOf(
    "nvidia/nemotron-3.5-lightning:free",
    "dots-studio/dots-3-note-preview:free",
    "particle-ai/particle-1:free",
)

/**
 * 免费车道的视觉模型顺序。
 *
 * 必须与 [freeFallbackChain] 分开：文本模型收到图片会直接 400/404，
 * 图片翻译拿文本链当兜底只会把失败拖长。
 */
val freeVisionChain: List<String> = listOf(
    "nvidia/nemotron-3.5-super-vl:free",
    "dots-studio/dots-3-note-preview:free",
    "particle-ai/particle-1:free",
    "openrouter/free",
)

/** 免费车道里唯一支持音频输入的模型（实测可转写，准确率一般）。 */
const val FREE_AUDIO_MODEL = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"

/**
 * 实测不可用的免费模型：即使远端目录里还列着，也不放进选择器。
 *
 * 远端目录只能说明「上游声明它免费」，不说明「现在真的能调」——
 * 这份名单是 2026-11-15 逐个发请求验证出来的，理由见 docs/FREE-MODELS.md。
 */
val freeModelBlocklist: Set<String> = setOf(
    // 每日额度 limit_rpd：每天必然 429，连续测试 0/3 通过。
    "inclusionai/ling-3.2-flash:free",
    // 偶发 60 秒首字、503 居多，体验不可接受。
    "nvidia/nemotron-3-ultra-550b-a55b:free",
)

/**
 * 强制思考的免费模型：不能下发 reasoning.enabled=false，否则 400「Reasoning is mandatory」。
 *
 * 对这些模型直接省略该字段（而不是先撞一次 400 再重试），省掉一轮往返；
 * 代价是它们总会带上一小段思考过程。
 */
val freeForcedReasoning: Set<String> = setOf(
    "baidu/ernie-5.5-flash-preview:free",
    "liquid/lfm-3-1.7b:free",
    // 实测（2026-11-15 真机自检）：传 enabled=false 报 400，省略字段则正常。
    "openrouter/free",
)

data class ModelChoice(
    val provider: ProviderId,
    val id: String,
    val label: String,
    val vision: Boolean = false,
    val imageGen: Boolean = false,
    @StringRes val groupRes: Int = R.string.group_official,
    val contextWindow: Int = 0,
    val maxOutput: Int = 0,
    val reasoning: Boolean = false,
)

fun ProviderId.modelChoices(): List<ModelChoice> =
    models.map { option ->
        ModelChoice(
            provider = this,
            id = option.id,
            label = option.label,
            vision = option.vision,
            imageGen = option.imageGen,
            groupRes = option.groupRes,
            contextWindow = option.contextWindow,
            maxOutput = option.maxOutput,
            reasoning = option.reasoning,
        )
    }

/** 目录里没有的旧 ID 由界面显示为「自定义」，ID 本身仍保留。 */
fun ProviderId.knownModelLabel(id: String): String? =
    models.firstOrNull { it.id == id }?.label

fun ProviderId.groupedModels(): List<Pair<Int, List<ModelOption>>> =
    models.groupBy { it.groupRes }.entries.map { it.key to it.value }

fun ProviderId.staticSupportsVision(modelId: String): Boolean =
    models.firstOrNull { it.id == modelId }?.vision == true ||
        ModelCapabilities.inferVision(this, modelId)

fun ProviderId.defaultImageGenModel(): String =
    models.firstOrNull { it.imageGen }?.id ?: "doubao-seedream-5-0-flash-260915"

/** 新建对话时使用的服务商和模型：优先上次成功发送，否则用已配置密钥的默认模型。 */
data class SelectedModel(
    val provider: ProviderId,
    val model: String,
)

enum class Role {
    SYSTEM,
    USER,
    ASSISTANT,
    /** 工具结果回填轮次，只在一次请求内出现，不落库。 */
    TOOL,
}

enum class MessageStatus {
    STREAMING,
    DONE,
    FAILED,
}

enum class AttachmentSource {
    USER,
    GENERATED,
}

data class MessageAttachment(
    val id: Long,
    val messageId: Long,
    val localPath: String,
    val mime: String,
    val source: AttachmentSource,
    val remoteUrl: String = "",
    val sortOrder: Int = 0,
)

data class Conversation(
    val id: Long,
    val title: String,
    val provider: ProviderId,
    val model: String,
    val systemPrompt: String,
    val updatedAt: Long,
    val pinned: Boolean = false,
    /** 是否请求深度思考；免费车道默认关闭。 */
    val thinking: Boolean = false,
    /** 最后一条消息预览，列表页用。 */
    val preview: String? = null,
    /** 会话累计 token，列表页副标题用；0 表示没有数据。 */
    val totalTokens: Int = 0,
    /** 当前激活分支的叶子消息；0 表示未初始化，按全量线性兜底。 */
    val leafMessageId: Long = 0,
)

data class ChatMessage(
    val id: Long,
    val conversationId: Long,
    val role: Role,
    val content: String,
    val reasoning: String,
    val status: MessageStatus,
    val error: String,
    val createdAt: Long,
    val attachments: List<MessageAttachment> = emptyList(),
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    /** 工具调用轨迹（JSON 行），界面折叠展示。 */
    val toolTrace: String = "",
    /** 消息树的父节点；0 表示一条链的起点。 */
    val parentId: Long = 0,
)

/** 发给接口的消息。图片用 data URL，由客户端从本地文件编码。 */
data class WireMessage(
    val role: Role,
    val text: String,
    val imageDataUrls: List<String> = emptyList(),
    /** assistant 轮次里模型请求的工具调用。 */
    val toolCalls: List<ToolCallRequest> = emptyList(),
    /** tool 轮次对应的调用 id 与工具名。 */
    val toolCallId: String = "",
    val toolName: String = "",
)

data class ChatRequest(
    val model: String,
    val messages: List<WireMessage>,
    /** 豆包对话可挂 generate_image 工具，由客户端执行 Seedream。 */
    val enableImageTool: Boolean = false,
    /** 是否请求思考过程。免费车道据此下发 reasoning.enabled。 */
    val thinking: Boolean = false,
    /** 正文出来之前允许降级的候选模型（免费车道用）。 */
    val fallbackModels: List<String> = emptyList(),
    /** 单次输出上限；0 表示不显式下发。 */
    val maxOutputTokens: Int = 0,
    /** 本轮可用的工具 schema（OpenAI tools 数组元素）；空表示不声明工具。 */
    val toolSchemas: List<kotlinx.serialization.json.JsonObject> = emptyList(),
)

data class ProviderConfig(
    val provider: ProviderId,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val speechApiKey: String = "",
    /**
     * 旧版控制台的语音应用凭证。
     * 与 [speechAccessToken] 同时非空时，ASR/TTS/AST/实时语音一律优先用 AppId+Token；
     * 否则使用 [speechApiKey]（X-Api-Key）。
     */
    val speechAppId: String = "",
    val speechAccessToken: String = "",
)

data class ToolCallRequest(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
    data class ToolCalls(val calls: List<ToolCallRequest>) : StreamEvent
    data class Completed(
        val promptTokens: Int?,
        val completionTokens: Int?,
        val costUsd: Double? = null,
    ) : StreamEvent
    data class Failed(val message: String, val retryable: Boolean = false) : StreamEvent
}

/** 从模型 ID / 远程字段推断识图、生图能力。 */
object ModelCapabilities {
    fun inferVision(provider: ProviderId, modelId: String): Boolean {
        val id = modelId.lowercase()
        if (id.contains("seedream")) return false
        return when (provider) {
            ProviderId.DEEPSEEK -> id.contains("flash") && !id.contains("pro")
            ProviderId.DOUBAO ->
                id.contains("seed-2") ||
                    id.contains("seed-evolving") ||
                    id.contains("vision") ||
                    id.contains("seed-1-6") ||
                    id.contains("seed-1-8")
            ProviderId.FREE ->
                id.contains("vl") ||
                    id.contains("omni") ||
                    id.contains("vision") ||
                    id.contains("particle") ||
                    id.contains("dots") ||
                    id == "kilo-auto/free" ||
                    id == "openrouter/free"
            ProviderId.CUSTOM -> false
        }
    }

    fun inferImageGen(modelId: String): Boolean =
        modelId.lowercase().contains("seedream")

    fun arkKeep(modelId: String): Boolean {
        val id = modelId.lowercase()
        return id.startsWith("doubao-seed-") ||
            id.startsWith("doubao-seedream-") ||
            id.startsWith("deepseek-v4") ||
            id.startsWith("glm-5")
    }

    /** 同系列多版本取日期后缀更大的；无日期的单独保留。 */
    fun versionKey(modelId: String): Long {
        val match = Regex("""(\d{6})$""").find(modelId) ?: return -1L
        return match.groupValues[1].toLongOrNull() ?: -1L
    }

    fun familyKey(modelId: String): String =
        modelId.replace(Regex("""-\d{6}$"""), "")
}
