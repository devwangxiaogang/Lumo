package com.yuguang.lumo.ui.arena

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.ArenaDraft
import com.yuguang.lumo.domain.ArenaRepository
import com.yuguang.lumo.domain.ArenaRun
import com.yuguang.lumo.domain.ArenaSide
import com.yuguang.lumo.domain.ArenaTally
import com.yuguang.lumo.domain.ModelCatalogRepository
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.MessageStatus
import com.yuguang.lumo.domain.model.ModelChoice
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.SelectedModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 顶部两个选择器的当前值；与持久化分开，避免每次输入都写盘。 */
private data class ArenaSelection(
    val providerA: ProviderId,
    val modelA: String,
    val providerB: ProviderId,
    val modelB: String,
)

data class ArenaUiState(
    val prompt: String = "",
    val providerA: ProviderId = ProviderId.DEEPSEEK,
    val modelA: String = ProviderId.DEEPSEEK.defaultModel,
    val providerB: ProviderId = ProviderId.DOUBAO,
    val modelB: String = ProviderId.DOUBAO.defaultModel,
    /** 当前是否有一局在跑（按钮在「开战/停止」之间切换）。 */
    val running: Boolean = false,
    val activeRunId: Long = 0L,
    val activeRun: ArenaRun? = null,
    /** 回看历史时非 0；此时展示的是历史局而不是当前局。 */
    val viewingRunId: Long = 0L,
    val viewingRun: ArenaRun? = null,
    val runs: List<ArenaRun> = emptyList(),
    val catalogs: Map<ProviderId, List<ModelChoice>> = emptyMap(),
) {
    /** 界面主体展示哪一局：回看优先，否则当前局。 */
    val shownRun: ArenaRun? get() = viewingRun ?: activeRun

    val isViewingHistory: Boolean get() = viewingRunId > 0L

    /** 两栏都落了终态才允许投票：还在流的时候投票会锁死结果。 */
    val canVote: Boolean
        get() = shownRun?.let { run ->
            run.id > 0L &&
                run.slotA.status != MessageStatus.STREAMING &&
                run.slotB.status != MessageStatus.STREAMING
        } == true

}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArenaViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ArenaRepository,
    private val settingsRepository: SettingsRepository,
    private val modelCatalog: ModelCatalogRepository,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("arena_prefs", Context.MODE_PRIVATE)

    private val promptFlow = MutableStateFlow("")
    private val selectionFlow = MutableStateFlow(
        ArenaSelection(
            providerA = ProviderId.DEEPSEEK,
            modelA = ProviderId.DEEPSEEK.defaultModel,
            providerB = ProviderId.DOUBAO,
            modelB = ProviderId.DOUBAO.defaultModel,
        ),
    )
    private val activeIdFlow = MutableStateFlow(0L)
    private val viewingIdFlow = MutableStateFlow(0L)
    private val runningFlow = MutableStateFlow(false)

    private val errorsFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors = errorsFlow.asSharedFlow()

    private val activeRunFlow = activeIdFlow.flatMapLatest { id ->
        if (id == 0L) flowOf(null) else repository.observeRun(id)
    }
    private val viewingRunFlow = viewingIdFlow.flatMapLatest { id ->
        if (id == 0L) flowOf(null) else repository.observeRun(id)
    }
    private val runsFlow = repository.observeRuns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val catalogsFlow = combine(
        modelCatalog.observe(ProviderId.DEEPSEEK),
        modelCatalog.observe(ProviderId.DOUBAO),
        modelCatalog.observe(ProviderId.FREE),
    ) { deepseek, doubao, free ->
        mapOf(
            ProviderId.DEEPSEEK to deepseek,
            ProviderId.DOUBAO to doubao,
            ProviderId.FREE to free,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val baseFlow = combine(
        promptFlow,
        selectionFlow,
        runningFlow,
    ) { prompt, selection, running -> Triple(prompt, selection, running) }

    private val runFlow = combine(
        activeIdFlow,
        activeRunFlow,
        viewingIdFlow,
        viewingRunFlow,
        runsFlow,
    ) { activeId, active, viewingId, viewing, runs ->
        RunSnapshot(activeId, active, viewingId, viewing, runs)
    }

    val uiState: StateFlow<ArenaUiState> = combine(baseFlow, runFlow, catalogsFlow) { base, run, catalogs ->
        ArenaUiState(
            prompt = base.first,
            providerA = base.second.providerA,
            modelA = base.second.modelA,
            providerB = base.second.providerB,
            modelB = base.second.modelB,
            running = base.third,
            activeRunId = run.activeId,
            activeRun = run.active,
            viewingRunId = run.viewingId,
            viewingRun = run.viewing,
            runs = run.runs,
            catalogs = catalogs,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ArenaUiState(
            providerA = ProviderId.DEEPSEEK,
            modelA = ProviderId.DEEPSEEK.defaultModel,
            providerB = ProviderId.DOUBAO,
            modelB = ProviderId.DOUBAO.defaultModel,
        ),
    )

    init {
        refreshSelections()
        viewModelScope.launch { modelCatalog.refreshAll() }
        // 两栏都落终态后按钮回到「开战」；停止后的收尾写入也会走这里。
        viewModelScope.launch {
            activeRunFlow.collect { run ->
                if (run == null) return@collect
                val settled = run.slotA.status != MessageStatus.STREAMING &&
                    run.slotB.status != MessageStatus.STREAMING
                if (settled) runningFlow.value = false
            }
        }
    }

    fun updatePrompt(text: String) {
        promptFlow.value = text
    }

    fun selectModel(side: ArenaSide, provider: ProviderId, model: String) {
        val current = selectionFlow.value
        val next = when (side) {
            ArenaSide.A -> current.copy(providerA = provider, modelA = model)
            ArenaSide.B -> current.copy(providerB = provider, modelB = model)
        }
        selectionFlow.value = next
        // 记下上次选择：下次进擂台沿用同一对模型。
        persist(next)
    }

    fun start() {
        if (runningFlow.value) return
        val prompt = promptFlow.value.trim()
        if (prompt.isEmpty()) {
            viewModelScope.launch { errorsFlow.emit(context.getString(R.string.arena_prompt_empty)) }
            return
        }
        val selection = selectionFlow.value
        persist(selection)
        viewModelScope.launch {
            try {
                val id = repository.start(
                    ArenaDraft(
                        prompt = prompt,
                        providerA = selection.providerA,
                        modelA = selection.modelA,
                        providerB = selection.providerB,
                        modelB = selection.modelB,
                    ),
                )
                activeIdFlow.value = id
                viewingIdFlow.value = 0L
                runningFlow.value = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorsFlow.emit(error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.arena_side_failed))
            }
        }
    }

    fun stop() {
        val id = activeIdFlow.value
        if (id == 0L) return
        repository.stop(id)
    }

    fun vote(runId: Long, winner: String) {
        if (runId == 0L) return
        viewModelScope.launch {
            try {
                repository.vote(runId, winner)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorsFlow.emit(error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.arena_side_failed))
            }
        }
    }

    fun delete(runId: Long) {
        viewModelScope.launch {
            try {
                repository.delete(runId)
                if (activeIdFlow.value == runId) {
                    activeIdFlow.value = 0L
                    runningFlow.value = false
                }
                if (viewingIdFlow.value == runId) viewingIdFlow.value = 0L
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorsFlow.emit(error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.arena_side_failed))
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                runsFlow.value.forEach { repository.delete(it.id) }
                activeIdFlow.value = 0L
                viewingIdFlow.value = 0L
                runningFlow.value = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorsFlow.emit(error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.arena_side_failed))
            }
        }
    }

    fun openRun(runId: Long) {
        if (runId == 0L) return
        viewingIdFlow.value = runId
    }

    fun closeHistory() {
        viewingIdFlow.value = 0L
    }

    /** 「再来一局」：把历史局的问题和模型搬回输入区，不复制输出。 */
    fun replay(run: ArenaRun) {
        promptFlow.value = run.prompt
        val selection = ArenaSelection(
            providerA = run.slotA.provider,
            modelA = run.slotA.model,
            providerB = run.slotB.provider,
            modelB = run.slotB.model,
        )
        selectionFlow.value = selection
        persist(selection)
        viewingIdFlow.value = 0L
    }

    /** 首次进入：沿用上次选择；没有记录时用当前默认模型 + 另一服务商。 */
    private fun refreshSelections() {
        viewModelScope.launch {
            val starting = settingsRepository.startingModel()
            val first = readSelection(KEY_A) ?: starting
            val second = readSelection(KEY_B) ?: defaultSecond(first)
            selectionFlow.value = ArenaSelection(
                providerA = first.provider,
                modelA = first.model,
                providerB = second.provider,
                modelB = second.model,
            )
        }
    }

    private fun defaultSecond(first: SelectedModel): SelectedModel {
        val provider = ProviderId.entries.firstOrNull {
            it != first.provider && it != ProviderId.CUSTOM && it.models.isNotEmpty()
        } ?: ProviderId.FREE
        return SelectedModel(provider, provider.defaultModel)
    }

    private fun readSelection(key: String): SelectedModel? {
        val provider = prefs.getString(key + SUFFIX_PROVIDER, null)
            ?.let { runCatching { ProviderId.valueOf(it) }.getOrNull() }
            ?: return null
        val model = prefs.getString(key + SUFFIX_MODEL, null)?.takeIf { it.isNotBlank() } ?: return null
        return SelectedModel(provider, model)
    }

    private fun persist(selection: ArenaSelection) {
        prefs.edit()
            .putString(KEY_A + SUFFIX_PROVIDER, selection.providerA.name)
            .putString(KEY_A + SUFFIX_MODEL, selection.modelA)
            .putString(KEY_B + SUFFIX_PROVIDER, selection.providerB.name)
            .putString(KEY_B + SUFFIX_MODEL, selection.modelB)
            .apply()
    }

    private data class RunSnapshot(
        val activeId: Long,
        val active: ArenaRun?,
        val viewingId: Long,
        val viewing: ArenaRun?,
        val runs: List<ArenaRun>,
    )

    private companion object {
        const val KEY_A = "arena_a"
        const val KEY_B = "arena_b"
        const val SUFFIX_PROVIDER = "_provider"
        const val SUFFIX_MODEL = "_model"
    }
}
