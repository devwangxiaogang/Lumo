package com.yuguang.lumo.data

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.yuguang.lumo.R
import com.yuguang.lumo.data.local.SearchDao
import com.yuguang.lumo.data.local.SearchHitRow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息正文检索。
 *
 * FTS4 的 unicode61 分词器不切分中日韩文字，因此按查询类型分流：
 * 含 CJK 的查询走 LIKE 子串匹配，纯拉丁查询走 FTS 的 MATCH + snippet 高亮。
 */
@Singleton
class ChatSearch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchDao: SearchDao,
) {
    fun search(rawQuery: String): Flow<List<SearchHitRow>> {
        val query = rawQuery.trim()
        val sql = when {
            query.isEmpty() -> emptySql()
            containsCjk(query) -> likeSql(query)
            else -> ftsSql(query)
        }
        return searchDao.search(sql).flowOn(Dispatchers.IO)
    }

    private fun emptySql(): SimpleSQLiteQuery =
        SimpleSQLiteQuery("SELECT 0 AS conversationId, 0 AS messageId, '' AS title, '' AS role, 0 AS createdAt, '' AS snippet WHERE 0")

    /** 中文查询：直接子串匹配，命中条数上限 60，避免长文会话拖慢列表。 */
    private fun likeSql(query: String): SimpleSQLiteQuery {
        val pattern = "%${escapeLike(query)}%"
        return SimpleSQLiteQuery(
            """
            SELECT m.conversationId AS conversationId,
                   m.id AS messageId,
                   c.title AS title,
                   m.role AS role,
                   m.createdAt AS createdAt,
                   substr(m.content, 1, 160) AS snippet
            FROM messages m
            JOIN conversations c ON c.id = m.conversationId
            WHERE m.content LIKE ? ESCAPE '\'
            ORDER BY m.createdAt DESC
            LIMIT 60
            """.trimIndent(),
            arrayOf<Any>(pattern),
        )
    }

    /**
     * 拉丁查询：FTS 的 MATCH + snippet 高亮。
     *
     * FTS 语法里的引号/星号/连字符会被当作运算符，这里按「整句短语」处理最稳：
     * 给查询加上双引号并转义内部引号。
     */
    private fun ftsSql(query: String): SimpleSQLiteQuery {
        val phrase = "\"" + query.replace("\"", "\"\"") + "\""
        return SimpleSQLiteQuery(
            """
            SELECT m.conversationId AS conversationId,
                   m.id AS messageId,
                   c.title AS title,
                   m.role AS role,
                   m.createdAt AS createdAt,
                   snippet(messages_fts, '[', ']', '…', -1, 12) AS snippet
            FROM messages_fts
            JOIN messages m ON m.id = messages_fts.docid
            JOIN conversations c ON c.id = m.conversationId
            WHERE messages_fts MATCH ?
            ORDER BY m.createdAt DESC
            LIMIT 60
            """.trimIndent(),
            arrayOf<Any>(phrase),
        )
    }

    private fun containsCjk(text: String): Boolean =
        text.any { it in '\u3040'..'\u30ff' || it in '\u3400'..'\u9fff' || it in '\uac00'..'\ud7af' }

    /** LIKE 的 % _ \ 必须转义，否则中文里的下划线会变成通配符。 */
    private fun escapeLike(text: String): String = text
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    fun emptyResult(): String = context.getString(R.string.search_no_result)
}
