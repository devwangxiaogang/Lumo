package com.yuguang.lumo.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuguang.lumo.data.IncomingShare
import com.yuguang.lumo.data.IncomingShareBus
import com.yuguang.lumo.data.TranslateSeedBus
import com.yuguang.lumo.domain.ChatRepository
import com.yuguang.lumo.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 分享内容要跳到哪里。 */
sealed interface ShareTarget {
    data object Translate : ShareTarget

    data class Chat(val conversationId: Long) : ShareTarget
}

/**
 * 把系统分享 / 文字处理的内容落到界面上。
 *
 * 文本分享新建会话并预填草稿；图片分享新建会话；文字处理直接进翻译页。
 * 一律不自动发送。
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shareBus: IncomingShareBus,
    private val translateSeedBus: TranslateSeedBus,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private var handled = false

    fun handlePending(onTarget: (ShareTarget) -> Unit) {
        // 只在首次进入时消费：配置变更或返回导航不该再触发一次。
        if (handled) return
        val incoming = shareBus.consume() ?: return
        handled = true
        viewModelScope.launch {
            when (incoming) {
                is IncomingShare.Translate -> onTarget(ShareTarget.Translate)
                is IncomingShare.Text -> {
                    val id = newConversation()
                    chatRepository.setDraft(id, incoming.text)
                    onTarget(ShareTarget.Chat(id))
                }
                is IncomingShare.Images -> {
                    val id = newConversation()
                    chatRepository.setDraftImages(id, incoming.uris)
                    onTarget(ShareTarget.Chat(id))
                }
            }
        }
    }

    /** 斜杠指令 /translate：把文本交给翻译页预填。 */
    fun seedTranslate(text: String) {
        translateSeedBus.offer(text)
    }

    private suspend fun newConversation(): Long {
        val selected = settingsRepository.startingModel()
        return chatRepository.createConversation(selected.provider, selected.model)
    }
}
