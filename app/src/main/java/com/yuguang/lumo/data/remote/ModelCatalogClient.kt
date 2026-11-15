package com.yuguang.lumo.data.remote

import com.yuguang.lumo.domain.model.ModelCapabilities
import com.yuguang.lumo.domain.model.ModelChoice
import com.yuguang.lumo.domain.model.ModelGroup
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.freeFallbackChain
import com.yuguang.lumo.domain.model.freeModelBlocklist
import com.yuguang.lumo.domain.model.modelChoices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelCatalogClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) {
    suspend fun fetch(provider: ProviderId, apiKey: String, baseUrl: String): List<ModelChoice> =
        withContext(Dispatchers.IO) {
            // 免费车道与自定义端点不要求 Key：前者公开免密，后者常指向本地网关。
            if (apiKey.isBlank() && provider.requiresKey) return@withContext provider.modelChoices()
            if (provider.defaultBaseUrl.isBlank() && baseUrl.isBlank()) {
                return@withContext provider.modelChoices()
            }
            val request = Request.Builder()
                .url(EndpointUrls.models(baseUrl, provider))
                .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
                .get()
                .build()
            val raw = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext provider.modelChoices()
                response.body?.string().orEmpty()
            }
            val parsed = runCatching {
                json.decodeFromString<ModelsListResponse>(raw)
            }.getOrNull() ?: return@withContext provider.modelChoices()
            when (provider) {
                ProviderId.DEEPSEEK -> mapDeepSeek(parsed.data)
                ProviderId.DOUBAO -> mapArk(parsed.data)
                ProviderId.FREE -> mapKilo(parsed.data)
                ProviderId.CUSTOM -> mapCustom(parsed.data)
            }.ifEmpty { provider.modelChoices() }
        }

    private fun mapDeepSeek(items: List<RemoteModelDto>): List<ModelChoice> =
        items.map { item ->
            val vision = item.resolvedInputModalities.any { it.equals("image", ignoreCase = true) } ||
                ModelCapabilities.inferVision(ProviderId.DEEPSEEK, item.id)
            ModelChoice(
                provider = ProviderId.DEEPSEEK,
                id = item.id,
                label = item.name?.takeIf { it.isNotBlank() } ?: item.id,
                vision = vision,
                imageGen = false,
                groupRes = ModelGroup.OFFICIAL.labelRes,
            )
        }.sortedBy { it.id }

    private fun mapArk(items: List<RemoteModelDto>): List<ModelChoice> {
        val kept = items
            .map { it.id }
            .filter { ModelCapabilities.arkKeep(it) }
            .distinct()
        val latestByFamily = kept
            .groupBy { ModelCapabilities.familyKey(it) }
            .mapValues { (_, ids) ->
                ids.maxWithOrNull(
                    compareBy<String> { ModelCapabilities.versionKey(it) }
                        .thenBy { it },
                )!!
            }
            .values
            .toSet()
        return latestByFamily.map { id ->
            val imageGen = ModelCapabilities.inferImageGen(id)
            val vision = !imageGen && ModelCapabilities.inferVision(ProviderId.DOUBAO, id)
            ModelChoice(
                provider = ProviderId.DOUBAO,
                id = id,
                label = prettyLabel(id),
                vision = vision,
                imageGen = imageGen,
                groupRes = when {
                    imageGen -> ModelGroup.SEEDREAM.labelRes
                    id.startsWith("deepseek", ignoreCase = true) -> ModelGroup.ARK_DEEPSEEK.labelRes
                    id.startsWith("glm", ignoreCase = true) -> ModelGroup.ARK_GLM.labelRes
                    else -> ModelGroup.SEED.labelRes
                },
            )
        }.sortedWith(
            compareBy<ModelChoice> { it.groupRes }
                .thenByDescending { it.imageGen }
                .thenByDescending { it.vision }
                .thenBy { it.label },
        )
    }

    /**
     * Kilo 公共池：只保留 `isFree` 项（老响应没有该字段时用 `:free` 后缀兜底）。
     *
     * `kilo-auto/free` 是自动路由，能力按最宽松处理；其余按上游声明映射。
     */
    private fun mapKilo(items: List<RemoteModelDto>): List<ModelChoice> {
        val free = items.filter { item ->
            val freeTagged = item.free == true || item.id.endsWith(":free") || item.id == "kilo-auto/free"
            // 远端只保证「声明免费」，不保证「现在能调」：实测不可用的进黑名单。
            freeTagged && item.id !in freeModelBlocklist
        }
        return free.map { item ->
            val vision = item.resolvedInputModalities
                .any { it.equals("image", ignoreCase = true) || it.equals("video", ignoreCase = true) }
            ModelChoice(
                provider = ProviderId.FREE,
                id = item.id,
                label = prettyFreeLabel(item),
                vision = vision,
                imageGen = false,
                groupRes = ModelGroup.FREE.labelRes,
                contextWindow = item.contextLength ?: 0,
                maxOutput = item.maxCompletionTokens ?: 0,
                reasoning = item.supportsReasoning(),
            )
        }.sortedWith(
            // 顺序按 freeFallbackChain 的实测优先级；链外的按上下文从大到小。
            compareBy<ModelChoice> { model ->
                freeFallbackChain.indexOf(model.id).let { if (it < 0) freeFallbackChain.size else it }
            }.thenByDescending { it.contextWindow },
        )
    }

    /** 自定义端点：上游怎么列就怎么列，统一放进「自定义端点」分组。 */
    private fun mapCustom(items: List<RemoteModelDto>): List<ModelChoice> =
        items.map { item ->
            ModelChoice(
                provider = ProviderId.CUSTOM,
                id = item.id,
                label = item.name?.takeIf { it.isNotBlank() } ?: item.id,
                vision = item.resolvedInputModalities.any { it.equals("image", ignoreCase = true) },
                imageGen = false,
                groupRes = ModelGroup.CUSTOM.labelRes,
                contextWindow = item.contextLength ?: 0,
                maxOutput = item.maxCompletionTokens ?: 0,
            )
        }.sortedBy { it.id }

    private fun prettyFreeLabel(item: RemoteModelDto): String {
        val raw = item.name?.takeIf { it.isNotBlank() } ?: item.id
        return raw.removeSuffix(" (free)").trim()
    }

    private fun RemoteModelDto.supportsReasoning(): Boolean =
        supportedParameters.orEmpty().any { it.equals("reasoning", ignoreCase = true) } ||
            reasoning != null

    private fun prettyLabel(id: String): String {
        val withoutDate = id.replace(Regex("""-\d{6}$"""), "")
        return withoutDate
            .removePrefix("doubao-")
            .replace('-', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

@Serializable
private data class ModelsListResponse(
    val data: List<RemoteModelDto> = emptyList(),
)

@Serializable
private data class RemoteModelDto(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    @SerialName("input_modalities") val inputModalities: List<String>? = null,
    @SerialName("output_modalities") val outputModalities: List<String>? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("isFree") val free: Boolean? = null,
    @SerialName("supported_parameters") val supportedParameters: List<String>? = null,
    val reasoning: JsonElement? = null,
    @SerialName("top_provider") val topProvider: TopProviderDto? = null,
    val architecture: ArchitectureDto? = null,
) {
    /** 兼容两种放置位置：老响应在顶层，新响应在 top_provider；模态也可能在 architecture 里。 */
    val maxCompletionTokens: Int?
        get() = topProvider?.maxCompletionTokens

    val resolvedInputModalities: List<String>
        get() = inputModalities ?: architecture?.inputModalities.orEmpty()
}

@Serializable
private data class TopProviderDto(
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
)

@Serializable
private data class ArchitectureDto(
    @SerialName("input_modalities") val inputModalities: List<String>? = null,
)
