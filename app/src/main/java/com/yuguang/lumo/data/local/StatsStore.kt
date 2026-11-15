package com.yuguang.lumo.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 用量统计的一行：某天某模型的输入/输出 token 合计。 */
data class UsageDayRow(
    val day: String,
    val provider: String,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
)

/** 按模型聚合的一行。 */
data class UsageModelRow(
    val provider: String,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val messages: Int,
)

/**
 * 用量统计的只读聚合。
 *
 * 数据源只有 messages 的 promptTokens / completionTokens；
 * 按天分组用 SQLite 的 date() 把毫秒时间戳换成 UTC 日期串，
 * 界面按本地时区需要的偏移在纯函数里补齐（见 UsageStats）。
 */
@Dao
interface StatsDao {
    @Query(
        "SELECT strftime('%Y-%m-%d', m.createdAt / 1000, 'unixepoch') AS day, " +
            "c.provider AS provider, c.model AS model, " +
            "COALESCE(SUM(m.promptTokens), 0) AS promptTokens, " +
            "COALESCE(SUM(m.completionTokens), 0) AS completionTokens " +
            "FROM messages m JOIN conversations c ON c.id = m.conversationId " +
            "WHERE m.createdAt >= :since AND m.role = 'ASSISTANT' " +
            "GROUP BY day, provider, model ORDER BY day ASC",
    )
    fun observeDaily(since: Long): Flow<List<UsageDayRow>>

    @Query(
        "SELECT c.provider AS provider, c.model AS model, " +
            "COALESCE(SUM(m.promptTokens), 0) AS promptTokens, " +
            "COALESCE(SUM(m.completionTokens), 0) AS completionTokens, " +
            "COUNT(m.id) AS messages " +
            "FROM messages m JOIN conversations c ON c.id = m.conversationId " +
            "WHERE m.role = 'ASSISTANT' AND (m.promptTokens > 0 OR m.completionTokens > 0) " +
            "GROUP BY provider, model ORDER BY (promptTokens + completionTokens) DESC LIMIT :limit",
    )
    fun observeModels(limit: Int): Flow<List<UsageModelRow>>

    @Query(
        "SELECT COALESCE(SUM(m.promptTokens + m.completionTokens), 0) FROM messages m " +
            "WHERE m.role = 'ASSISTANT'",
    )
    fun observeTotalTokens(): Flow<Int>

    @Query("SELECT COUNT(*) FROM conversations")
    fun observeConversationCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM translations")
    fun observeTranslationCount(): Flow<Int>
}
