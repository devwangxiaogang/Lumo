package com.yuguang.lumo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 当前 schema 版本。
 *
 * 单独提出来是为了让 [DatabaseOpener] 能在不改 Room 注解的前提下判断「库比代码新」；
 * `@Database(version = ...)` 只接受编译期常量，两者必须一致（有 migration 测试守着）。
 */
const val DATABASE_VERSION = 6

/** 会话、消息、附件和翻译记录存在本机。删除会话时消息与附件级联删除。 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        TranslationEntity::class,
        MessageFts::class,
        MemoryEntity::class,
        ArenaRunEntity::class,
        ArenaOutputEntity::class,
    ],
    version = DATABASE_VERSION,
    // 导出 schema 到 app/schemas，迁移才能被 room-testing 验证。
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun translationDao(): TranslationDao

    abstract fun searchDao(): SearchDao

    abstract fun memoryDao(): MemoryDao

    abstract fun arenaDao(): ArenaDao

    abstract fun statsDao(): StatsDao
}
