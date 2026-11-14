package com.yuguang.lumo.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 一个可供模型调用的本地工具。
 *
 * 参数用 JSON Schema 描述，执行结果一律是字符串——失败也返回字符串，
 * 让模型看得懂并继续这一轮，而不是把整次请求打挂。
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val execute: suspend (JsonObject) -> String,
)

/** 便捷构造一个「无参数」的工具 schema。 */
fun emptyToolSchema(): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject { })
}
