package io.github.plumblossomcat.torbox.core.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/*
 * The TorBox API is loosely typed in practice: numeric fields occasionally arrive as strings,
 * booleans as 0/1, and ids as either. These serializers accept every shape we have seen and
 * fall back to a neutral value instead of failing the whole response.
 */

typealias LLong = @Serializable(with = LenientLongSerializer::class) Long
typealias LDouble = @Serializable(with = LenientDoubleSerializer::class) Double
typealias LBool = @Serializable(with = LenientBooleanSerializer::class) Boolean
typealias LString = @Serializable(with = LenientStringSerializer::class) String

private fun Decoder.element(): JsonElement =
    (this as? JsonDecoder)?.decodeJsonElement() ?: error("TorBox models require JSON decoding")

private fun JsonElement.primitiveOrNull(): JsonPrimitive? = when (this) {
    is JsonNull, is JsonObject, is JsonArray -> null
    is JsonPrimitive -> this
}

object LenientLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
    override fun deserialize(decoder: Decoder): Long {
        val p = decoder.element().primitiveOrNull() ?: return 0L
        return p.longOrNull
            ?: p.doubleOrNull?.toLong()
            ?: p.content.trim().toDoubleOrNull()?.toLong()
            ?: if (p.booleanOrNull == true) 1L else 0L
    }
}

object LenientDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
    override fun deserialize(decoder: Decoder): Double {
        val p = decoder.element().primitiveOrNull() ?: return 0.0
        return p.doubleOrNull ?: p.content.trim().toDoubleOrNull() ?: 0.0
    }
}

object LenientBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
    override fun deserialize(decoder: Decoder): Boolean {
        val p = decoder.element().primitiveOrNull() ?: return false
        p.booleanOrNull?.let { return it }
        p.longOrNull?.let { return it != 0L }
        return when (p.content.trim().lowercase()) {
            "true", "yes", "1" -> true
            else -> false
        }
    }
}

object LenientStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String =
        decoder.element().primitiveOrNull()?.content ?: ""
}
