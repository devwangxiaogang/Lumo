package com.yuguang.lumo.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuguang.lumo.R
import com.yuguang.lumo.data.ChatBackupManager
import com.yuguang.lumo.data.FailureCooldown
import com.yuguang.lumo.data.FreeLaneSelfTest
import com.yuguang.lumo.data.FreeModelHealth
import com.yuguang.lumo.data.ModelFailure
import com.yuguang.lumo.data.MemoryStore
import com.yuguang.lumo.data.SelfTestItem
import com.yuguang.lumo.data.local.MemoryEntity
import com.yuguang.lumo.domain.ModelCatalogRepository
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.FreeTtsBackend
import com.yuguang.lumo.domain.model.ModelChoice
import com.yuguang.lumo.domain.model.ProviderConfig
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.SpeechPreferences
import com.yuguang.lumo.domain.model.SpeechTier
import com.yuguang.lumo.domain.model.TtsEngine
import com.yuguang.lumo.domain.model.VoiceChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val modelCatalog: ModelCatalogRepository,
    private val backupManager: ChatBackupManager,
    private val memoryStore: MemoryStore,
    private val selfTest: FreeLaneSelfTest,
    private val freeHealth: FreeModelHealth,
) : ViewModel() {
    var configs by mutableStateOf<Map<ProviderId, ProviderConfig>?>(null)
        private set

    var speech by mutableStateOf(SpeechPreferences())
        private set

    var refreshing by mutableStateOf(false)
        private set

    private val messagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = messagesFlow.asSharedFlow()

    val catalogs: StateFlow<Map<ProviderId, List<ModelChoice>>> = combine(
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

    init {
        viewModelScope.launch {
            configs = ProviderId.entries.associateWith { settingsRepository.get(it) }
            speech = settingsRepository.speech()
        }
    }

    fun setEngine(engine: TtsEngine) {
        viewModelScope.launch {
            settingsRepository.editSpeech { it.copy(engine = engine) }
            speech = settingsRepository.speech()
        }
    }

    fun setLiveVoice(voice: VoiceChoice) {
        viewModelScope.launch {
            settingsRepository.editSpeech { it.copy(liveVoice = voice) }
            speech = settingsRepository.speech()
        }
    }

    fun setSpeaker(speaker: VoiceChoice) {
        viewModelScope.launch {
            settingsRepository.editSpeech { it.copy(speaker = speaker) }
            speech = settingsRepository.speech()
        }
    }

    /** 切换档位：收费（豆包） / 全链路免费。 */
    fun setTier(tier: SpeechTier) {
        viewModelScope.launch {
            settingsRepository.editSpeech { it.copy(tier = tier) }
            speech = settingsRepository.speech()
        }
    }

    fun setFreeTtsBackend(backend: FreeTtsBackend) {
        viewModelScope.launch {
            settingsRepository.editSpeech { it.copy(freeTtsBackend = backend) }
            speech = settingsRepository.speech()
        }
    }

    /** 边生成边朗读开关。 */
    fun setAutoRead(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.editSpeech { it.copy(autoRead = enabled) }
            speech = settingsRepository.speech()
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMemoryEnabled(enabled)
            memoryOn = enabled
        }
    }

    /** AI 自动标题开关。默认开；关闭后列表沿用首条消息截断标题。 */
    var autoTitleEnabled by mutableStateOf(true)
        private set

    fun setAutoTitle(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoTitleEnabled(enabled)
            autoTitleEnabled = enabled
        }
    }

    var memoryOn by mutableStateOf(false)
        private set

    var memories by mutableStateOf<List<MemoryEntity>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            memoryOn = settingsRepository.memoryEnabled()
            autoTitleEnabled = settingsRepository.autoTitleEnabled()
            memoryStore.observe().collect { memories = it }
        }
    }

    fun addMemory(content: String) {
        viewModelScope.launch {
            val added = memoryStore.add(content)
            messagesFlow.emit(
                context.getString(if (added) R.string.saved_provider else R.string.memory_deleted),
            )
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryStore.delete(id)
            messagesFlow.emit(context.getString(R.string.memory_deleted))
        }
    }

    /** 清空全部长期记忆：一次事务 + 一条提示，避免逐条删除刷屏。 */
    fun clearAllMemories() {
        viewModelScope.launch {
            val removed = memoryStore.clearAll()
            messagesFlow.emit(context.getString(R.string.settings_memory_cleared, removed))
        }
    }

    fun toggleMemory(id: Long, enabled: Boolean) {
        viewModelScope.launch { memoryStore.setEnabled(id, enabled) }
    }

    fun update(config: ProviderConfig) {
        val current = configs ?: return
        configs = current + (config.provider to config)
    }

    fun save(provider: ProviderId) {
        val config = configs?.get(provider) ?: return
        viewModelScope.launch {
            settingsRepository.save(config)
            messagesFlow.emit(context.getString(R.string.saved_provider, context.getString(provider.nameRes)))
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            refreshing = true
            val error = modelCatalog.refreshAll()
            refreshing = false
            messagesFlow.emit(
                if (error == null) context.getString(R.string.models_refreshed)
                else error,
            )
        }
    }

    // --- 免费链路自检 ---

    var selfTesting by mutableStateOf(false)
        private set

    var selfTestResults by mutableStateOf<List<SelfTestItem>>(emptyList())
        private set

    /** 免费池当前的失败冷却记录；界面据此显示「哪些模型暂时不参与」。 */
    var modelFailures by mutableStateOf<List<ModelFailure>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            freeHealth.failures.collect { modelFailures = FailureCooldown.active(it, freeHealth.now()) }
        }
    }

    /** 清空冷却记录，让所有模型立刻重新参与降级链。 */
    fun resetModelHealth() {
        viewModelScope.launch {
            freeHealth.reset()
            messagesFlow.emit(context.getString(R.string.free_health_reset_done))
        }
    }

    fun runSelfTest() {
        if (selfTesting) return
        viewModelScope.launch {
            selfTesting = true
            selfTestResults = emptyList()
            val results = runCatching { selfTest.run() }.getOrElse { emptyList() }
            selfTestResults = results
            selfTesting = false
            messagesFlow.emit(
                context.getString(
                    if (results.all { it.ok }) R.string.free_self_passed else R.string.free_self_partial,
                ),
            )
        }
    }

    /** 自定义端点：用一次模型列表探测验证 Base URL 是否可达。 */
    fun testConnection(provider: ProviderId) {
        val config = configs?.get(provider) ?: return
        viewModelScope.launch {
            messagesFlow.emit(context.getString(R.string.free_self_test_running))
            val start = System.currentTimeMillis()
            val error = modelCatalog.refresh(provider)
            val elapsed = System.currentTimeMillis() - start
            messagesFlow.emit(
                if (error == null) context.getString(R.string.connection_ok, elapsed)
                else context.getString(R.string.connection_failed, error),
            )
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            messagesFlow.emit(
                runCatching { backupManager.exportTo(uri) }.fold(
                    onSuccess = { context.getString(R.string.backup_export_done, it) },
                    onFailure = { context.getString(R.string.backup_failed, it.message.orEmpty()) },
                ),
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            messagesFlow.emit(
                runCatching { backupManager.importFrom(uri) }.fold(
                    onSuccess = { context.getString(R.string.backup_import_done, it) },
                    onFailure = { context.getString(R.string.backup_failed, it.message.orEmpty()) },
                ),
            )
        }
    }
}
