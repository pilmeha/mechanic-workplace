package com.example.mechanicworkplace.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DateFormatsTest {

    @Test
    fun `parses datetime with offset`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 12, 9, 0),
            DateFormats.parse("2026-11-12T09:00:00+05:00")
        )
    }

    @Test
    fun `parses datetime without offset`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 12, 9, 0),
            DateFormats.parse("2026-11-12T09:00:00")
        )
    }

    @Test
    fun `display formats known values`() {
        assertEquals("12.11.2026 09:00", DateFormats.display("2026-11-12T09:00:00+05:00"))
        assertEquals("12.11.2026 09:00", DateFormats.display("2026-11-12T09:00:00"))
    }

    @Test
    fun `display falls back to original string`() {
        assertEquals("not-a-date", DateFormats.display("not-a-date"))
        assertEquals("", DateFormats.display(null))
        assertNull(DateFormats.parse("not-a-date"))
    }
}
