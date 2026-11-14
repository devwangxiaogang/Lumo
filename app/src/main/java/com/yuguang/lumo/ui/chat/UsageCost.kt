package com.yuguang.lumo.ui.chat

import com.yuguang.lumo.domain.model.ProviderId
import java.util.Locale

/**
 * Token 费用的粗估。
 *
 * 价格写死成常量表：只在界面上给个量级，不做账单依据。
 * 免费车道恒为 0；表里没有的模型返回 0 并让界面显示「—」。
 * 单位：美元 / 每百万 token。
 */
private data class Price(val promptPerMillion: Double, val completionPerMillion: Double)

private val prices: Map<String, Price> = mapOf(
    // DeepSeek 官方
    "deepseek-flash" to Price(0.10, 0.30),
    "deepseek-v4-pro" to Price(0.60, 2.20),
    // 方舟 Seed
    "doubao-seed-2-1-lite-260915" to Price(0.15, 0.60),
    "doubao-seed-evolving" to Price(0.15, 0.60),
    "doubao-seed-2-1-turbo-260628" to Price(0.35, 1.40),
    "doubao-seed-2-0-mini-260428" to Price(0.10, 0.40),
    "doubao-seed-2-0-lite-260428" to Price(0.15, 0.60),
    // 方舟上的第三方
    "deepseek-v4-1-flash-260910" to Price(0.30, 1.20),
    "deepseek-v4-pro-ga-260813" to Price(0.60, 2.20),
    "deepseek-v4-flash-ga-260731" to Price(0.20, 0.80),
    "glm-5-3-flash-260828" to Price(0.20, 0.80),
    "glm-5-2-260617" to Price(0.40, 1.60),
)

fun estimateCost(provider: ProviderId?, model: String?, promptTokens: Int, completionTokens: Int): Double {
    if (provider == ProviderId.FREE) return 0.0
    val price = model?.let { prices[it] } ?: return 0.0
    return promptTokens / 1_000_000.0 * price.promptPerMillion +
        completionTokens / 1_000_000.0 * price.completionPerMillion
}

/** 不足一美分时显示 4 位小数，避免全部显示成 ¥0.00。 */
fun formatCost(usd: Double): String {
    if (usd <= 0.0) return "¥0.00"
    val cny = usd * 7.2
    return if (cny < 0.01) {
        "¥" + String.format(Locale.US, "%.4f", cny)
    } else {
        "¥" + String.format(Locale.US, "%.2f", cny)
    }
}
