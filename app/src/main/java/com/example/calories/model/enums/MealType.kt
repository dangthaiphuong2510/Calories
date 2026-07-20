package com.example.calories.model.enums

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = MealTypeSerializer::class)
enum class MealType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACKS,
}

object MealTypeSerializer : KSerializer<MealType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MealType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: MealType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): MealType {
        val raw = decoder.decodeString().trim()
        return MealType.entries.find { it.name.equals(raw, ignoreCase = true) }
            ?: MealType.SNACKS
    }
}
