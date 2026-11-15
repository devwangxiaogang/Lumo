package com.yuguang.lumo.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/**
 * 消息全文索引。
 *
 * FTS4 的 unicode61 分词器不切分中文，所以中文查询走 LIKE 分支（见 [SearchDao]），
 * 这个索引服务英文/数字等以空格分词的查询。
 */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFts(
    @ColumnInfo(name = "content") val content: String,
)

/** 跨会话的长期记忆。默认关闭，开启后由助手在回合结束后提炼。 */
@Entity(tableName = "memories", indices = [Index("enabled")])
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val tags: String = "",
    val sourceConversationId: Long = 0,
    val createdAt: Long,
    val enabled: Boolean = true,
)

/** 一条搜索命中：所属会话 + 命中消息 + 高亮片段。 */
data class SearchHitRow(
    val conversationId: Long,
    val messageId: Long,
    val title: String,
    val role: String,
    val createdAt: Long,
    val snippet: String,
)

@Dao
interface SearchDao {
    /**
     * 两种检索方式共用一条 RawQuery：含中日韩文字时按 LIKE 子串匹配，
     * 纯拉丁查询走 FTS 的 MATCH + snippet 高亮。
     */
    @RawQuery(observedEntities = [MessageEntity::class, ConversationEntity::class])
    fun search(query: SupportSQLiteQuery): Flow<List<SearchHitRow>>
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE enabled = 1 ORDER BY createdAt DESC LIMIT :limit")
    suspend fun active(limit: Int): List<MemoryEntity>

    @Query("SELECT content FROM memories")
    suspend fun allContents(): List<String>

    @Insert
    suspend fun insert(entity: MemoryEntity): Long

    @Query("UPDATE memories SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memories WHERE id NOT IN (SELECT id FROM memories ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM memories")
    suspend fun clear()
}
