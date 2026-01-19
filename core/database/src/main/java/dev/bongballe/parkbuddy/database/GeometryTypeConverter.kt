package dev.bongballe.parkbuddy.database

import androidx.room.TypeConverter
import dev.bongballe.parkbuddy.model.Geometry
import kotlinx.serialization.json.Json

class GeometryTypeConverter {
  private val json = Json { ignoreUnknownKeys = true }

  @TypeConverter
  fun fromGeometry(geometry: Geometry): String {
    return json.encodeToString(geometry)
  }

  @TypeConverter
  fun toGeometry(geometryString: String): Geometry {
    return Json.decodeFromString(geometryString)
  }
}
