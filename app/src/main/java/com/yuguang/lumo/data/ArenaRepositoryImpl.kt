package com.yuguang.lumo.data

import android.content.Context
import android.os.SystemClock
import com.yuguang.lumo.R
import com.yuguang.lumo.data.local.ArenaDao
import com.yuguang.lumo.data.local.ArenaOutputEntity
import com.yuguang.lumo.data.local.ArenaRunEntity
import com.yuguang.lumo.di.AppScope
import com.yuguang.lumo.domain.ArenaDraft
import com.yuguang.lumo.domain.ArenaRepository
import com.yuguang.lumo.domain.ArenaRun
import com.yuguang.lumo.domain.ArenaSide
import com.yuguang.lumo.domain.ArenaSlot
import com.yuguang.lumo.domain.ArenaTally
import com.yuguang.lumo.domain.LlmProvider
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.ChatRequest
import com.yuguang.lumo.domain.model.MessageStatus
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.domain.model.StreamEvent
import com.yuguang.lumo.domain.model.WireMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 擂台：两条独立的流并发跑，各写各的 arena_outputs。
 *
 * 与 ChatRepositoryImpl 分开是刻意的：擂台不进消息图、不做工具轮次、
 * 不碰会话表；单侧失败只影响自己那一栏。
 */
@Singleton
class ArenaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val arenaDao: ArenaDao,
    private val settingsRepository: SettingsRepository,
    private val providers: Map<ProviderId, @JvmSuppressWildcards LlmProvider>,
    @AppScope private val appScope: CoroutineScope,
) : ArenaRepository {
    private val jobs = ConcurrentHashMap<Long, Job>()

    /**
     * 历史列表的刷新信号。
     *
     * ArenaDao 没有「跨局 outputs」查询，列表只能按 runId 逐局读；
     * 仓库自己写库时递增这个序号，列表就能在开战/落终态/投票/删除后刷新，
     * 又不必让每 200ms 的流式写入去触发 50 次查询。
     */
    private val revision = MutableStateFlow(0L)

    override fun observeRuns(limit: Int): Flow<List<ArenaRun>> =
        combine(arenaDao.observeRuns(limit), revision) { rows, _ -> rows }
            .map { rows -> rows.map { row -> row.toDomain(arenaDao.outputs(row.id)) } }

    override fun observeRun(runId: Long): Flow<ArenaRun?> =
        combine(arenaDao.observeOutputs(runId), revision) { outputs, _ -> outputs }
            .map { outputs -> arenaDao.run(runId)?.toDomain(outputs) }

    override suspend fun start(draft: ArenaDraft): Long {
        val prompt = draft.prompt.trim()
        require(prompt.isNotEmpty()) { context.getString(R.string.arena_prompt_empty) }
        val modelA = draft.modelA.trim().ifBlank { draft.providerA.defaultModel }
        val modelB = draft.modelB.trim().ifBlank { draft.providerB.defaultModel }
        val runId = arenaDao.insertRun(
            ArenaRunEntity(
                prompt = prompt,
                providerA = draft.providerA.name,
                modelA = modelA,
                providerB = draft.providerB.name,
                modelB = modelB,
                createdAt = System.currentTimeMillis(),
            ),
        )
        val outputA = insertPendingSide(runId, ArenaSide.A)
        val outputB = insertPendingSide(runId, ArenaSide.B)
        revision.update { it + 1 }
        val job = appScope.launch {
            // supervisorScope：一侧抛错不影响另一侧收尾。
            supervisorScope {
                launch { streamSide(runId, outputA, ArenaSide.A, draft.providerA, modelA, prompt) }
                launch { streamSide(runId, outputB, ArenaSide.B, draft.providerB, modelB, prompt) }
            }
            // 停止会取消父任务；收尾仍要在不可取消上下文里跑完。
            withContext(NonCancellable) {
                arenaDao.trimTo(ArenaTally.MAX_RUNS)
                revision.update { it + 1 }
            }
        }
        jobs[runId] = job
        job.invokeOnCompletion { jobs.remove(runId, job) }
        return runId
    }

    override fun stop(runId: Long) {
        jobs[runId]?.cancel()
    }

    override suspend fun vote(runId: Long, winner: String) {
        val normalized = ArenaTally.normalizeWinner(winner)
        arenaDao.vote(
            id = runId,
            winner = normalized,
            votedAt = if (normalized == ArenaTally.WINNER_NONE) 0 else System.currentTimeMillis(),
        )
        revision.update { it + 1 }
    }

    override suspend fun delete(runId: Long) {
        jobs.remove(runId)?.cancel()
        arenaDao.deleteRun(runId)
        revision.update { it + 1 }
    }

    private suspend fun insertPendingSide(runId: Long, side: ArenaSide): Long =
        arenaDao.insertOutput(
            ArenaOutputEntity(
                runId = runId,
                side = side.name,
                content = "",
                reasoning = "",
                status = MessageStatus.STREAMING.name,
                error = "",
            ),
        )

    private suspend fun streamSide(
        runId: Long,
        outputId: Long,
        side: ArenaSide,
        provider: ProviderId,
        model: String,
        prompt: String,
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var ttftMs = 0
        // 首字指「任一可见输出」的首个增量，思考也算。
        // 若只在正文为空时记录，先出思考的模型（DeepSeek 深度思考）永远记不到首字。
        var firstTokenSeen = false
        var promptTokens = 0
        var completionTokens = 0
        var lastFlush = 0L
        var outcome = MessageStatus.DONE
        var error = ""
        var cancelled = false

        suspend fun write(status: MessageStatus, errorText: String, totalMs: Int, bump: Boolean) {
            arenaDao.updateOutput(
                id = outputId,
                content = content.toString(),
                reasoning = reasoning.toString(),
                status = status.name,
                error = errorText,
                // 首字延迟只在第一次写；之后即使总耗时变了也不覆盖。
                ttftMs = ttftMs,
                totalMs = totalMs,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
            )
            if (bump) revision.update { it + 1 }
        }

        suspend fun flush(force: Boolean) {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastFlush < FLUSH_INTERVAL_MS) return
            lastFlush = now
            write(MessageStatus.STREAMING, "", (now - startedAt).toInt(), bump = false)
        }

        try {
            val config = settingsRepository.get(provider)
            val llm = providers[provider]
                ?: error(context.getString(R.string.provider_missing, context.getString(provider.nameRes)))
            val effectiveModel = model.ifBlank { config.model.ifBlank { provider.defaultModel } }
            llm.stream(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                request = ChatRequest(
                    model = effectiveModel,
                    messages = listOf(WireMessage(role = Role.USER, text = prompt)),
                    // 留空：FreeProvider 自己会补上默认降级链，与对话页行为一致。
                    fallbackModels = emptyList(),
                    maxOutputTokens = if (provider == ProviderId.FREE) FREE_MAX_OUTPUT else 0,
                ),
            ).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        if (event.text.isNotEmpty()) {
                            val first = !firstTokenSeen
                            content.append(event.text)
                            if (first) {
                                firstTokenSeen = true
                                ttftMs = ArenaTally.ttftAfter(ttftMs, (SystemClock.elapsedRealtime() - startedAt).toInt())
                                // 首字立刻落盘：中途退出再进也能看到延迟和开头。
                                flush(force = true)
                            } else {
                                flush(force = false)
                            }
                        }
                    }
                    is StreamEvent.Reasoning -> {
                        if (event.text.isNotEmpty()) {
                            reasoning.append(event.text)
                            // 先出思考也算出了第一个字，否则这一侧的首字延迟永远是「—」。
                            if (!firstTokenSeen) {
                                firstTokenSeen = true
                                ttftMs = ArenaTally.ttftAfter(ttftMs, (SystemClock.elapsedRealtime() - startedAt).toInt())
                            }
                        }
                        flush(force = false)
                    }
                    is StreamEvent.ToolCalls -> Unit
                    is StreamEvent.Completed -> {
                        promptTokens += event.promptTokens ?: 0
                        completionTokens += event.completionTokens ?: 0
                        outcome = MessageStatus.DONE
                    }
                    is StreamEvent.Failed -> {
                        error = event.message
                        outcome = MessageStatus.FAILED
                    }
                }
            }
        } catch (cancelledError: CancellationException) {
            cancelled = true
        } catch (t: Throwable) {
            error = t.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.arena_side_failed)
            outcome = MessageStatus.FAILED
        }

        withContext(NonCancellable) {
            val total = (SystemClock.elapsedRealtime() - startedAt).toInt()
            val status = when {
                // 停止：出过字就保留为 DONE 便于回看，没出字记 FAILED「已停止」。
                cancelled -> if (content.isEmpty() && reasoning.isEmpty()) MessageStatus.FAILED else MessageStatus.DONE
                else -> outcome
            }
            val finalError = when {
                cancelled && status == MessageStatus.FAILED -> context.getString(R.string.arena_stopped)
                cancelled -> ""
                else -> error
            }
            write(status, finalError, total, bump = true)
        }
    }

    private fun ArenaRunEntity.toDomain(outputs: List<ArenaOutputEntity>): ArenaRun {
        val outputA = outputs.firstOrNull { it.side == ArenaSide.A.name }
        val outputB = outputs.firstOrNull { it.side == ArenaSide.B.name }
        return ArenaRun(
            id = id,
            prompt = prompt,
            slotA = slotOf(ArenaSide.A, providerA, modelA, outputA),
            slotB = slotOf(ArenaSide.B, providerB, modelB, outputB),
            winner = winner,
            votedAt = votedAt,
            createdAt = createdAt,
        )
    }

    private fun slotOf(
        side: ArenaSide,
        providerName: String,
        model: String,
        output: ArenaOutputEntity?,
    ): ArenaSlot = ArenaSlot(
        provider = runCatching { ProviderId.valueOf(providerName) }.getOrDefault(ProviderId.DEEPSEEK),
        model = model,
        content = output?.content.orEmpty(),
        reasoning = output?.reasoning.orEmpty(),
        // 缺行时按失败处理：数据不完整比假装生成中更诚实。
        status = output?.status?.let { runCatching { MessageStatus.valueOf(it) }.getOrNull() }
            ?: MessageStatus.FAILED,
        error = output?.error.orEmpty(),
        ttftMs = output?.ttftMs ?: 0,
        totalMs = output?.totalMs ?: 0,
        promptTokens = output?.promptTokens ?: 0,
        completionTokens = output?.completionTokens ?: 0,
    )

    private companion object {
        /** 与 ChatRepositoryImpl 的流式节流同量级：够快看到字，又不把库写爆。 */
        const val FLUSH_INTERVAL_MS = 200L

        /** 免费池单次输出上限，与对话页保持一致，避免长文撞额度。 */
        const val FREE_MAX_OUTPUT = 4_096
    }
}
