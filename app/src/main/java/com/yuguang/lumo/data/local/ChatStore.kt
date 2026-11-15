package com.yuguang.lumo.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** provider、model 存在会话上。换模型只改这一行，不改已经发出的消息。 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val provider: String,
    val model: String,
    val systemPrompt: String,
    val updatedAt: Long,
    /** 置顶会话排在列表最前。 */
    val pinned: Boolean = false,
    /** 是否请求深度思考。免费车道的思考与正文共享输出额度，默认关闭。 */
    val thinking: Boolean = false,
    /**
     * 当前激活分支的叶子消息。0 表示未初始化（老数据迁移前的默认值）。
     * 会话历史按 leaf 沿 parentId 回溯，切分支只改这一列。
     */
    val leafMessageId: Long = 0,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId"), Index("parentId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val reasoning: String = "",
    val status: String,
    val error: String = "",
    val createdAt: Long,
    /** 本轮请求的输入/输出 token，用量面板用；0 表示上游没给。 */
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    /** 本轮工具调用的轨迹（JSON 行），界面折叠展示。 */
    val toolTrace: String = "",
    /**
     * 消息树的父节点；0 表示一条链的起点。
     * 分支由「同一 parentId 下多条子消息」表达，老数据迁移后是一条单链。
     */
    val parentId: Long = 0,
)

@Entity(
    tableName = "message_attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId"), Index("conversationId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val conversationId: Long,
    val localPath: String,
    val mime: String,
    val source: String,
    val remoteUrl: String = "",
    val sortOrder: Int = 0,
)

/** 列表页用的会话摘要：带最后一条消息预览与累计 token。 */
data class ConversationSummaryRow(
    val id: Long,
    val title: String,
    val provider: String,
    val model: String,
    val systemPrompt: String,
    val updatedAt: Long,
    val pinned: Boolean,
    val thinking: Boolean,
    val preview: String?,
    /** 会话累计 token；上游没给 usage 时为 0，界面不显示。 */
    val totalTokens: Int,
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    /** 列表页：会话 + 最后一条消息预览 + 累计 token，置顶优先。 */
    @Query(
        "SELECT c.id AS id, c.title AS title, c.provider AS provider, c.model AS model, " +
            "c.systemPrompt AS systemPrompt, c.updatedAt AS updatedAt, c.pinned AS pinned, " +
            "c.thinking AS thinking, " +
            "(SELECT m.content FROM messages m WHERE m.conversationId = c.id " +
            "ORDER BY m.createdAt DESC, m.id DESC LIMIT 1) AS preview, " +
            "(SELECT COALESCE(SUM(m2.promptTokens + m2.completionTokens), 0) FROM messages m2 " +
            "WHERE m2.conversationId = c.id) AS totalTokens " +
            "FROM conversations c ORDER BY c.pinned DESC, c.updatedAt DESC",
    )
    fun observeSummaries(): Flow<List<ConversationSummaryRow>>

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE conversations SET thinking = :thinking WHERE id = :id")
    suspend fun setThinking(id: Long, thinking: Boolean)

    /** 分支游标：当前激活路径的叶子消息。0 表示未初始化。 */
    @Query("UPDATE conversations SET leafMessageId = :messageId WHERE id = :id")
    suspend fun setLeaf(id: Long, messageId: Long)

    /**
     * 只更新会话的标题/模型/更新时间。
     *
     * 生成过程中会先插入新消息再改会话；若用整行 update(entity)，
     * 会用旧快照里的 leafMessageId 覆盖刚推进的游标，分支立刻回退。
     */
    @Query("UPDATE conversations SET title = :title, model = :model, updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, title: String, model: String, updatedAt: Long)

    @Query("SELECT leafMessageId FROM conversations WHERE id = :id")
    suspend fun leaf(id: Long): Long?

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    /** 导出备份时按时间顺序取全部会话。 */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun all(): List<ConversationEntity>

    @Insert
    suspend fun insert(entity: ConversationEntity): Long

    @Update
    suspend fun update(entity: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observe(conversationId: Long): Flow<List<MessageEntity>>

    /** 导出备份时按时间顺序取整个会话的消息。 */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun list(conversationId: Long): List<MessageEntity>

    /** 分支：同一父节点下的所有子消息，按时间升序（兄弟切换器用）。 */
    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND parentId = :parentId " +
            "ORDER BY createdAt ASC, id ASC",
    )
    suspend fun children(conversationId: Long, parentId: Long): List<MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND status = 'DONE' " +
            "ORDER BY createdAt DESC, id DESC LIMIT :limit",
    )
    suspend fun recentDone(conversationId: Long, limit: Int): List<MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND status = 'DONE' " +
            "AND role = 'USER' ORDER BY createdAt DESC, id DESC LIMIT 1",
    )
    suspend fun lastDoneUser(conversationId: Long): MessageEntity?

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId " +
            "ORDER BY createdAt DESC, id DESC LIMIT 1",
    )
    suspend fun latest(conversationId: Long): MessageEntity?

    @Insert
    suspend fun insert(entity: MessageEntity): Long

    /** 编辑并分支：按 id 取目标消息，用它定位同级的父节点。 */
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "UPDATE messages SET content = :content, reasoning = :reasoning, status = :status, error = :error " +
            "WHERE id = :id",
    )
    suspend fun updateBody(id: Long, content: String, reasoning: String, status: String, error: String)

    @Query(
        "UPDATE messages SET promptTokens = :prompt, completionTokens = :completion WHERE id = :id",
    )
    suspend fun updateUsage(id: Long, prompt: Int, completion: Int)

    @Query("UPDATE messages SET toolTrace = :trace WHERE id = :id")
    suspend fun updateToolTrace(id: Long, trace: String)

    /**
     * 进程在流式生成中被杀掉时，回复会停在 STREAMING。
     * 启动时把这些行改成 FAILED，界面才不会一直显示「生成中」，重试也能用。
     */
    @Query("UPDATE messages SET status = 'FAILED', error = :error WHERE status = 'STREAMING'")
    suspend fun failStreaming(error: String): Int
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM message_attachments WHERE conversationId = :conversationId ORDER BY sortOrder ASC, id ASC")
    fun observeForConversation(conversationId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM message_attachments WHERE messageId = :messageId ORDER BY sortOrder ASC, id ASC")
    suspend fun forMessage(messageId: Long): List<AttachmentEntity>

    /** 一次取回多条消息的用户附图，避免按消息逐条查询。 */
    @Query(
        "SELECT * FROM message_attachments WHERE messageId IN (:messageIds) " +
            "AND source = 'USER' ORDER BY sortOrder ASC, id ASC",
    )
    suspend fun forUserMessages(messageIds: List<Long>): List<AttachmentEntity>

    @Query("SELECT * FROM message_attachments WHERE conversationId = :conversationId")
    suspend fun forConversation(conversationId: Long): List<AttachmentEntity>

    @Insert
    suspend fun insert(entity: AttachmentEntity): Long

    @Insert
    suspend fun insertAll(entities: List<AttachmentEntity>)
}
