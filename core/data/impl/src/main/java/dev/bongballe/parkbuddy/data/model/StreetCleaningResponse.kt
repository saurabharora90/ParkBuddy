package dev.bongballe.parkbuddy.data.model

import dev.bongballe.parkbuddy.data.repository.serializers.StringToBooleanSerializer
import dev.bongballe.parkbuddy.model.Weekday
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StreetCleaningResponse(
  val cnn: String,
  @SerialName("corridor") val streetName: String,
  @SerialName("week1")
  @Serializable(StringToBooleanSerializer::class)
  val servicedOnFirstWeekOfMonth: Boolean,
  @SerialName("week2")
  @Serializable(StringToBooleanSerializer::class)
  val servicedOnSecondWeekOfMonth: Boolean,
  @SerialName("week3")
  @Serializable(StringToBooleanSerializer::class)
  val servicedOnThirdWeekOfMonth: Boolean,
  @SerialName("week4")
  @Serializable(StringToBooleanSerializer::class)
  val servicedOnFourthWeekOfMonth: Boolean,
  @SerialName("week5")
  @Serializable(StringToBooleanSerializer::class)
  val servicedOnFifthWeekOfMonth: Boolean,
  @SerialName("holidays")
  @Serializable(StringToBooleanSerializer::class)
  val servicedOnHolidays: Boolean,
  val weekday: Weekday,
  val fromhour: Int,
  val tohour: Int,
  @SerialName("line") val geometry: Geometry,
)
