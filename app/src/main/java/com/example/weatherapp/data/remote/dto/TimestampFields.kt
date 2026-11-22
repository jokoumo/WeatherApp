package com.example.weatherapp.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads an Open-Meteo timestamp field as plain text.
 *
 * The forecast endpoint is queried with `timeformat=unixtime`, so `time` arrives as a JSON
 * number (`1795302000`), while older cached payloads and hand written fixtures still use the
 * ISO-8601 default (`"2026-11-22T08:00"`). Keeping both in one `String` field lets the mapper
 * decide how to interpret the value and keeps cache payloads written by previous app versions
 * readable.
 */
object TimestampAsStringSerializer : KSerializer<String> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TimestampAsString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String =
        if (decoder is JsonDecoder) {
            when (val element = decoder.decodeJsonElement()) {
                is JsonPrimitive -> element.content
                else -> element.toString()
            }
        } else {
            decoder.decodeString()
        }

    override fun serialize(encoder: Encoder, value: String) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonPrimitive(value))
        } else {
            encoder.encodeString(value)
        }
    }
}

/** List form of [TimestampAsStringSerializer], used for the hourly and daily `time` arrays. */
object TimestampListSerializer : KSerializer<List<String>> {

    private val delegate = ListSerializer(TimestampAsStringSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> = delegate.deserialize(decoder)

    override fun serialize(encoder: Encoder, value: List<String>) = delegate.serialize(encoder, value)
}
