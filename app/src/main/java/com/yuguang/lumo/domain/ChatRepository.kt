package com.yuguang.lumo.domain

import android.net.Uri
import com.yuguang.lumo.domain.model.ChatMessage
import com.yuguang.lumo.domain.model.Conversation
import com.yuguang.lumo.domain.model.ProviderId
import kotlinx.coroutines.flow.Flow

/** 分享进来、还没被发送的草稿。只在内存里存活，进程重启即丢弃。 */
data class ChatDraft(
    val text: String = "",
    val uris: List<Uri> = emptyList(),
)

interface ChatRepository {    fun observeConversations(): Flow<List<Conversation>>

    fun observeConversation(id: Long): Flow<Conversation?>

    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>>

    fun isGenerating(conversationId: Long): Flow<Boolean>

    suspend fun createConversation(provider: ProviderId, model: String): Long

    suspend fun updateModel(conversationId: Long, model: String): String?

    suspend fun updateSystemPrompt(conversationId: Long, prompt: String): String?

    suspend fun deleteConversation(id: Long)

    suspend fun setPinned(id: Long, pinned: Boolean)

    /** 切换深度思考；只影响后续消息。 */
    suspend fun setThinking(id: Long, thinking: Boolean)

    /** 分享进来的文字先作为草稿，界面预填但不自动发送。 */
    suspend fun setDraft(conversationId: Long, text: String)

    /** 分享进来的图片先挂到会话上，界面预填但不自动发送。 */
    suspend fun setDraftImages(conversationId: Long, uris: List<Uri>)

    /** 取出并清空草稿；没有草稿时返回 null。 */
    fun takeDraft(conversationId: Long): ChatDraft?

    suspend fun renameConversation(id: Long, title: String)

    /** 返回 null 表示已开始生成，否则是需要展示给用户的原因。 */
    suspend fun send(
        conversationId: Long,
        text: String,
        imageUris: List<Uri> = emptyList(),
        drawMode: Boolean = false,
    ): String?

    suspend fun retry(conversationId: Long): String?

    /**
     * 编辑并分支：在 [messageId] 的父节点下插入新的用户消息并生成。
     * 返回 null 表示已开始，否则是需要展示给用户的原因。
     */
    suspend fun editAndSend(conversationId: Long, messageId: Long, newText: String): String?

    /**
     * 切分支：把会话叶子指向 [messageId] 子树里最深的那条，再沿此路径显示历史。
     * 返回 null 表示成功。
     */
    suspend fun switchBranch(conversationId: Long, messageId: Long): String?

    fun stop(conversationId: Long)
}
