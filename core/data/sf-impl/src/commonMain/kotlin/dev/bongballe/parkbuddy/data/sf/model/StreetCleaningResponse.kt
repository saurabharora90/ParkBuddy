package dev.bongballe.parkbuddy.data.sf.model

import dev.bongballe.parkbuddy.data.repository.serializers.StringToBooleanSerializer
import dev.bongballe.parkbuddy.model.Weekday
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Response model for SF Open Data Street Sweeping API.
 *
 * API: https://data.sfgov.org/resource/yhqp-riqs.json Dataset: Street Sweeping Schedule
 *
 * This API provides street sweeping schedules for San Francisco. Each record represents one
 * sweeping occurrence (one side of a street, one day of the week).
 *
 * A single street block will have MULTIPLE records:
 * - One per weekday that sweeping occurs
 * - Separate records for left (L) and right (R) sides of the street
 *
 * Example: Clement St between 8th-9th Ave might have 10 records:
 * - 5 for left side (different weekdays)
 * - 5 for right side (different weekdays)
 *
 * @property cnn Centerline Network Number - unique identifier for a street segment. Same CNN is
 *   shared by left and right side records.
 * @property streetName Street name from `corridor` field (e.g., "Clement St", "Market St")
 * @property servicedOnFirstWeekOfMonth Whether sweeping occurs on 1st occurrence of [weekday] in
 *   month
 * @property servicedOnSecondWeekOfMonth Whether sweeping occurs on 2nd occurrence
 * @property servicedOnThirdWeekOfMonth Whether sweeping occurs on 3rd occurrence
 * @property servicedOnFourthWeekOfMonth Whether sweeping occurs on 4th occurrence
 * @property servicedOnFifthWeekOfMonth Whether sweeping occurs on 5th occurrence (rare)
 * @property servicedOnHolidays Whether sweeping occurs on holidays
 * @property weekday Day of week: Mon, Tues (note spelling), Wed, Thu, Fri, Sat, Sun, Holiday
 * @property fromhour Start hour as string (e.g., "7", "8", "10")
 * @property tohour End hour as string (e.g., "8", "10", "12")
 * @property geometry GeoJSON LineString geometry from `line` field
 * @property limits Cross streets (e.g., "8th Ave - 9th Ave", "Market St - Mission St")
 * @property blockSide Compass direction (e.g., "North", "South", "East", "West")
 * @property cnnRightLeft Which side of street centerline: "L" (left) or "R" (right). Critical for
 *   matching parking spots to correct sweeping schedule.
 */
@Serializable
data class StreetCleaningResponse(
  @SerialName("cnn") val cnn: String = "",
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
  @SerialName("fromhour") val fromhour: String = "",
  @SerialName("tohour") val tohour: String = "",
  @SerialName("line") val geometry: JsonElement? = null,
  @SerialName("limits") val limits: String = "",
  @SerialName("blockside") val blockSide: String = "",
  @SerialName("cnnrightleft") val cnnRightLeft: String = "",
)
