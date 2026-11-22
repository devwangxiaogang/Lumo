package com.yuguang.lumo

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuguang.lumo.data.local.AppDatabase
import com.yuguang.lumo.data.local.DATABASE_VERSION
import com.yuguang.lumo.data.local.DatabaseOpener
import com.yuguang.lumo.data.local.MIGRATION_2_3
import com.yuguang.lumo.data.local.MIGRATION_3_4
import com.yuguang.lumo.data.local.MIGRATION_4_5
import com.yuguang.lumo.data.local.MIGRATION_5_6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 完整迁移链的 schema 与数据校验。
 *
 * 之前只有 2→3 与 4→5 两个孤立用例，v6（分支与擂台）从未被自动化验证过，
 * 而它恰好是回填 `parentId` / `leafMessageId` 的复杂 SQL。
 * 这里用 [MigrationTestHelper] 以导出的 schema 为基准，逐条旧版本升级到当前版本。
 *
 * 断言的重点不是「SQL 跑完了」，而是「用户的历史还在」：
 * 消息条数、分支单链、叶子游标、FTS 回填、附件与擂台表结构。
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val allMigrations = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

    @Test
    fun migrate3ToCurrentBuildsChainAndKeepsFts() {
        // v2 没有导出过 schema（1.0 时代没有开 exportSchema），
        // 因此 schema 级校验从 3 开始；2→3 由 MigrationTest 用手工建库覆盖。
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO conversations (id, title, provider, model, systemPrompt, updatedAt) " +
                    "VALUES (1, '旧会话', 'DEEPSEEK', 'deepseek-flash', '人设', 100)",
            )
            db.execSQL(
                "INSERT INTO messages (id, conversationId, role, content, reasoning, status, error, createdAt) " +
                    "VALUES (1, 1, 'USER', 'first migration message', '', 'DONE', '', 10)",
            )
            db.execSQL(
                "INSERT INTO messages (id, conversationId, role, content, reasoning, status, error, createdAt) " +
                    "VALUES (2, 1, 'ASSISTANT', 'second migration reply', '', 'DONE', '', 20)",
            )
        }

        val db = helper.runMigrationsAndValidate(DB, DATABASE_VERSION, true, *allMigrations)

        db.query("SELECT COUNT(*) FROM messages").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        // v6 的回填：第 2 条的父节点应当是第 1 条，第 1 条是链首。
        db.query("SELECT id, parentId FROM messages ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(0L, cursor.getLong(1))
            assertTrue(cursor.moveToNext())
            assertEquals(2L, cursor.getLong(0))
            assertEquals(1L, cursor.getLong(1))
        }
        // 叶子游标指向最后一条消息，升级后历史窗口不变。
        db.query("SELECT leafMessageId FROM conversations WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2L, cursor.getLong(0))
        }
        // v5 的 FTS 回填仍然生效。
        db.query("SELECT COUNT(*) FROM messages_fts WHERE messages_fts MATCH ?", arrayOf("migration")).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
    }

    @Test
    fun migrate3ToCurrentAddsAttachmentsAndTree() {        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO conversations (id, title, provider, model, systemPrompt, updatedAt) " +
                    "VALUES (1, 'v3 会话', 'DOUBAO', 'doubao-seed', '人设', 100)",
            )
            db.execSQL(
                "INSERT INTO messages (id, conversationId, role, content, reasoning, status, error, createdAt) " +
                    "VALUES (1, 1, 'USER', 'v3 message', '', 'DONE', '', 10)",
            )
        }

        val db = helper.runMigrationsAndValidate(DB, DATABASE_VERSION, true, *allMigrations)

        db.query("SELECT pinned FROM conversations WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM message_attachments").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT parentId FROM messages WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
    }

    @Test
    fun migrate4ToCurrentKeepsUsageColumnsAndIndex() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO conversations (id, title, provider, model, systemPrompt, updatedAt, pinned) " +
                    "VALUES (1, 'v4 会话', 'DEEPSEEK', 'deepseek-flash', '人设', 100, 1)",
            )
            db.execSQL(
                "INSERT INTO messages (id, conversationId, role, content, reasoning, status, error, createdAt) " +
                    "VALUES (1, 1, 'USER', 'v4 migration keyword', '', 'DONE', '', 10)",
            )
        }

        val db = helper.runMigrationsAndValidate(DB, DATABASE_VERSION, true, *allMigrations)

        db.query("SELECT pinned, thinking, leafMessageId FROM conversations WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(1L, cursor.getLong(2))
        }
        db.query("SELECT promptTokens, completionTokens, toolTrace FROM messages WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("", cursor.getString(2))
        }
        assertEquals(1, countFts(db, "migration"))
    }

    @Test
    fun migrate5ToCurrentAddsBranchAndArenaTables() {
        helper.createDatabase(DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO conversations (id, title, provider, model, systemPrompt, updatedAt, pinned, thinking) " +
                    "VALUES (1, 'v5 会话', 'DEEPSEEK', 'deepseek-flash', '人设', 100, 1, 0)",
            )
            db.execSQL(
                "INSERT INTO messages (id, conversationId, role, content, reasoning, status, error, createdAt, " +
                    "promptTokens, completionTokens, toolTrace) " +
                    "VALUES (1, 1, 'USER', 'v5 migration keyword', '', 'DONE', '', 10, 7, 9, '')",
            )
        }

        val db = helper.runMigrationsAndValidate(DB, DATABASE_VERSION, true, MIGRATION_5_6)

        db.query("SELECT parentId FROM messages WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
        db.query("SELECT leafMessageId FROM conversations WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }
        // 擂台表存在且为空，且不污染消息表。
        assertEquals(0, count(db, "arena_runs"))
        assertEquals(0, count(db, "arena_outputs"))
        assertEquals(1, count(db, "messages"))
    }

    @Test
    fun versionConstantMatchesRoomSchema() {
        // @Database 是 BINARY 级保留，运行时反射拿不到，所以直接问 Room 本身：
        // 新建一个库后读 PRAGMA user_version，那就是它实际写入的 schema 版本。
        // 常量偏小会让「库比代码新」的判断漏掉，偏大则会误判并留档重建。
        val probe = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            probe.query("PRAGMA user_version", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(DATABASE_VERSION, cursor.getInt(0))
            }
        } finally {
            probe.close()
        }
    }

    private fun countFts(db: androidx.sqlite.db.SupportSQLiteDatabase, term: String): Int =
        db.query("SELECT COUNT(*) FROM messages_fts WHERE messages_fts MATCH ?", arrayOf(term)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else -1
        }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else -1
        }

    private companion object {
        const val DB = "migration-full.db"
    }
}
