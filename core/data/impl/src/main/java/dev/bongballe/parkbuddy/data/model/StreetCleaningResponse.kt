package dev.bongballe.parkbuddy.data.model

import dev.bongballe.parkbuddy.data.repository.serializers.StringToBooleanSerializer
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.Weekday
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StreetCleaningResponse(
  val cnn: String = "",
  @SerialName("corridor") val streetName: String = "",
  @SerialName("week1")
  @Serializable(StringToBooleanSerializer::class)
  val servicedOnFirstWeekOfMonth: Boolean = false,
  @SerialName("week2")
  @Serializable(StringToBooleanSerializer::class)
  val servicedOnSecondWeekOfMonth: Boolean = false,
  @SerialName("week3")
  @Serializable(StringToBooleanSerializer::class)
  val servicedOnThirdWeekOfMonth: Boolean = false,
  @SerialName("week4")
  @Serializable(StringToBooleanSerializer::class)
  val servicedOnFourthWeekOfMonth: Boolean = false,
  @SerialName("week5")
  @Serializable(StringToBooleanSerializer::class)
  val servicedOnFifthWeekOfMonth: Boolean = false,
  @SerialName("holidays")
  @Serializable(StringToBooleanSerializer::class)
  val servicedOnHolidays: Boolean = false,
  val weekday: Weekday = Weekday.Holiday,
  @SerialName("fromhour") val fromhour: Int = 0,
  @SerialName("tohour") val tohour: Int = 0,
  @SerialName("line") val geometry: Geometry,
)
