package com.yuguang.lumo.data.remote

import android.content.Context
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.LlmProvider
import com.yuguang.lumo.domain.model.ChatRequest
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.domain.model.StreamEvent
import com.yuguang.lumo.domain.model.WireMessage
import com.yuguang.lumo.domain.model.freeFallbackChain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepSeekProvider @Inject constructor(
    private val client: OpenAiCompatibleClient,
) : LlmProvider {
    override val id: ProviderId = ProviderId.DEEPSEEK

    override fun stream(apiKey: String, baseUrl: String, request: ChatRequest): Flow<StreamEvent> {
        return client.stream(
            url = EndpointUrls.chatCompletions(baseUrl, id),
            apiKey = apiKey,
            body = chatBody(request),
        )
    }
}

/**
 * 免费车道：Kilo 公共网关。
 *
 * 无需密钥，请求头只带归属信息（文档标为可选，去掉也可用）。
 * 降级链在正文增量出现之前才切换模型——思考增量不算正文，
 * 因此「想了很久才撞上限」正是最值得降级的场景。
 */
@Singleton
class FreeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OpenAiCompatibleClient,
) : LlmProvider {
    override val id: ProviderId = ProviderId.FREE

    override fun stream(apiKey: String, baseUrl: String, request: ChatRequest): Flow<StreamEvent> = flow {
        val chain = buildList {
            add(request.model)
            addAll(request.fallbackModels)
            addAll(freeFallbackChain)
        }.filter { it.isNotBlank() }.distinct()

        var lastFailure: StreamEvent.Failed? = null
        for ((index, model) in chain.withIndex()) {
            val outcome = Attempt()
            client.stream(
                url = EndpointUrls.chatCompletions(baseUrl, id),
                apiKey = apiKey,
                body = freeChatBody(request.copy(model = model)),
                headers = ATTRIBUTION,
            ).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        outcome.body = true
                        emit(event)
                    }
                    is StreamEvent.Reasoning -> emit(event)
                    is StreamEvent.ToolCalls -> emit(event)
                    is StreamEvent.Completed -> {
                        outcome.done = true
                        emit(event)
                    }
                    is StreamEvent.Failed -> {
                        lastFailure = event
                        // 已经出过正文，或错误与模型无关，就把失败交给上层。
                        if (outcome.body || !event.retryable) {
                            outcome.done = true
                            emit(event)
                        }
                    }
                }
            }
            if (outcome.done) return@flow
            if (index == chain.lastIndex) {
                emit(lastFailure ?: StreamEvent.Failed(context.getString(R.string.free_chat_failed)))
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    /** 单次尝试的结果：是否已产出正文、是否需要停止整条流。 */
    private class Attempt {
        var body = false
        var done = false
    }

    private fun freeChatBody(request: ChatRequest): JsonObject = buildJsonObject {
        put("model", request.model)
        put("stream", true)
        putJsonObject("stream_options") { put("include_usage", true) }
        // 免费池的思考默认开启，且思考与正文共享输出额度：
        // 不显式关闭或限制上限时，短问题也可能整轮只出思考。
        putJsonObject("reasoning") { put("enabled", request.thinking) }
        if (request.maxOutputTokens > 0) {
            put("max_completion_tokens", request.maxOutputTokens)
        }
        putJsonArray("messages") {
            request.messages.forEach { message ->
                addJsonObject {
                    put("role", message.role.wireName())
                    putWireContent(message)
                }
            }
        }
    }

    private companion object {
        /** 上游按接入方统计用量，带上归属信息是礼貌的默认值。 */
        val ATTRIBUTION = mapOf(
            "HTTP-Referer" to "https://kilo.ai/",
            "X-Title" to "Lumo",
        )
    }
}

/** 用户自填的 OpenAI 兼容端点；Key 可选（本地网关通常不需要）。 */
@Singleton
class CustomProvider @Inject constructor(
    private val client: OpenAiCompatibleClient,
) : LlmProvider {
    override val id: ProviderId = ProviderId.CUSTOM

    override fun stream(apiKey: String, baseUrl: String, request: ChatRequest): Flow<StreamEvent> {
        return client.stream(
            url = EndpointUrls.chatCompletions(baseUrl, id),
            apiKey = apiKey,
            body = chatBody(request),
        )
    }
}

/** 方舟在通用流式请求上额外要求返回 token 用量。 */
@Singleton
class DoubaoProvider @Inject constructor(
    private val client: OpenAiCompatibleClient,
) : LlmProvider {
    override val id: ProviderId = ProviderId.DOUBAO

    override fun stream(apiKey: String, baseUrl: String, request: ChatRequest): Flow<StreamEvent> {
        val body = buildJsonObject {
            chatBody(request).forEach { (key, value) -> put(key, value) }
            putJsonObject("stream_options") {
                put("include_usage", true)
            }
            if (request.enableImageTool) {
                putJsonArray("tools") {
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", GENERATE_IMAGE_TOOL)
                            put("description", "Generate an image from a text prompt. Use when the user asks to draw, paint, or create a picture.")
                            putJsonObject("parameters") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("prompt") {
                                        put("type", "string")
                                        put("description", "Detailed image generation prompt")
                                    }
                                }
                                putJsonArray("required") { add(JsonPrimitive("prompt")) }
                            }
                        }
                    }
                }
            }
        }
        return client.stream(
            url = EndpointUrls.chatCompletions(baseUrl, id),
            apiKey = apiKey,
            body = body,
        )
    }

    companion object {
        const val GENERATE_IMAGE_TOOL = "generate_image"
    }
}

internal fun chatBody(request: ChatRequest): JsonObject = buildJsonObject {
    put("model", request.model)
    put("stream", true)
    if (request.maxOutputTokens > 0) {
        put("max_tokens", request.maxOutputTokens)
    }
    putJsonArray("messages") {
        request.messages.forEach { message ->
            addJsonObject {
                put("role", message.role.wireName())
                putWireContent(message)
                putToolCallFields(message)
            }
        }
    }
    putTools(request)
}

/** 工具轮次：assistant 声明 tool_calls，tool 回填 tool_call_id。 */
private fun kotlinx.serialization.json.JsonObjectBuilder.putToolCallFields(message: WireMessage) {
    if (message.toolCalls.isNotEmpty()) {
        putJsonArray("tool_calls") {
            message.toolCalls.forEach { call ->
                addJsonObject {
                    put("id", call.id)
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", call.name)
                        put("arguments", call.argumentsJson.ifBlank { "{}" })
                    }
                }
            }
        }
    }
    if (message.toolCallId.isNotBlank()) {
        put("tool_call_id", message.toolCallId)
        if (message.toolName.isNotBlank()) put("name", message.toolName)
    }
}

/** 声明可用工具。 */
internal fun kotlinx.serialization.json.JsonObjectBuilder.putTools(request: ChatRequest) {
    if (request.toolSchemas.isEmpty()) return
    putJsonArray("tools") {
        request.toolSchemas.forEach { schema -> add(schema) }
    }
}

/** 把 ToolSpec 包成 OpenAI tools 的元素形态。 */
fun toolSchemaOf(name: String, description: String, parameters: JsonObject): JsonObject =
    buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", name)
            put("description", description)
            put("parameters", parameters)
        }
    }

private fun kotlinx.serialization.json.JsonObjectBuilder.putWireContent(message: WireMessage) {
    if (message.imageDataUrls.isEmpty()) {
        put("content", message.text)
        return
    }
    putJsonArray("content") {
        val text = message.text.ifBlank { DEFAULT_IMAGE_PROMPT }
        addJsonObject {
            put("type", "text")
            put("text", text)
        }
        message.imageDataUrls.forEach { url ->
            addJsonObject {
                put("type", "image_url")
                putJsonObject("image_url") {
                    put("url", url)
                }
            }
        }
    }
}

private fun Role.wireName(): String = when (this) {
    Role.SYSTEM -> "system"
    Role.USER -> "user"
    Role.ASSISTANT -> "assistant"
    Role.TOOL -> "tool"
}
private const val DEFAULT_IMAGE_PROMPT = "请描述这张图片。"
