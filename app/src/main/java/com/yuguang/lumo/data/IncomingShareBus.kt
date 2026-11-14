package com.yuguang.lumo.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 系统分享 / 文字处理菜单的入口缓冲。
 *
 * Activity 在 onCreate / onNewIntent 里把 Intent 解析成 [IncomingShare] 放进来，
 * Compose 侧消费一次后清空——避免配置变更或返回导航把同一份内容反复投喂。
 */
sealed interface IncomingShare {
    /** 普通分享文本：新建会话并预填。 */
    data class Text(val text: String) : IncomingShare

    /** 选中文字后的「翻译」动作：直接进翻译页。 */
    data class Translate(val text: String) : IncomingShare

    /** 分享图片：新建会话并挂上附件。 */
    data class Images(val uris: List<Uri>) : IncomingShare
}

@Singleton
class IncomingShareBus @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val state = MutableStateFlow<IncomingShare?>(null)
    val pending: StateFlow<IncomingShare?> = state.asStateFlow()

    /** 由 Activity 调用；解析失败或没有内容时什么都不做。 */
    fun accept(intent: Intent?) {
        if (intent == null) return
        val parsed = parse(intent) ?: return
        state.value = parsed
    }

    /** 取出并清空。 */
    fun consume(): IncomingShare? {
        val current = state.value
        state.value = null
        return current
    }

    private fun parse(intent: Intent): IncomingShare? = when (intent.action) {
        Intent.ACTION_SEND -> {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
            when {
                intent.type?.startsWith("image/") == true -> {
                    val uri = streamUri(intent)
                    if (uri != null) IncomingShare.Images(listOf(uri)) else null
                }
                !text.isNullOrBlank() -> IncomingShare.Text(text)
                else -> null
            }
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }.orEmpty().filterNotNull()
            if (uris.isEmpty()) {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
                if (!text.isNullOrBlank()) IncomingShare.Text(text) else null
            } else {
                IncomingShare.Images(uris)
            }
        }
        Intent.ACTION_PROCESS_TEXT -> {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()
            if (text.isNullOrBlank()) null else IncomingShare.Translate(text)
        }
        else -> null
    }

    private fun streamUri(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
}
