package com.jsos.phone.openclaw

object GatewayUrl {
    fun webSocketUrl(host: String, port: Int): String =
        displayUrl(host, port.toString())

    fun displayUrl(host: String, port: String): String {
        val trimmedHost = host.trim().trimEnd('/')
        val trimmedPort = port.trim().ifBlank { "18789" }

        return if (hasWebSocketScheme(trimmedHost)) {
            trimmedHost
        } else {
            "ws://${appendPortIfMissing(trimmedHost, trimmedPort)}"
        }
    }

    fun originUrl(webSocketUrl: String): String {
        val trimmed = webSocketUrl.trim().trimEnd('/')
        val secure = trimmed.startsWith("wss://", ignoreCase = true)
        val withoutScheme = when {
            trimmed.startsWith("wss://", ignoreCase = true) -> trimmed.substring("wss://".length)
            trimmed.startsWith("ws://", ignoreCase = true) -> trimmed.substring("ws://".length)
            else -> trimmed
        }
        val authority = withoutScheme.substringBefore("/")
        val originScheme = if (secure) "https" else "http"
        return "$originScheme://$authority"
    }

    fun isCleartext(webSocketUrl: String): Boolean =
        webSocketUrl.trim().startsWith("ws://", ignoreCase = true)

    private fun hasWebSocketScheme(value: String): Boolean =
        value.startsWith("ws://", ignoreCase = true) ||
            value.startsWith("wss://", ignoreCase = true)

    private fun appendPortIfMissing(value: String, port: String): String =
        if (value.contains(Regex(":\\d+$"))) value else "$value:$port"
}
