package com.yuguang.lumo.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 图片里的一行：原文与译文。译文可能为空（只做了识别）。 */
data class PhotoLine(val source: String, val target: String)

/** 解析结果。fallback 表示模型没按约定返回结构，内容被当成整段处理。 */
data class PhotoParseResult(val lines: List<PhotoLine>, val fallback: Boolean)

/**
 * 图片文字翻译的容错解析。
 *
 * 视觉模型不一定老老实实返回 JSON：可能带 ```json 围栏、可能用别名键、
 * 也可能直接吐一段纯文本。这里按「结构化优先、文本兜底」的顺序处理，
 * 保证界面上永远有东西可看，而不是一句解析失败。
 */
object PhotoLines {
    fun parse(raw: String, json: Json = Json): PhotoParseResult {
        val text = raw.trim().removeSurrounding("```json", "```").removeSurrounding("```").trim()
        if (text.isEmpty()) return PhotoParseResult(emptyList(), false)
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull()
        val fromStructure = element?.let { collect(it) }.orEmpty()
        if (fromStructure.isNotEmpty()) return PhotoParseResult(fromStructure, false)
        // 没有结构：按行拆，至少能给出原文。
        val lines = text.split('\n')
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotEmpty() }
            .map { PhotoLine(it, "") }
        return PhotoParseResult(lines, lines.isNotEmpty())
    }

    private fun collect(element: JsonElement): List<PhotoLine> = when (element) {
        is JsonArray -> element.mapNotNull(::line)
        is JsonObject -> {
            val nested = listOf("lines", "items", "data", "result", "results")
                .firstNotNullOfOrNull { key -> (element[key] as? JsonArray)?.let { collect(it) } }
            when {
                nested != null -> nested
                else -> listOfNotNull(line(element))
            }
        }
        is JsonPrimitive -> element.contentOrNull?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { listOf(PhotoLine(it, "")) }
            .orEmpty()
    }

    private fun line(element: JsonElement): PhotoLine? = when (element) {
        is JsonObject -> {
            val source = pick(element, "text", "source", "original", "src", "ocr", "line").orEmpty()
            val target = pick(element, "translation", "translated", "target", "dst", "result").orEmpty()
            if (source.isBlank() && target.isBlank()) null else PhotoLine(source, target)
        }
        is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { PhotoLine(it, "") }
        else -> null
    }

    private fun pick(element: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val value = element[key] ?: continue
            val text = (value as? JsonPrimitive)?.contentOrNull?.trim()
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }
}
