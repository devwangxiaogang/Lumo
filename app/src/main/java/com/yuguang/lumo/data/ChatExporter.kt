package com.yuguang.lumo.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.yuguang.lumo.data.local.AttachmentDao
import com.yuguang.lumo.data.local.ConversationDao
import com.yuguang.lumo.data.local.MessageDao
import com.yuguang.lumo.domain.model.AttachmentSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把会话导成可读文档。
 *
 * 两种格式：
 * - Markdown：轻量、可进版本库，图片以本地绝对路径引用（不内联，保持文件小）；
 * - HTML：单文件、图片 base64 内联，浏览器打开后可直接「打印成 PDF」当长图用。
 *
 * 与 [ChatBackupManager] 的分工：备份是「能导回去还原」，导出是「能给人看」。
 */
@Singleton
class ChatExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao,
) {
    enum class Format(val mime: String, val extension: String) {
        MARKDOWN("text/markdown", "md"),
        HTML("text/html", "html"),
    }

    /** @return 导出的消息条数。 */
    suspend fun export(conversationId: Long, uri: Uri, format: Format): Int =
        withContext(Dispatchers.IO) {
            val conversation = conversationDao.get(conversationId) ?: return@withContext 0
            val messages = messageDao.list(conversationId)
            val attachments = messages.associate { message ->
                message.id to attachmentDao.forMessage(message.id)
            }
            val writer = context.contentResolver.openOutputStream(uri)?.let { output ->
                OutputStreamWriter(output, Charsets.UTF_8)
            } ?: error(context.getString(com.yuguang.lumo.R.string.export_failed))
            writer.use {
                when (format) {
                    Format.MARKDOWN -> writeMarkdown(it, conversation, messages, attachments)
                    Format.HTML -> writeHtml(it, conversation, messages, attachments)
                }
            }
            messages.size
        }

    private fun writeMarkdown(
        out: Appendable,
        conversation: com.yuguang.lumo.data.local.ConversationEntity,
        messages: List<com.yuguang.lumo.data.local.MessageEntity>,
        attachments: Map<Long, List<com.yuguang.lumo.data.local.AttachmentEntity>>,
    ) {
        out.appendLine("# ${conversation.title.ifBlank { "对话" }}")
        out.appendLine()
        out.appendLine("- 服务商：`${conversation.provider}`")
        out.appendLine("- 模型：`${conversation.model}`")
        out.appendLine("- 导出时间：${formatTime(System.currentTimeMillis())}")
        out.appendLine()
        if (conversation.systemPrompt.isNotBlank()) {
            out.appendLine("> ${conversation.systemPrompt.replace("\n", "\n> ")}")
            out.appendLine()
        }
        messages.forEach { message ->
            out.appendLine("## ${roleLabel(message.role)}")
            out.appendLine()
            if (message.content.isNotBlank()) {
                out.appendLine(message.content)
                out.appendLine()
            }
            attachments[message.id].orEmpty().forEach { attachment ->
                if (attachment.source == AttachmentSource.GENERATED.name) {
                    out.appendLine("![生成图片](${File(attachment.localPath).absolutePath})")
                } else {
                    out.appendLine("![附图](${File(attachment.localPath).absolutePath})")
                }
                out.appendLine()
            }
            if (message.reasoning.isNotBlank()) {
                out.appendLine("<details><summary>思考过程</summary>")
                out.appendLine()
                out.appendLine(message.reasoning)
                out.appendLine()
                out.appendLine("</details>")
                out.appendLine()
            }
        }
    }

    private fun writeHtml(
        out: Appendable,
        conversation: com.yuguang.lumo.data.local.ConversationEntity,
        messages: List<com.yuguang.lumo.data.local.MessageEntity>,
        attachments: Map<Long, List<com.yuguang.lumo.data.local.AttachmentEntity>>,
    ) {
        val title = escape(conversation.title.ifBlank { "对话" })
        out.appendLine(
            """
            <!doctype html>
            <html lang="zh-CN"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>$title</title>
            <style>
              :root { color-scheme: light dark; }
              body { font: 16px/1.7 -apple-system, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                     max-width: 760px; margin: 0 auto; padding: 32px 20px 64px; }
              h1 { font-size: 24px; margin: 0 0 4px; }
              .meta { color: #888; font-size: 13px; margin-bottom: 28px; }
              .msg { margin: 0 0 22px; }
              .who { font-size: 13px; font-weight: 600; color: #666; margin-bottom: 6px; }
              .user .who { color: #2f6feb; }
              .body { white-space: pre-wrap; word-break: break-word; }
              .bubble { padding: 12px 14px; border-radius: 12px; background: #f5f6f8; }
              .user .bubble { background: #eaf1fe; }
              img { max-width: 100%; border-radius: 10px; margin-top: 8px; }
              details { margin-top: 8px; color: #777; font-size: 14px; }
              @media (prefers-color-scheme: dark) {
                body { background: #14161a; color: #e6e8ec; }
                .bubble { background: #1d2027; }
                .user .bubble { background: #17233a; }
                .who { color: #9aa0aa; }
              }
            </style></head><body>
            <h1>$title</h1>
            <div class="meta">${escape(conversation.provider)} · ${escape(conversation.model)}
              · ${escape(formatTime(System.currentTimeMillis()))}</div>
            """.trimIndent(),
        )
        messages.forEach { message ->
            val user = message.role == "USER"
            out.appendLine("<div class=\"msg ${if (user) "user" else "assistant"}\">")
            out.appendLine("<div class=\"who\">${escape(roleLabel(message.role))}</div>")
            out.appendLine("<div class=\"bubble\">")
            if (message.content.isNotBlank()) {
                out.appendLine("<div class=\"body\">${escape(message.content)}</div>")
            }
            attachments[message.id].orEmpty().forEach { attachment ->
                val dataUrl = inlineImage(attachment.localPath, attachment.mime)
                if (dataUrl != null) {
                    out.appendLine("<img alt=\"附件\" src=\"$dataUrl\">")
                }
            }
            if (message.reasoning.isNotBlank()) {
                out.appendLine("<details><summary>思考过程</summary>")
                out.appendLine("<div class=\"body\">${escape(message.reasoning)}</div>")
                out.appendLine("</details>")
            }
            out.appendLine("</div></div>")
        }
        out.appendLine("</body></html>")
    }

    /** 单文件 HTML 的图片内联；超过 2 MB 的图跳过，避免导出一个几十兆的文档。 */
    private fun inlineImage(path: String, mime: String): String? {
        val file = File(path)
        if (!file.isFile || file.length() > MAX_INLINE_BYTES) return null
        val encoded = runCatching {
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }.getOrNull() ?: return null
        return "data:${mime.ifBlank { "image/jpeg" }};base64,$encoded"
    }

    private fun roleLabel(role: String): String = when (role) {
        "USER" -> "我"
        "ASSISTANT" -> "助手"
        "SYSTEM" -> "系统"
        else -> role
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun formatTime(millis: Long): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return format.format(java.util.Date(millis))
    }

    private fun Appendable.appendLine(text: String = "") {
        append(text)
        append('\n')
    }

    private companion object {
        const val MAX_INLINE_BYTES = 2L * 1024 * 1024
    }
}
