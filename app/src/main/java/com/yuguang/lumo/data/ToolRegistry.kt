package com.yuguang.lumo.data

import android.content.Context
import android.os.Build
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.ToolSpec
import com.yuguang.lumo.domain.emptyToolSchema
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地工具注册表。
 *
 * 目前注册四个不依赖第三方服务的工具：时间、四则运算、取网页正文、检索自己的历史消息。
 * `generate_image` 仍在豆包侧单独处理（要 Seedream Key），不放进这里。
 *
 * 设计原则：
 * - 每个工具的执行都是「失败也返回字符串」，让模型能自己纠错或换个说法；
 * - 取网页做 SSRF 防护：拒绝回环 / 私有 / 链路本地地址，并限制响应体大小。
 */
@Singleton
class ToolRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val search: ChatSearch,
    private val json: Json,
) {
    fun specs(): List<ToolSpec> = listOf(
        ToolSpec(
            name = TIME_TOOL,
            description = "Get the current date and time with timezone. Use when the user asks about today, now, or relative dates.",
            parameters = emptyToolSchema(),
        ) { _ -> currentTime() },
        ToolSpec(
            name = CALC_TOOL,
            description = "Evaluate a basic arithmetic expression. Supports numbers, + - * / % ^ and parentheses.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("expression") {
                        put("type", "string")
                        put("description", "The arithmetic expression, e.g. (12+8)*3/4")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("expression")) }
            },
        ) { args -> calculate(args.stringArg("expression")) },
        ToolSpec(
            name = FETCH_TOOL,
            description = "Fetch a web page over http(s) and return its readable text (max 8 KB). Private and loopback addresses are refused.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("url") {
                        put("type", "string")
                        put("description", "Absolute http(s) URL")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("url")) }
            },
        ) { args -> fetchUrl(args.stringArg("url")) },
        ToolSpec(
            name = SEARCH_TOOL,
            description = "Search the user's own chat history on this device and return the best matching snippets.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Keywords to look for")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("query")) }
            },
        ) { args -> searchHistory(args.stringArg("query")) },
    )

    fun spec(name: String): ToolSpec? = specs().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** 执行单个工具；任何异常都变成一句可读的失败说明。 */
    suspend fun execute(name: String, argumentsJson: String): String {
        val spec = spec(name) ?: return "Unknown tool: $name"
        val args = runCatching {
            json.parseToJsonElement(argumentsJson.ifBlank { "{}" }) as? JsonObject
        }.getOrNull() ?: JsonObject(emptyMap())
        return runCatching { spec.execute(args) }
            .getOrElse { error -> "Tool $name failed: ${error.message.orEmpty()}" }
    }

    private fun currentTime(): String {
        // minSdk 是 24，而 java.time 要 API 26（未开 desugaring 时直接 NoClassDefFoundError）。
        // 这里按版本分流：26+ 用 java.time 拿到带时区缩写的完整时间，24/25 退回 Calendar。
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val now = ZonedDateTime.now()
            now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzz")) +
                " (" + now.zone.id + ")"
        } else {
            @Suppress("DEPRECATION")
            val calendar = java.util.Calendar.getInstance()
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz", java.util.Locale.US)
                .format(calendar.time)
            "$stamp (${calendar.timeZone.id})"
        }
    }

    /** 递归下降求值：只接受数字与 + - * / % ^ ( )，其余字符直接拒绝。 */
    private fun calculate(expression: String?): String {
        val source = expression?.trim().orEmpty()
        if (source.isEmpty()) return "Empty expression"
        if (source.length > MAX_EXPRESSION) return "Expression too long"
        if (!source.all { it.isDigit() || it in "+-*/%^(). " }) return "Illegal characters"
        return runCatching {
            val parser = ExpressionParser(source)
            val value = parser.parse()
            if (value.isNaN() || value.isInfinite()) "Math error" else stripTrailingZero(value)
        }.getOrElse { "Cannot evaluate: ${it.message.orEmpty()}" }
    }

    private fun stripTrailingZero(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private suspend fun fetchUrl(url: String?): String = withContext(Dispatchers.IO) {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return@withContext "Empty URL"
        val parsed = runCatching { URL(raw) }.getOrNull() ?: return@withContext "Invalid URL"
        if (parsed.protocol != "http" && parsed.protocol != "https") {
            return@withContext "Only http(s) is supported"
        }
        // SSRF 防护：解析出的每个地址都必须是公网地址。
        val addresses = runCatching { InetAddress.getAllByName(parsed.host) }.getOrElse {
            return@withContext "Cannot resolve host"
        }
        if (addresses.isEmpty() || addresses.any { isBlocked(it) }) {
            return@withContext "Refused: private or loopback address"
        }
        val request = Request.Builder()
            .url(raw)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "HTTP ${response.code}"
                val body = response.body
                val charset = body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                val stream = body?.byteStream() ?: return@withContext "Empty response"
                val buffer = ByteArray(MAX_BODY_BYTES)
                var read = 0
                stream.use { input ->
                    while (read < buffer.size) {
                        val count = input.read(buffer, read, buffer.size - read)
                        if (count <= 0) break
                        read += count
                    }
                }
                val text = String(buffer, 0, read, charset)
                stripHtml(text)
            }
        }.getOrElse { "Fetch failed: ${it.message.orEmpty()}" }
    }

    private suspend fun searchHistory(query: String?): String {
        val keyword = query?.trim().orEmpty()
        if (keyword.isEmpty()) return "Empty query"
        // 检索是持续观察的 Flow，这里只要当前快照，取第一个结果即可。
        val rows = runCatching { search.search(keyword).first() }.getOrNull()
            ?: return "Search failed"
        if (rows.isEmpty()) return "No matching messages"
        return rows.take(MAX_HITS).joinToString("\n") { row ->
            "${row.title.ifBlank { "(untitled)" }}: ${row.snippet.replace('\n', ' ').take(160)}"
        }
    }

    private fun isBlocked(address: InetAddress): Boolean =
        address.isLoopbackAddress ||
            address.isSiteLocalAddress ||
            address.isLinkLocalAddress ||
            address.isAnyLocalAddress ||
            address.isMulticastAddress

    private fun stripHtml(raw: String): String = raw
        .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?s)<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_BODY_BYTES / 2)

    private fun JsonObject.stringArg(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    /** 只做四则运算的小型递归下降解析器，避免引入脚本引擎。 */
    private class ExpressionParser(private val source: String) {
        private var index = 0

        fun parse(): Double {
            val value = expression()
            skipSpaces()
            if (index < source.length) error("Unexpected '${source[index]}'")
            return value
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '+' -> {
                        index++
                        value += term()
                    }
                    '-' -> {
                        index++
                        value -= term()
                    }
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '*' -> {
                        index++
                        value *= power()
                    }
                    '/' -> {
                        index++
                        val divisor = power()
                        if (divisor == 0.0) error("division by zero")
                        value /= divisor
                    }
                    '%' -> {
                        index++
                        val divisor = power()
                        if (divisor == 0.0) error("division by zero")
                        value %= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            skipSpaces()
            if (peek() == '^') {
                index++
                return Math.pow(base, power())
            }
            return base
        }

        private fun unary(): Double {
            skipSpaces()
            return when (peek()) {
                '-' -> {
                    index++
                    -unary()
                }
                '+' -> {
                    index++
                    unary()
                }
                else -> primary()
            }
        }

        private fun primary(): Double {
            skipSpaces()
            if (peek() == '(') {
                index++
                val value = expression()
                skipSpaces()
                if (peek() != ')') error("missing ')'")
                index++
                return value
            }
            val start = index
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
            if (start == index) error("unexpected end")
            return source.substring(start, index).toDouble()
        }

        private fun peek(): Char? = if (index < source.length) source[index] else null

        private fun skipSpaces() {
            while (index < source.length && source[index] == ' ') index++
        }
    }

    private companion object {
        const val TIME_TOOL = "get_time"
        const val CALC_TOOL = "calculate"
        const val FETCH_TOOL = "fetch_url"
        const val SEARCH_TOOL = "search_my_chats"
        const val MAX_EXPRESSION = 200
        const val MAX_BODY_BYTES = 8 * 1024
        const val MAX_HITS = 5
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) Lumo"
    }
}
