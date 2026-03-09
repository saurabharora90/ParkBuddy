package dev.bongballe.parkbuddy.data.sf.database

import androidx.room.TypeConverter
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.Weekday
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ParkBuddyTypeConverters {
  private val json = Json { ignoreUnknownKeys = true }

  @TypeConverter fun fromLocalTime(time: LocalTime): String = time.toString()

  @TypeConverter fun toLocalTime(value: String): LocalTime = LocalTime.parse(value)

  @TypeConverter
  fun fromDayOfWeekSet(days: Set<DayOfWeek>): String = days.joinToString(",") { it.name }

  @TypeConverter
  fun toDayOfWeekSet(value: String): Set<DayOfWeek> =
    if (value.isBlank()) emptySet() else value.split(",").map { DayOfWeek.valueOf(it) }.toSet()

  @TypeConverter fun fromGeometry(geometry: Geometry): String = json.encodeToString(geometry)

  @TypeConverter fun toGeometry(value: String): Geometry = json.decodeFromString(value)

  @TypeConverter fun fromParkingRegulation(regulation: ParkingRegulation): String = regulation.name

  @TypeConverter
  fun toParkingRegulation(value: String): ParkingRegulation = ParkingRegulation.valueOf(value)

  @TypeConverter fun fromStreetSide(side: StreetSide?): String? = side?.name

  @TypeConverter
  fun toStreetSide(value: String?): StreetSide? = value?.let { StreetSide.valueOf(it) }

  @TypeConverter fun fromWeekday(weekday: Weekday): String = weekday.name

  @TypeConverter fun toWeekday(value: String): Weekday = Weekday.valueOf(value)
}
