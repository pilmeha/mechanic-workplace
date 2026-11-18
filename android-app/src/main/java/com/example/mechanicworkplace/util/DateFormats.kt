package com.example.mechanicworkplace.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * The contract says ISO 8601, but real examples mix values with and without an offset
 * (`2026-11-12T09:00:00` vs `2026-11-12T09:00:00+05:00`). Dates stay as the original
 * string in DTOs; this helper parses leniently for display and never throws.
 */
object DateFormats {
    private val DISPLAY_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun parse(raw: String?): LocalDateTime? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { OffsetDateTime.parse(value).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value) }.getOrNull()
    }

    /**
     * Returns a formatted date or the original string when parsing fails, so an unknown
     * server format is shown verbatim instead of crashing or showing an empty field.
     */
    fun display(raw: String?, formatter: DateTimeFormatter = DISPLAY_FORMATTER): String {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
        return parse(value)?.format(formatter) ?: value
    }
}
