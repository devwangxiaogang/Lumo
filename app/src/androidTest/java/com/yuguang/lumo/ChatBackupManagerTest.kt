package com.yuguang.lumo

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuguang.lumo.data.BackupException
import com.yuguang.lumo.data.ChatBackupManager
import com.yuguang.lumo.data.local.AppDatabase
import com.yuguang.lumo.data.local.AttachmentEntity
import com.yuguang.lumo.data.local.ChatMediaStore
import com.yuguang.lumo.data.local.ConversationEntity
import com.yuguang.lumo.data.local.MessageEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 备份导入的三条硬约束：
 * 1. 超过预算的包被拒，且不写库；
 * 2. 结构合法但来源更新版本时被拒；
 * 3. 导入中途失败时整体回滚，且不留下孤儿图片。
 */
@RunWith(AndroidJUnit4::class)
class ChatBackupManagerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var manager: ChatBackupManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = ChatBackupManager(
            context = context,
            database = db,
            conversationDao = db.conversationDao(),
            messageDao = db.messageDao(),
            attachmentDao = db.attachmentDao(),
            mediaStore = ChatMediaStore(context),
            json = Json { ignoreUnknownKeys = true; explicitNulls = false },
        )
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("backup-import-") }
            ?.forEach { it.deleteRecursively() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun exportThenImportRestoresTreeAndLeaf() = runBlocking {
        val conversationId = db.conversationDao().insert(
            ConversationEntity(
                title = "带分支的会话",
                provider = "DEEPSEEK",
                model = "deepseek-flash",
                systemPrompt = "人设",
                updatedAt = 100,
            ),
        )
        val user = db.messageDao().insert(
            MessageEntity(
                conversationId = conversationId,
                role = "USER",
                content = "问题",
                status = "DONE",
                createdAt = 10,
            ),
        )
        val firstReply = db.messageDao().insert(
            MessageEntity(
                conversationId = conversationId,
                role = "ASSISTANT",
                content = "第一次回答",
                status = "DONE",
                createdAt = 20,
                parentId = user,
            ),
        )
        val secondReply = db.messageDao().insert(
            MessageEntity(
                conversationId = conversationId,
                role = "ASSISTANT",
                content = "第二次回答",
                status = "DONE",
                createdAt = 30,
                parentId = user,
            ),
        )
        db.conversationDao().setLeaf(conversationId, secondReply)
        db.messageDao().updateUsage(firstReply, 11, 22)

        val file = File(context.cacheDir, "round-trip.zip")
        val uri = Uri.fromFile(file)
        assertEquals(1, manager.exportTo(uri))

        val imported = manager.importFrom(uri)

        assertEquals(1, imported)
        val conversations = db.conversationDao().all()
        assertEquals(2, conversations.size)
        val copy = conversations.first { it.title == "带分支的会话" && it.id != conversationId }
        val messages = db.messageDao().list(copy.id)
        assertEquals(3, messages.size)
        // 同一父节点下两条助手回复：树被原样还原，而不是压成单链。
        val restoredUser = messages.first { it.role == "USER" }
        val replies = messages.filter { it.role == "ASSISTANT" }
        assertEquals(2, replies.size)
        // 导入必然换用新的行 id，所以断言的是「父节点指向本次导入的用户消息」，
        // 而不是备份里的旧 id。
        assertEquals(setOf(restoredUser.id), replies.map { it.parentId }.toSet())
        assertEquals(0L, restoredUser.parentId)
        // 叶子游标指向备份时的激活分支（第二条回复）。
        val restoredLeaf = messages.first { it.content == "第二次回答" }
        assertEquals(restoredLeaf.id, copy.leafMessageId)
    }

    @Test
    fun rejectPackageFromNewerVersion() = runBlocking {
        val file = File(context.cacheDir, "future.zip")
        writeZip(
            file,
            mapOf(
                "backup.json" to """
                    {"version":99,"exportedAt":1,"conversations":[]}
                """.trimIndent(),
            ),
        )

        val error = runCatching { manager.importFrom(Uri.fromFile(file)) }.exceptionOrNull()

        assertTrue(error is BackupException)
        assertTrue(error!!.message.orEmpty().contains("更新版本"))
        assertEquals(0, db.conversationDao().all().size)
    }

    @Test
    fun rejectPackageWithoutIndex() = runBlocking {
        val file = File(context.cacheDir, "noindex.zip")
        writeZip(file, mapOf("media/0.jpg" to "not really an image"))

        val error = runCatching { manager.importFrom(Uri.fromFile(file)) }.exceptionOrNull()

        assertTrue(error is BackupException)
        assertEquals(0, db.conversationDao().all().size)
    }

    @Test
    fun invalidIndexLeavesDatabaseUntouched() = runBlocking {
        val file = File(context.cacheDir, "broken.zip")
        writeZip(file, mapOf("backup.json" to "{ not json"))

        val error = runCatching { manager.importFrom(Uri.fromFile(file)) }.exceptionOrNull()

        assertTrue(error is BackupException)
        assertEquals(0, db.conversationDao().all().size)
        assertEquals(0, db.messageDao().list(0L).size)
    }

    @Test
    fun failedCommitRollsBackLeavingNoConversations() = runBlocking {
        // 角色为空会在写库前被 validate 拦下：这是最容易构造的「合法 ZIP、非法内容」。
        val file = File(context.cacheDir, "bad-role.zip")
        writeZip(
            file,
            mapOf(
                "backup.json" to """
                    {
                      "version": 1,
                      "exportedAt": 1,
                      "conversations": [
                        {
                          "title": "坏会话",
                          "provider": "DEEPSEEK",
                          "model": "deepseek-flash",
                          "systemPrompt": "",
                          "updatedAt": 1,
                          "messages": [{"role": "", "content": "x", "status": "DONE", "createdAt": 1}]
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )

        val error = runCatching { manager.importFrom(Uri.fromFile(file)) }.exceptionOrNull()

        assertTrue(error is BackupException)
        assertEquals(0, db.conversationDao().all().size)
    }

    @Test
    fun mediaFilesAreCleanedUpWhenImportFails() = runBlocking {
        val mediaDir = File(context.filesDir, "chat_media")
        val before = mediaDir.listFiles()?.size ?: 0
        val file = File(context.cacheDir, "media-then-fail.zip")
        writeZip(
            file,
            mapOf(
                "media/0.jpg" to "jpeg-bytes",
                "backup.json" to """
                    {
                      "version": 1,
                      "exportedAt": 1,
                      "conversations": [
                        {
                          "title": "有图但角色缺失",
                          "provider": "DEEPSEEK",
                          "model": "deepseek-flash",
                          "systemPrompt": "",
                          "updatedAt": 1,
                          "messages": [
                            {"role": "", "content": "x", "status": "DONE", "createdAt": 1,
                             "attachments": [{"entry": "media/0.jpg", "mime": "image/jpeg",
                                              "source": "USER"}]}
                          ]
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )

        runCatching { manager.importFrom(Uri.fromFile(file)) }

        assertEquals(before, mediaDir.listFiles()?.size ?: 0)
    }

    private fun writeZip(target: File, entries: Map<String, String>) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            entries.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
