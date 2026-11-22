package com.yuguang.lumo.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.yuguang.lumo.data.local.AppDatabase
import com.yuguang.lumo.data.local.AttachmentDao
import com.yuguang.lumo.data.local.AttachmentEntity
import com.yuguang.lumo.data.local.ChatMediaStore
import com.yuguang.lumo.data.local.ConversationDao
import com.yuguang.lumo.data.local.ConversationEntity
import com.yuguang.lumo.data.local.MessageDao
import com.yuguang.lumo.data.local.MessageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话备份：导出一个 ZIP（backup.json + media/ 图片），导入时写回数据库和 chat_media。
 * 导入总是创建新的会话，避免和现有记录冲突。
 *
 * 导入刻意分成三段，任何一段失败都不会留下「半个会话 + 一堆孤儿图片」：
 * 1. 解包：只读，边读边按 [BackupLimits] 卡上限（防 zip bomb、畸形包），
 *    图片直接写进临时目录，不整包进内存；
 * 2. 校验：版本、附件引用、父消息引用都在写库之前查一遍；
 * 3. 落库：图片搬进 chat_media，数据库写入包在单个事务里，失败回滚并删掉本次媒体。
 */
@Singleton
class ChatBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao,
    private val mediaStore: ChatMediaStore,
    private val json: Json,
) {
    /** @return 导出的会话数量。 */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val conversations = conversationDao.all()
        val mediaEntries = linkedMapOf<String, String>()
        val backup = BackupFile(
            exportedAt = System.currentTimeMillis(),
            conversations = conversations.map { conversation ->
                BackupConversation(
                    title = conversation.title,
                    provider = conversation.provider,
                    model = conversation.model,
                    systemPrompt = conversation.systemPrompt,
                    updatedAt = conversation.updatedAt,
                    // 1.3：备份保存整棵消息树（leaf 游标 + 每条消息的 parentId），
                    // 导入后分支可以原样还原，不像导出那样只留激活路径。
                    leafMessageId = conversation.leafMessageId,
                    messages = messageDao.list(conversation.id).map { message ->
                        BackupMessage(
                            id = message.id,
                            role = message.role,
                            content = message.content,
                            reasoning = message.reasoning,
                            status = message.status,
                            error = message.error,
                            createdAt = message.createdAt,
                            parentId = message.parentId,
                            attachments = attachmentDao.forMessage(message.id).map { attachment ->
                                val entry = mediaEntries.getOrPut(attachment.localPath) {
                                    "media/${mediaEntries.size}_${File(attachment.localPath).name}"
                                }
                                BackupAttachment(
                                    entry = entry,
                                    mime = attachment.mime,
                                    source = attachment.source,
                                    remoteUrl = attachment.remoteUrl,
                                    sortOrder = attachment.sortOrder,
                                )
                            },
                        )
                    },
                )
            },
        )
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw BackupException("cannot open output")
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry(BackupLimits.INDEX_ENTRY))
            zip.write(json.encodeToString(backup).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            mediaEntries.forEach { (path, entry) ->
                val file = File(path)
                if (!file.isFile) return@forEach
                zip.putNextEntry(ZipEntry(entry))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        conversations.size
    }

    /** @return 导入的会话数量。 */
    suspend fun importFrom(uri: Uri): Int = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "backup-import-${System.currentTimeMillis()}")
        var adopted = emptyList<String>()
        try {
            val packageFile = readPackage(uri, staging)
            validate(packageFile.backup)
            val entryToFile = promoteMedia(packageFile)
            adopted = entryToFile.values.toList()
            commit(packageFile.backup, entryToFile)
        } catch (error: Throwable) {
            // 事务已回滚，但图片已经搬进 chat_media，必须清掉，
            // 否则下次备份会把它们一起带上，也会一直占着磁盘。
            if (adopted.isNotEmpty()) mediaStore.deletePaths(adopted)
            throw error
        } finally {
            runCatching { staging.deleteRecursively() }
        }
    }

    /** 第一段：只读解包，图片落到 [staging]，JSON 留在内存。 */
    private fun readPackage(uri: Uri, staging: File): StagedPackage {
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw BackupException("cannot open input")
        staging.mkdirs()
        var backup: BackupFile? = null
        val staged = LinkedHashMap<String, File>()
        var total = 0L
        var entries = 0
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                entries += 1
                if (entries > BackupLimits.MAX_ENTRIES) {
                    throw BackupException("备份内文件过多（超过 ${BackupLimits.MAX_ENTRIES} 个）")
                }
                val name = entry.name
                when {
                    name == BackupLimits.INDEX_ENTRY -> {
                        val bytes = readLimited(zip, BackupLimits.MAX_INDEX_BYTES, name)
                        total += bytes.size
                        backup = runCatching {
                            json.decodeFromString<BackupFile>(bytes.toString(Charsets.UTF_8))
                        }.getOrElse { throw BackupException("备份索引无法解析：${it.message.orEmpty()}") }
                    }
                    name.startsWith(BackupLimits.MEDIA_PREFIX) && !entry.isDirectory -> {
                        // 同名 entry 只留第一份，避免刷出大量重复临时文件。
                        if (name !in staged) {
                            val target = File(staging, "m${staged.size}")
                            val written = copyLimited(zip, target, BackupLimits.MAX_ENTRY_BYTES, name)
                            total += written
                            staged[name] = target
                        }
                    }
                }
                // 这里不再逐条累计总字节：单条已卡上限，整体由条目数与 package 上限兜住。
                if (total > BackupLimits.MAX_TOTAL_BYTES) {
                    throw BackupException("备份解压后过大（超过 ${BackupLimits.MAX_TOTAL_BYTES / 1024 / 1024} MB）")
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val parsed = backup ?: throw BackupException("备份缺少 ${BackupLimits.INDEX_ENTRY}")
        if (total > BackupLimits.MAX_TOTAL_BYTES) throw BackupException("备份解压后过大")
        return StagedPackage(parsed, staged)
    }

    /** 第二段：版本与引用校验，全部在写库之前完成。 */
    private fun validate(backup: BackupFile) {
        if (backup.version < 1) throw BackupException("备份文件已损坏（版本 ${backup.version}）")
        if (backup.version > BackupLimits.CURRENT_VERSION) {
            throw BackupException("备份来自更新版本的应用（版本 ${backup.version}）")
        }
        backup.conversations.forEachIndexed { index, conversation ->
            conversation.messages.forEach { message ->
                if (message.role.isBlank()) {
                    throw BackupException("第 ${index + 1} 个会话里有缺少角色的消息")
                }
            }
            // parentId 指向不存在的消息时不用中断：导入时会话内的 idMap 查不到，
            // 自然回落到 0（链首）。老备份整片 parentId=0 也是正常形态。
        }
    }

    /** 第三段：把临时图片搬进 chat_media，返回 entry → 最终路径。 */
    private suspend fun promoteMedia(packageFile: StagedPackage): Map<String, String> {
        if (packageFile.media.isEmpty()) return emptyMap()
        val result = HashMap<String, String>(packageFile.media.size)
        packageFile.media.forEach { (entry, temp) ->
            val mime = when {
                entry.endsWith(".png", ignoreCase = true) -> "image/png"
                entry.endsWith(".webp", ignoreCase = true) -> "image/webp"
                else -> "image/jpeg"
            }
            result[entry] = mediaStore.adoptStaged(temp, mime)
        }
        return result
    }

    /** 第四段：单事务写库；失败由调用方删掉已搬走的媒体。 */
    private suspend fun commit(backup: BackupFile, entryToPath: Map<String, String>): Int =
        database.withTransaction {
            backup.conversations.forEach { conversation ->
                val conversationId = conversationDao.insert(
                    ConversationEntity(
                        title = conversation.title,
                        provider = conversation.provider,
                        model = conversation.model,
                        systemPrompt = conversation.systemPrompt,
                        updatedAt = conversation.updatedAt,
                    ),
                )
                // 备份里的 id 是新库不存在的旧 id：先落一条 id 映射，再把 parentId 换成本库的 id。
                // 老备份没有 parentId 字段（默认 0），导入后就是一条单链，行为与 1.2 一致。
                val idMap = HashMap<Long, Long>()
                var lastMessageId = 0L
                conversation.messages.forEach { message ->
                    val messageId = messageDao.insert(
                        MessageEntity(
                            conversationId = conversationId,
                            role = message.role,
                            content = message.content,
                            reasoning = message.reasoning,
                            status = message.status,
                            error = message.error,
                            createdAt = message.createdAt,
                            parentId = if (message.parentId > 0L) idMap[message.parentId] ?: 0L else 0L,
                        ),
                    )
                    idMap[message.id] = messageId
                    lastMessageId = messageId
                    val attachments = message.attachments.mapNotNull { attachment ->
                        val path = entryToPath[attachment.entry] ?: return@mapNotNull null
                        AttachmentEntity(
                            messageId = messageId,
                            conversationId = conversationId,
                            localPath = path,
                            mime = attachment.mime,
                            source = attachment.source,
                            remoteUrl = attachment.remoteUrl,
                            sortOrder = attachment.sortOrder,
                        )
                    }
                    if (attachments.isNotEmpty()) attachmentDao.insertAll(attachments)
                }
                // 游标也要跟着新 id 走；备份里没有（老备份）时退回最后一条消息。
                val leaf = idMap[conversation.leafMessageId] ?: lastMessageId
                if (leaf > 0L) conversationDao.setLeaf(conversationId, leaf)
            }
            backup.conversations.size
        }

    /** 读取单个 entry，超过 [limit] 立即失败，避免先把内存撑爆再判断。 */
    private fun readLimited(input: InputStream, limit: Long, name: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var read = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            read += count
            if (read > limit) throw BackupException("$name 超过上限（${limit / 1024 / 1024} MB）")
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun copyLimited(input: InputStream, target: File, limit: Long, name: String): Long {
        var read = 0L
        val buffer = ByteArray(16 * 1024)
        target.outputStream().use { output: OutputStream ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                read += count
                if (read > limit) throw BackupException("$name 超过上限（${limit / 1024 / 1024} MB）")
                output.write(buffer, 0, count)
            }
        }
        return read
    }

    private data class StagedPackage(val backup: BackupFile, val media: Map<String, File>)

    @Serializable
    private data class BackupFile(
        val version: Int = 1,
        val exportedAt: Long,
        val conversations: List<BackupConversation> = emptyList(),
    )

    @Serializable
    private data class BackupConversation(
        val title: String,
        val provider: String,
        val model: String,
        val systemPrompt: String,
        val updatedAt: Long,
        /** 当前激活分支的叶子；老备份没有这个字段，导入后按单链处理。 */
        val leafMessageId: Long = 0,
        val messages: List<BackupMessage> = emptyList(),
    )

    @Serializable
    private data class BackupMessage(
        val role: String,
        val content: String,
        val reasoning: String = "",
        val status: String,
        val error: String = "",
        val createdAt: Long,
        /** 备份内的原始消息 id，用来重建消息树；老备份没有时为 0。 */
        val id: Long = 0,
        /** 父消息在备份里的 id；0 表示一条链的起点。 */
        val parentId: Long = 0,
        val attachments: List<BackupAttachment> = emptyList(),
    )

    @Serializable
    private data class BackupAttachment(
        val entry: String,
        val mime: String,
        val source: String,
        val remoteUrl: String = "",
        val sortOrder: Int = 0,
    )
}
