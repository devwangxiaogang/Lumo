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

/**
 * v6：会话分支（消息树）+ 多模型擂台。
 *
 * 迁移策略：
 * - `messages.parentId` 默认 0，随后按会话内 (createdAt, id) 顺序回填成单链；
 * - `conversations.leafMessageId` 回填为每个会话最大的消息 id，升级后历史窗口与 1.2 完全一致；
 * - 擂台用独立两张表，不写 messages / conversations，也不进 FTS。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `parentId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_parentId` ON `messages` (`parentId`)")
        db.execSQL("ALTER TABLE `conversations` ADD COLUMN `leafMessageId` INTEGER NOT NULL DEFAULT 0")

        // 单链化：每条消息的父节点是同一会话里时间上的前一条消息。
        db.execSQL(
            """
            UPDATE `messages` SET `parentId` = COALESCE((
                SELECT p.`id` FROM `messages` p
                WHERE p.`conversationId` = `messages`.`conversationId`
                  AND (p.`createdAt` < `messages`.`createdAt`
                       OR (p.`createdAt` = `messages`.`createdAt` AND p.`id` < `messages`.`id`))
                ORDER BY p.`createdAt` DESC, p.`id` DESC
                LIMIT 1
            ), 0)
            """.trimIndent(),
        )
        // 叶子游标：会话最后一条消息，历史窗口不因升级而改变。
        db.execSQL(
            """
            UPDATE `conversations` SET `leafMessageId` = COALESCE((
                SELECT m.`id` FROM `messages` m
                WHERE m.`conversationId` = `conversations`.`id`
                ORDER BY m.`createdAt` DESC, m.`id` DESC
                LIMIT 1
            ), 0)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `arena_runs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `prompt` TEXT NOT NULL,
                `providerA` TEXT NOT NULL,
                `modelA` TEXT NOT NULL,
                `providerB` TEXT NOT NULL,
                `modelB` TEXT NOT NULL,
                `winner` TEXT NOT NULL,
                `votedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `arena_outputs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `runId` INTEGER NOT NULL,
                `side` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `reasoning` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `error` TEXT NOT NULL,
                `ttftMs` INTEGER NOT NULL,
                `totalMs` INTEGER NOT NULL,
                `promptTokens` INTEGER NOT NULL,
                `completionTokens` INTEGER NOT NULL,
                FOREIGN KEY(`runId`) REFERENCES `arena_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_arena_outputs_runId` ON `arena_outputs` (`runId`)",
        )
    }
}
