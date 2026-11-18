package com.example.mechanicworkplace.data.remote.onecmobile

/**
 * Redacts secrets from any text that could reach a log.
 *
 * The HTTP client only logs method/path/status, but this helper is kept and tested so that
 * accidental future logging of headers or bodies cannot leak the Basic header, password,
 * base64 payload, or bearer tokens.
 */
object LogSanitizer {
    private val patterns: List<Pair<Regex, String>> = listOf(
        Regex("(?i)(authorization\\s*:\\s*(?:basic|bearer)\\s+)[^\\s,;]+") to "$1<redacted>",
        Regex("(?i)(\"password\"\\s*:\\s*\")[^\"]*(\")") to "$1<redacted>$2",
        Regex("(?i)(\"contentBase64\"\\s*:\\s*\")[^\"]*(\")") to "$1<redacted>$2",
        Regex("(?i)(\\bbasic\\s+)[A-Za-z0-9+/=]{8,}") to "$1<redacted>",
        Regex("(?i)(\\bbearer\\s+)[A-Za-z0-9._~+/=-]{8,}") to "$1<redacted>"
    )

    fun redact(text: String): String =
        patterns.fold(text) { acc, (pattern, replacement) ->
            pattern.replace(acc, replacement)
        }
}
