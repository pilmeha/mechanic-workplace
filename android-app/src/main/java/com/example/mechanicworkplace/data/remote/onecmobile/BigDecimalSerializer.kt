package com.example.mechanicworkplace.data.remote.onecmobile

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.math.BigDecimal

/**
 * Money/quantity serializer.
 *
 * 1C sends amounts as JSON numbers with two decimals (12500.00). We decode them into
 * [BigDecimal] without going through Double, and write them back as unquoted numbers while
 * preserving the scale (e.g. 12500.00, not 12500.0).
 *
 * JsonUnquotedLiteral is used instead of JsonPrimitive because the latter normalizes some
 * BigDecimal values through Number.toString implementations that lose trailing zeros.
 */
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OneCMobileBigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
        } else {
            encoder.encodeString(value.toPlainString())
        }
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive) {
                return element.content.toBigDecimalOrThrow()
            }
            throw SerializationException("Expected a JSON number for BigDecimal, got: $element")
        }
        return decoder.decodeString().toBigDecimalOrThrow()
    }

    private fun String.toBigDecimalOrThrow(): BigDecimal =
        try {
            BigDecimal(this)
        } catch (t: NumberFormatException) {
            throw SerializationException("Invalid decimal value: $this", t)
        }
}
