package com.yuguang.lumo.data.remote

import com.yuguang.lumo.domain.model.ProviderId

/** 设置里可以只填服务根地址，也可以填完整的 chat/completions 地址。 */
object EndpointUrls {
    /**
     * 非本机的 `http://` 自动升为 `https://`，降低密钥打到明文 HTTP 端点的风险。
     * 回环与私有网段仍允许 HTTP：本地网关（Ollama、LM Studio、桌面端转发口）都走明文回环/局域网。
     */
    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) return trimmed
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://")) return trimmed
        val host = lower.removePrefix("http://").substringBefore('/').substringBefore(':')
        return if (isLocalHost(host)) trimmed else "https://" + trimmed.substring(7)
    }

    /** 回环、私有网段、`.local` 与 Android 模拟器宿主地址。 */
    fun isLocalHost(host: String): Boolean {
        if (host.isBlank()) return false
        if (host == "localhost" || host == "[::1]" || host == "::1") return true
        if (host == "10.0.2.2" || host == "10.0.3.2") return true
        if (host.endsWith(".local")) return true
        if (host == "127.0.0.1") return true
        if (host.startsWith("127.")) return true
        if (host.startsWith("192.168.")) return true
        if (host.startsWith("10.")) return true
        val blocks = host.split('.')
        if (blocks.size == 4) {
            val second = blocks[1].toIntOrNull() ?: return false
            if (blocks[0] == "172" && second in 16..31) return true
        }
        return false
    }

    fun root(baseUrl: String, provider: ProviderId): String {
        val trimmed = normalizeBaseUrl(baseUrl).ifBlank { provider.defaultBaseUrl }.trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed.removeSuffix("/chat/completions")
            trimmed.endsWith("/images/generations") -> trimmed.removeSuffix("/images/generations")
            trimmed.endsWith("/responses") -> trimmed.removeSuffix("/responses")
            trimmed.endsWith("/models") -> trimmed.removeSuffix("/models")
            else -> trimmed
        }
    }

    fun chatCompletions(baseUrl: String, provider: ProviderId): String =
        "${root(baseUrl, provider)}/chat/completions"

    fun models(baseUrl: String, provider: ProviderId): String =
        "${root(baseUrl, provider)}/models"

    fun imageGenerations(baseUrl: String, provider: ProviderId = ProviderId.DOUBAO): String =
        "${root(baseUrl, provider)}/images/generations"
}
