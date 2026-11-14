package com.yuguang.lumo.data.remote

import android.content.Context
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.model.StreamEvent
import com.yuguang.lumo.domain.model.ToolCallRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** DeepSeek 与豆包方舟都使用 OpenAI 兼容的流式 chat/completions。 */
@Singleton
class OpenAiCompatibleClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
) {
    fun stream(
        url: String,
        apiKey: String,
        body: JsonObject,
        headers: Map<String, String> = emptyMap(),
    ): Flow<StreamEvent> = flow {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA))
        // 免密钥车道（免费池 / 本地端点）不发送空的 Authorization。
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        headers.forEach { (name, value) -> builder.header(name, value) }
        val request = builder.build()
        val call = http.newCall(request)
        currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                val payload = response.body
                if (payload == null) {
                    emit(StreamEvent.Failed(context.getString(R.string.empty_server)))
                    return@flow
                }
                if (!response.isSuccessful) {
                    val raw = payload.string()
                    emit(StreamEvent.Failed(errorMessage(response.code, raw), isRetryable(response.code, raw)))
                    return@flow
                }
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("event-stream")) {
                    emitNonStream(payload.string())
                    return@flow
                }
                val source = payload.source()
                var promptTokens: Int? = null
                var completionTokens: Int? = null
                var costUsd: Double? = null
                val toolBuf = sortedMapOf<Int, ToolCallBuffer>()
                while (currentCoroutineContext().isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    for (piece in SseDecoder.decode(line)) {
                        when (piece) {
                            is ChunkPiece.Delta -> emit(StreamEvent.Delta(piece.text))
                            is ChunkPiece.Reasoning -> emit(StreamEvent.Reasoning(piece.text))
                            is ChunkPiece.ToolCallDelta -> {
                                val buf = toolBuf.getOrPut(piece.index) { ToolCallBuffer() }
                                if (!piece.id.isNullOrBlank()) buf.id = piece.id
                                if (!piece.name.isNullOrBlank()) buf.name = piece.name
                                if (!piece.arguments.isNullOrEmpty()) buf.arguments += piece.arguments
                            }
                            is ChunkPiece.Usage -> {
                                promptTokens = piece.promptTokens ?: promptTokens
                                completionTokens = piece.completionTokens ?: completionTokens
                                costUsd = piece.costUsd ?: costUsd
                            }
                            ChunkPiece.Done -> {
                                emitTools(toolBuf)
                                emit(StreamEvent.Completed(promptTokens, completionTokens, costUsd))
                                return@flow
                            }
                            is ChunkPiece.Invalid -> {
                                emit(StreamEvent.Failed(friendlyError(piece.message), piece.retryable))
                                return@flow
                            }
                        }
                    }
                }
                emitTools(toolBuf)
                emit(StreamEvent.Completed(promptTokens, completionTokens, costUsd))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("cancelled", error)
            }
            emit(StreamEvent.Failed(error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.network_failed)))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.emitTools(
        toolBuf: Map<Int, ToolCallBuffer>,
    ) {
        val calls = toolBuf.values.mapNotNull { buf ->
            val name = buf.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ToolCallRequest(
                id = buf.id.orEmpty(),
                name = name,
                argumentsJson = buf.arguments,
            )
        }
        if (calls.isNotEmpty()) emit(StreamEvent.ToolCalls(calls))
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.emitNonStream(raw: String) {
        val text = runCatching {
            json.decodeFromString<ChatChunk>(raw).choices.firstOrNull()?.message?.content
        }.getOrNull()
        if (!text.isNullOrBlank()) {
            emit(StreamEvent.Delta(text))
            emit(StreamEvent.Completed(null, null))
        } else {
            emit(StreamEvent.Failed(errorMessage(200, raw), retryable = isRetryable(200, raw)))
        }
    }

    private fun errorMessage(code: Int, raw: String): String {
        val detail = runCatching {
            json.decodeFromString<ApiErrorBody>(raw).error
        }.getOrNull()
        val parsed = detail?.message
        val codeName = detail?.code.orEmpty()
        if (isMissingModel(code, codeName, parsed.orEmpty())) {
            return context.getString(R.string.model_not_available)
        }
        mapCommonError(code, parsed.orEmpty())?.let { return it }
        return parsed?.takeIf { it.isNotBlank() } ?: context.getString(R.string.request_failed_code, code)
    }

    private fun friendlyError(message: String): String {
        if (isMissingModel(404, "", message)) {
            return context.getString(R.string.model_not_available)
        }
        mapCommonError(0, message)?.let { return it }
        return message.ifBlank { context.getString(R.string.parse_model_failed) }
    }

    private fun friendlyError(message: String, retryable: Boolean): String = friendlyError(message)

    /** 把服务端英文错误归一到能直接指导操作的中文提示。 */
    private fun mapCommonError(httpCode: Int, message: String): String? {
        val blob = message.lowercase()
        return when {
            blob.contains("overdue") || blob.contains("arrear") ||
                blob.contains("insufficient balance") || blob.contains("欠费") ||
                blob.contains("余额不足") ->
                context.getString(R.string.error_account_overdue)
            httpCode == 401 || blob.contains("authentication fails") ||
                blob.contains("invalid api key") || blob.contains("invalid authentication") ->
                context.getString(R.string.error_api_key_invalid)
            httpCode == 403 -> context.getString(R.string.error_forbidden)
            else -> null
        }
    }

    private fun isMissingModel(httpCode: Int, apiCode: String, message: String): Boolean {
        val blob = "$apiCode $message".lowercase()
        return httpCode == 404 ||
            blob.contains("invalidendpointormodel") ||
            blob.contains("model not found") ||
            blob.contains("does not exist") ||
            blob.contains("notfound") ||
            blob.contains("model is unavailable")
    }

    /**
     * 免费车道会降级到下一个模型。
     *
     * 只有「这次请求本身没成功」才值得换模型：限流、模型下线、网关 5xx。
     * 鉴权/余额类错误换模型也没用，直接报错。
     */
    private fun isRetryable(httpCode: Int, raw: String): Boolean {
        if (httpCode == 429) return true
        if (httpCode in 500..599) return true
        if (isMissingModel(httpCode, "", raw)) return true
        val blob = raw.lowercase()
        return blob.contains("rate limit") ||
            blob.contains("quota") ||
            blob.contains("overloaded") ||
            blob.contains("unavailable")
    }

    private data class ToolCallBuffer(
        var id: String? = null,
        var name: String? = null,
        var arguments: String = "",
    )

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
