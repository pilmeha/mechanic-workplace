package com.example.mechanicworkplace.data.remote.onecmobile

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BigDecimalSerializerTest {

    @Serializable
    data class Holder(
        @Serializable(with = BigDecimalSerializer::class)
        val amount: BigDecimal
    )

    @Test
    fun `serializes with two decimal places`() {
        val encoded = OneCMobileJson.encodeToString(
            Holder.serializer(),
            Holder(BigDecimal("12500.00"))
        )
        assertEquals("""{"amount":12500.00}""", encoded)
    }

    @Test
    fun `decodes number without precision loss`() {
        val decoded = OneCMobileJson.decodeFromString(
            Holder.serializer(),
            """{"amount":12500.00}"""
        )
        assertEquals("12500.00", decoded.amount.toPlainString())
        assertEquals(2, decoded.amount.scale())
    }

    @Test
    fun `decodes quoted decimal as fallback`() {
        val decoded = OneCMobileJson.decodeFromString(
            Holder.serializer(),
            """{"amount":"0.80"}"""
        )
        assertEquals("0.80", decoded.amount.toPlainString())
    }

    @Test
    fun `rejects malformed decimal`() {
        val error = runCatching {
            OneCMobileJson.decodeFromString(Holder.serializer(), """{"amount":"abc"}""")
        }.exceptionOrNull()
        assertTrue(error != null)
    }
}
