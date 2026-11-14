package com.yuguang.lumo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuguang.lumo.data.local.AppDatabase
import com.yuguang.lumo.data.local.MIGRATION_2_3
import com.yuguang.lumo.data.local.MIGRATION_3_4
import com.yuguang.lumo.data.local.MIGRATION_4_5
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 迁移 4→5 的真机测试。
 *
 * 重点验证三件事：
 * 1. 新增列（thinking / promptTokens / completionTokens / toolTrace）存在且默认值正确；
 * 2. FTS 表能把迁移前的历史消息回填进去；
 * 3. Room 在 onPostMigrate 建好的触发器，能让后续插入的消息也进索引。
 */
@RunWith(AndroidJUnit4::class)
class MigrationV5Test {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val dbName = "migration-4-5.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        createVersion4Database(context.getDatabasePath(dbName))
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate4To5AddsUsageColumnsAndFullTextIndex() {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        try {
            db.openHelper.writableDatabase

            // 历史消息仍在，新列取默认值。
            db.openHelper.readableDatabase.query(
                "SELECT thinking FROM conversations WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.openHelper.readableDatabase.query(
                "SELECT promptTokens, completionTokens, toolTrace FROM messages WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals("", cursor.getString(2))
            }

            // 迁移前的消息已被回填进 FTS。
            db.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM messages_fts WHERE messages_fts MATCH ?",
                arrayOf("migration"),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }

            // 触发器生效：迁移后新插入的消息也会进索引。
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO messages (conversationId, role, content, reasoning, status, error, createdAt) " +
                    "VALUES (1, 'USER', 'after migration keyword', '', 'DONE', '', 1)",
            )
            db.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM messages_fts WHERE messages_fts MATCH ?",
                arrayOf("migration"),
            ).use { cursor ->
                // 两条都含 migration：一条来自回填，一条来自触发器。
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }

            db.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(5, cursor.getInt(0))
            }
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    /** 建一个 v4 库：含 pinned 列，不含 v5 的新列与 FTS/memories。 */
    private fun createVersion4Database(path: File) {
        path.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `conversations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `provider` TEXT NOT NULL, `model` TEXT NOT NULL, " +
                    "`systemPrompt` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `pinned` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`conversationId` INTEGER NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                    "`reasoning` TEXT NOT NULL, `status` TEXT NOT NULL, `error` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `message_attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`messageId` INTEGER NOT NULL, `conversationId` INTEGER NOT NULL, `localPath` TEXT NOT NULL, " +
                    "`mime` TEXT NOT NULL, `source` TEXT NOT NULL, `remoteUrl` TEXT NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_message_attachments_messageId` " +
                    "ON `message_attachments` (`messageId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_message_attachments_conversationId` " +
                    "ON `message_attachments` (`conversationId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `translations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `sourceLang` TEXT NOT NULL, `targetLang` TEXT NOT NULL, " +
                    "`sourceText` TEXT NOT NULL, `targetText` TEXT NOT NULL, `option` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "INSERT INTO conversations (id, title, provider, model, systemPrompt, updatedAt, pinned) " +
                    "VALUES (1, '旧会话', 'DEEPSEEK', 'deepseek-flash', '你是一个有帮助的助手。', 0, 0)",
            )
            db.execSQL(
                "INSERT INTO messages (id, conversationId, role, content, reasoning, status, error, createdAt) " +
                    "VALUES (1, 1, 'USER', 'before migration keyword', '', 'DONE', '', 0)",
            )
            db.version = 4
        } finally {
            db.close()
        }
    }
}
