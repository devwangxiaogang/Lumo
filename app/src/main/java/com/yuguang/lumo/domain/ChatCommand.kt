package com.yuguang.lumo.domain

import androidx.annotation.StringRes
import com.yuguang.lumo.R

/**
 * 斜杠指令：在客户端解释，不进入模型上下文，也不消耗 token。
 *
 * 解析只认首词，未命中命令表的输入一律返回 null（按普通消息发送），
 * 因此含 `/` 的路径、分数等文本不会被误拦。
 */
sealed interface ChatCommand {
    data class Draw(val prompt: String) : ChatCommand

    data class Translate(val text: String) : ChatCommand

    data class Summary(val text: String) : ChatCommand

    data class Model(val query: String) : ChatCommand

    data object Voice : ChatCommand

    data object NewChat : ChatCommand

    data object Help : ChatCommand

    data object Unknown : ChatCommand

    companion object {
        /** 面板用的指令清单；insert 是点选后填回输入框的模板。 */
        fun specs(): List<ChatCommandSpec> = listOf(
            ChatCommandSpec("/draw", R.string.cmd_draw, R.string.cmd_draw_detail, "/draw ", listOf("画图", "draw", "image")),
            ChatCommandSpec("/translate", R.string.cmd_translate, R.string.cmd_translate_detail, "/translate ", listOf("翻译", "translate")),
            ChatCommandSpec("/summary", R.string.cmd_summary, R.string.cmd_summary_detail, "/summary ", listOf("总结", "summary", "summarize")),
            ChatCommandSpec("/model", R.string.cmd_model, R.string.cmd_model_detail, "/model ", listOf("模型", "model")),
            ChatCommandSpec("/voice", R.string.cmd_voice, R.string.cmd_voice_detail, "/voice", listOf("语音", "voice")),
            ChatCommandSpec("/new", R.string.cmd_new, R.string.cmd_new_detail, "/new", listOf("新对话", "new")),
            ChatCommandSpec("/help", R.string.cmd_help, R.string.cmd_help_detail, "/help", listOf("帮助", "help")),
        )

        /** 输入框是否已经进入「指令模式」（只用于显示面板，不代表一定能解析）。 */
        fun looksLikeCommand(raw: String): Boolean = raw.trimStart().startsWith("/")

        /** 解析一条输入；返回 null 表示这不是指令，应按普通消息发送。 */
        fun parse(raw: String): ChatCommand? {
            val text = raw.trim()
            if (!looksLikeCommand(text) || text.length < 2) return null
            val head = text.substringBefore(' ')
            val token = head.lowercase()
            val argument = text.substring(head.length).trim()
            return when (token) {
                "/draw", "/画图", "/画" -> Draw(argument)
                "/translate", "/翻译" -> Translate(argument)
                "/summary", "/summarize", "/总结" -> Summary(argument)
                "/model", "/模型", "/换模型" -> Model(argument)
                "/voice", "/语音" -> Voice
                "/new", "/新对话" -> NewChat
                "/help", "/帮助", "/?" -> Help
                else -> Unknown
            }
        }
    }
}

/** 指令面板的一行。 */
data class ChatCommandSpec(
    val trigger: String,
    @StringRes val labelRes: Int,
    @StringRes val detailRes: Int,
    /** 点选后写回输入框的模板，例如 `/draw `。 */
    val insert: String,
    /** 中文别名与英文关键词，供面板前缀匹配。 */
    val keywords: List<String>,
)
