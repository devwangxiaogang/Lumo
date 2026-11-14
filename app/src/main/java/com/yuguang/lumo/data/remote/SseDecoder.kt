package com.yuguang.lumo.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface ChunkPiece {
    data class Delta(val text: String) : ChunkPiece
    data class Reasoning(val text: String) : ChunkPiece
    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val arguments: String?,
    ) : ChunkPiece
    data class Usage(
        val promptTokens: Int?,
        val completionTokens: Int?,
        val costUsd: Double? = null,
    ) : ChunkPiece
    data object Done : ChunkPiece
    data class Invalid(val message: String, val retryable: Boolean = false) : ChunkPiece
}

/** 解析 text/event-stream 的 data 行，兼容正文增量、思考内容、工具调用和用量。 */
object SseDecoder {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(line: String): List<ChunkPiece> {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return emptyList()
        val data = trimmed.removePrefix("data:").trim()
        if (data == "[DONE]") return listOf(ChunkPiece.Done)
        if (data.isEmpty()) return emptyList()
        val chunk = try {
            json.decodeFromString<ChatChunk>(data)
        } catch (error: Exception) {
            return listOf(ChunkPiece.Invalid(error.message.orEmpty()))
        }
        val apiError = chunk.error?.message
        if (!apiError.isNullOrBlank()) {
            return listOf(ChunkPiece.Invalid(apiError, isRetryableError(apiError)))
        }
        val pieces = mutableListOf<ChunkPiece>()
        val choice = chunk.choices.firstOrNull()
        val delta = choice?.delta
        val content = delta?.content ?: choice?.message?.content
        if (!content.isNullOrEmpty()) pieces += ChunkPiece.Delta(content)
        // 不同网关对思考字段的命名不一致：豆包/DeepSeek 用 reasoning_content，
        // Kilo 等 OpenAI 兼容网关用 reasoning 或 reasoning_details[]。
        val reasoning = delta?.thinkingText() ?: choice?.message?.thinkingText()
        if (!reasoning.isNullOrEmpty()) pieces += ChunkPiece.Reasoning(reasoning)
        delta?.toolCalls.orEmpty().forEach { call ->
            pieces += ChunkPiece.ToolCallDelta(
                index = call.index,
                id = call.id,
                name = call.function?.name,
                arguments = call.function?.arguments,
            )
        }
        chunk.usage?.let { usage ->
            pieces += ChunkPiece.Usage(usage.promptTokens, usage.completionTokens, usage.cost)
        }
        return pieces
    }

    /** 换一个模型可能就通了的错误：限流、过载、模型下线。 */
    private fun isRetryableError(message: String): Boolean {
        val blob = message.lowercase()
        return blob.contains("rate limit") ||
            blob.contains("too many requests") ||
            blob.contains("quota") ||
            blob.contains("overloaded") ||
            blob.contains("unavailable") ||
            blob.contains("model not found") ||
            blob.contains("no available channel") ||
            blob.contains("try again")
    }
}

/**
 * 思考内容的三字段归一，同一段只取一次。
 *
 * 优先级：`reasoning_content`（豆包/DeepSeek）→ `reasoning`（Kilo）→ `reasoning_details[].text`。
 */
private fun DeltaDto.thinkingText(): String? = firstNonBlank(
    reasoningContent,
    reasoning,
    reasoningDetails.joinToString("") { it.text.orEmpty() },
)

private fun MessageDto.thinkingText(): String? = firstNonBlank(
    reasoningContent,
    reasoning,
    reasoningDetails.joinToString("") { it.text.orEmpty() },
)

private fun firstNonBlank(vararg values: String?): String? =
    values.firstOrNull { !it.isNullOrBlank() }

@Serializable
data class ChatChunk(
    val choices: List<ChoiceDto> = emptyList(),
    val usage: UsageDto? = null,
    val error: ApiErrorDetail? = null,
)

@Serializable
data class ChoiceDto(
    val delta: DeltaDto? = null,
    val message: MessageDto? = null,
)

@Serializable
data class DeltaDto(
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val reasoning: String? = null,
    @SerialName("reasoning_details") val reasoningDetails: List<ReasoningDetailDto> = emptyList(),
    @SerialName("tool_calls") val toolCalls: List<ToolCallDeltaDto> = emptyList(),
)

@Serializable
data class ReasoningDetailDto(
    val type: String? = null,
    val text: String? = null,
)

@Serializable
data class ToolCallDeltaDto(
    val index: Int = 0,
    val id: String? = null,
    val function: ToolFunctionDeltaDto? = null,
)

@Serializable
data class ToolFunctionDeltaDto(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class MessageDto(
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val reasoning: String? = null,
    @SerialName("reasoning_details") val reasoningDetails: List<ReasoningDetailDto> = emptyList(),
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    val cost: Double? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    @SerialName("completion_tokens_details") val completionDetails: CompletionTokensDetailsDto? = null,
)

@Serializable
data class CompletionTokensDetailsDto(
    @SerialName("reasoning_tokens") val reasoningTokens: Int? = null,
)

@Serializable
data class ApiErrorBody(
    val error: ApiErrorDetail? = null,
)

@Serializable
data class ApiErrorDetail(
    val message: String? = null,
    val code: String? = null,
)
