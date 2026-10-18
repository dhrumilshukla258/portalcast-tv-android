package com.portalcast.tv.data.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * The one JSON configuration for the whole app.
 *
 * The server's response envelopes are untyped `any` on the server side and drift
 * without versioning (see docs/api-contract.md §1), so every model must survive
 * unknown keys, missing keys, nulls where a value was expected, and ids that are
 * sometimes a string and sometimes a number.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false   // null fields are omitted on encode (preferences PUT must not send nulls)
    encodeDefaults = false
}

/** A string field that the server may send as a number, bool, or string. */
typealias LenientStr = @Serializable(with = LenientStringSerializer::class) String

/** An int field that may arrive as `12`, `"12"`, `12.0`, or `"12.0"`. */
typealias LenientInt = @Serializable(with = LenientIntSerializer::class) Int

/** A double field that may arrive as a number or a numeric string. */
typealias LenientDouble = @Serializable(with = LenientDoubleSerializer::class) Double

/** A boolean that may arrive as `true`, `1`, `"1"`, `"true"`. Used for `is_series`-style flags. */
typealias LenientBool = @Serializable(with = LenientBoolSerializer::class) Boolean

object LenientStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val el = json.decodeJsonElement()) {
            is JsonPrimitive -> if (el is JsonNull) "" else el.content
            else -> el.toString()
        }
    }
}

object LenientIntSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
    override fun deserialize(decoder: Decoder): Int {
        val json = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val el = json.decodeJsonElement()
        val prim = el as? JsonPrimitive ?: return 0
        if (prim is JsonNull) return 0
        return prim.content.trim().toDoubleOrNull()?.toInt() ?: 0
    }
}

object LenientDoubleSerializer : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
    override fun deserialize(decoder: Decoder): Double {
        val json = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val prim = json.decodeJsonElement() as? JsonPrimitive ?: return 0.0
        if (prim is JsonNull) return 0.0
        return prim.content.trim().toDoubleOrNull() ?: 0.0
    }
}

object LenientBoolSerializer : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("LenientBool", PrimitiveKind.BOOLEAN)
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
    override fun deserialize(decoder: Decoder): Boolean {
        val json = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val prim = json.decodeJsonElement() as? JsonPrimitive ?: return false
        if (prim is JsonNull) return false
        return when (prim.content.trim().lowercase()) {
            "true", "1", "yes" -> true
            else -> prim.content.trim().toDoubleOrNull()?.let { it != 0.0 } ?: false
        }
    }
}

/** Safe read of a primitive string out of an arbitrary JsonElement (used for `js.cmd`). */
fun kotlinx.serialization.json.JsonElement?.primitiveOrNull(): String? =
    (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
