package com.yuguang.lumo.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从对话页跳到翻译页时预填的文本。
 *
 * 与 [IncomingShareBus] 分开：那条链路来自系统 Intent，这里来自应用内的斜杠指令，
 * 翻译页只消费一次，避免返回导航时重复翻译。
 */
@Singleton
class TranslateSeedBus @Inject constructor() {
    private val state = MutableStateFlow<String?>(null)
    val seeds: StateFlow<String?> = state.asStateFlow()

    fun offer(text: String) {
        state.value = text.trim().ifBlank { null }
    }

    fun consume(): String? = state.value.also { state.value = null }
}
