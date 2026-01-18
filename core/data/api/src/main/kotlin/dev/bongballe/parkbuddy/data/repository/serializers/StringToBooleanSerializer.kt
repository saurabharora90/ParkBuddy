package dev.bongballe.parkbuddy.data.repository.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class StringToBooleanSerializer : KSerializer<Boolean> {
  override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("dev.bongballe.parkbuddy", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Boolean) {
    val string = if (value) "1" else "0"
    encoder.encodeString(string)
  }

  override fun deserialize(decoder: Decoder): Boolean {
    val string = decoder.decodeString()
    return string == "1"
  }
}
