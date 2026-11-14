package com.yuguang.lumo.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuguang.lumo.data.ChatSearch
import com.yuguang.lumo.data.local.SearchHitRow
import com.yuguang.lumo.domain.ChatRepository
import com.yuguang.lumo.domain.ModelCatalogRepository
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.Conversation
import com.yuguang.lumo.domain.model.ModelChoice
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.SelectedModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val modelCatalog: ModelCatalogRepository,
    private val search: ChatSearch,
) : ViewModel() {
    /** 新建对话对话框里「开始」使用的模型。 */
    var starting by mutableStateOf(SelectedModel(ProviderId.DOUBAO, ProviderId.DOUBAO.defaultModel))
        private set

    /** 消息正文检索结果；空查询时为空列表。 */
    var hits by mutableStateOf<List<SearchHitRow>>(emptyList())
        private set

    private var searchJob: Job? = null

    fun searchMessages(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            hits = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            search.search(query).collect { hits = it }
        }
    }

    init {
        refreshStarting()
        viewModelScope.launch { modelCatalog.refreshAll() }
    }

    /** 从对话页返回时也刷新一次：上次成功发送的模型可能已经变了。 */
    fun refreshStarting() {
        viewModelScope.launch { starting = settingsRepository.startingModel() }
    }

    val conversations: StateFlow<List<Conversation>> = repository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    suspend fun create(provider: ProviderId, model: String): Long =
        repository.createConversation(provider, model)

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteConversation(id) }
    }

    fun setPinned(id: Long, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }

    fun rename(id: Long, title: String) {
        viewModelScope.launch { repository.renameConversation(id, title) }
    }
}
