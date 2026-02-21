package dev.bongballe.parkbuddy.data.sf

import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.Weekday
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Test

class CoordinateMatcherTest {

  @Test
  fun `findMatch returns correct sweeping data when point is near`() {
    val sweepingData =
      listOf(
        createStreetCleaningResponse(
          cnn = "123",
          side = "L",
          coords = listOf(listOf(-122.4194, 37.7749), listOf(-122.4195, 37.7750)),
        )
      )
    val matcher = CoordinateMatcher(sweepingData)
    val parkingGeometry =
      Geometry(type = "Point", coordinates = listOf(listOf(-122.41941, 37.77491)))

    val match = matcher.findMatch(parkingGeometry)

    assertThat(match).isNotNull()
    assertThat(match?.cnn).isEqualTo("123")
    assertThat(match?.side).isEqualTo("L")
  }

  @Test
  fun `findMatch returns null when point is too far`() {
    val sweepingData =
      listOf(
        createStreetCleaningResponse(
          cnn = "123",
          side = "L",
          coords = listOf(listOf(-122.4194, 37.7749), listOf(-122.4195, 37.7750)),
        )
      )
    val matcher = CoordinateMatcher(sweepingData)
    val parkingGeometry = Geometry(type = "Point", coordinates = listOf(listOf(-123.0, 38.0)))

    val match = matcher.findMatch(parkingGeometry)

    assertThat(match).isNull()
  }

  @Test
  fun `determineSide correctly identifies left and right`() {
    val matcher = CoordinateMatcher(emptyList())
    val line =
      Geometry(
        type = "LineString",
        coordinates = listOf(listOf(-122.4, 37.7), listOf(-122.4, 37.8)),
      )

    // Point on the left (West): lng = -122.401, lat = 37.75
    val leftPoint = 37.75 to -122.401
    assertThat(matcher.determineSide(leftPoint, line)).isEqualTo("L")

    // Point on the right (East): lng = -122.399, lat = 37.75
    val rightPoint = 37.75 to -122.399
    assertThat(matcher.determineSide(rightPoint, line)).isEqualTo("R")
  }

  @Test
  fun `findMatch flips side correctly when point is on opposite side of line`() {
    val coords = listOf(listOf(-122.4, 37.7), listOf(-122.4, 37.8))
    val leftSchedule = createStreetCleaningResponse(cnn = "123", side = "L", coords = coords)
    val rightSchedule = createStreetCleaningResponse(cnn = "123", side = "R", coords = coords)
    val matcher = CoordinateMatcher(listOf(leftSchedule, rightSchedule))

    // Parking point very close to the line but on the RIGHT side
    // Line is at lng = -122.4. Point at lng = -122.3999 is ~9m to the East.
    val rightParking = Geometry(type = "Point", coordinates = listOf(listOf(-122.3999, 37.75)))

    val match = matcher.findMatch(rightParking)

    assertThat(match?.cnn).isEqualTo("123")
    assertThat(match?.side).isEqualTo("R")
    assertThat(match?.schedules).containsExactly(rightSchedule)
  }

  private fun createStreetCleaningResponse(
    cnn: String,
    side: String,
    coords: List<List<Double>>,
  ): StreetCleaningResponse {
    val geometry = Geometry(type = "LineString", coordinates = coords)
    return StreetCleaningResponse(
      cnn = cnn,
      cnnRightLeft = side,
      weekday = Weekday.Mon,
      fromhour = "08:00",
      tohour = "10:00",
      geometry = Json.encodeToJsonElement(geometry),
      streetName = "Test St",
      limits = "100-200",
      servicedOnFirstWeekOfMonth = true,
      servicedOnSecondWeekOfMonth = true,
      servicedOnThirdWeekOfMonth = true,
      servicedOnFourthWeekOfMonth = true,
      servicedOnFifthWeekOfMonth = true,
      servicedOnHolidays = false,
    )
  }
}
