package com.yuguang.lumo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_attachments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                messageId INTEGER NOT NULL,
                conversationId INTEGER NOT NULL,
                localPath TEXT NOT NULL,
                mime TEXT NOT NULL,
                source TEXT NOT NULL,
                remoteUrl TEXT NOT NULL DEFAULT '',
                sortOrder INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(messageId) REFERENCES messages(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_message_attachments_messageId ON message_attachments(messageId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_message_attachments_conversationId ON message_attachments(conversationId)",
        )
    }
}

/** 列表页支持置顶会话。 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v5：Token 用量、深度思考开关、工具轨迹、长期记忆与消息全文索引。
 *
 * FTS 建表语句与 Room 为 `@Fts4(contentEntity = MessageEntity::class)` 生成的完全一致，
 * 四支 content-sync 触发器由 Room 在 onPostMigrate 里建立（迁移前会先删同名触发器），
 * 这里只负责建表并回填历史消息。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN thinking INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN promptTokens INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN completionTokens INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN toolTrace TEXT NOT NULL DEFAULT ''")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `content` TEXT NOT NULL,
                `tags` TEXT NOT NULL,
                `sourceConversationId` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_enabled` ON `memories` (`enabled`)")

        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `messages_fts` USING FTS4(`content` TEXT NOT NULL, content=`messages`)",
        )
        // docid 复用 messages.rowid（= id），把已有消息灌进索引。
        db.execSQL("INSERT INTO `messages_fts`(`docid`, `content`) SELECT `id`, `content` FROM `messages`")
    }
}
