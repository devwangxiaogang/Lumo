package com.yuguang.lumo.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 一条失败记录：哪个模型、什么时候、因为什么。 */
data class ModelFailure(
    val model: String,
    val failedAt: Long,
    val reason: String = "",
)

/**
 * 失败冷却的纯逻辑：不碰 Android、不读时钟，方便单测。
 *
 * 上游的可用性变化比发版快得多（模型下线、额度耗尽、强制思考策略调整），
 * 静态清单只能兜底。这里记录「刚刚失败过」的模型，在 [WINDOW_MS] 内跳过，
 * 过期后自动重试——既不写死也不用人工维护。
 */
object FailureCooldown {
    /** 冷却窗口：一天。上游的问题多数当天会自愈，再长就错过恢复了。 */
    const val WINDOW_MS = 24L * 60L * 60L * 1000L

    /** 最多保留多少条记录，避免偏好文件无限增长。 */
    const val MAX_ENTRIES = 32

    /** 仍在冷却期内、且属于 [models] 的记录。 */
    fun active(
        failures: List<ModelFailure>,
        now: Long,
        models: Collection<String>? = null,
    ): List<ModelFailure> = failures.filter { failure ->
        val fresh = now - failure.failedAt < WINDOW_MS
        fresh && (models == null || failure.model in models)
    }

    fun isCoolingDown(failures: List<ModelFailure>, model: String, now: Long): Boolean =
        failures.any { it.model == model && now - it.failedAt < WINDOW_MS }

    /**
     * 从候选链里剔除冷却中的模型。
     *
     * 全被剔除时返回原链：本地记录不该让功能彻底不可用——
     * 真不行的话，用户还会看到上游的真实报错，而不是「无事发生」。
     */
    fun filter(chain: List<String>, failures: List<ModelFailure>, now: Long): List<String> {
        val cooling = active(failures, now).map { it.model }.toSet()
        val kept = chain.filterNot { it in cooling }
        return kept.ifEmpty { chain }
    }

    /** 记录一次失败：同模型只留最新一条，并按时间倒序裁剪到 [MAX_ENTRIES]。 */
    fun record(
        failures: List<ModelFailure>,
        model: String,
        now: Long,
        reason: String = "",
    ): List<ModelFailure> =
        (failures.filterNot { it.model == model } + ModelFailure(model, now, reason.take(160)))
            .sortedByDescending { it.failedAt }
            .take(MAX_ENTRIES)

    /** 成功后立刻解除冷却，让模型尽早回到候选链。 */
    fun clear(failures: List<ModelFailure>, model: String): List<ModelFailure> =
        failures.filterNot { it.model == model }
}

/**
 * 失败冷却的持久化层。
 *
 * 用独立偏好文件而不是主设置：这部分是「运行时观测」，可以随时清空，
 * 不应该和密钥、档位混在一起被备份或导入。
 */
@Singleton
class FreeModelHealth @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences("free_model_health", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    /** 当前已知的失败记录（含已过期但尚未清理的）。 */
    val failures: StateFlow<List<ModelFailure>> = state.asStateFlow()

    fun now(): Long = System.currentTimeMillis()

    fun isCoolingDown(model: String): Boolean =
        FailureCooldown.isCoolingDown(state.value, model, now())

    /** 供 Provider 用：把候选链里正在冷却的模型剔掉。 */
    fun filterChain(chain: List<String>): List<String> =
        FailureCooldown.filter(chain, state.value, now())

    fun recordFailure(model: String, reason: String) {
        if (model.isBlank()) return
        write(FailureCooldown.record(state.value, model, now(), reason))
    }

    fun recordSuccess(model: String) {
        if (model.isBlank()) return
        if (state.value.none { it.model == model }) return
        write(FailureCooldown.clear(state.value, model))
    }

    fun reset() = write(emptyList())

    private fun write(next: List<ModelFailure>) {
        state.value = next
        val payload = json.encodeToString(FailuresDto(next.map { FailureDto(it.model, it.failedAt, it.reason) }))
        prefs.edit().putString(KEY, payload).apply()
    }

    private fun read(): List<ModelFailure> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val dto = runCatching { json.decodeFromString<FailuresDto>(raw) }.getOrNull() ?: return emptyList()
        // 读出来顺手剪掉过期项：偏好文件不会因为上游长期正常而越攒越多。
        val now = now()
        return dto.entries
            .map { ModelFailure(it.model, it.failedAt, it.reason) }
            .let { FailureCooldown.active(it, now) }
            .sortedByDescending { it.failedAt }
            .take(FailureCooldown.MAX_ENTRIES)
    }

    private companion object {
        const val KEY = "failures_v1"
    }
}

@Serializable
private data class FailuresDto(val entries: List<FailureDto> = emptyList())

@Serializable
private data class FailureDto(
    val model: String,
    val failedAt: Long = 0L,
    val reason: String = "",
)
