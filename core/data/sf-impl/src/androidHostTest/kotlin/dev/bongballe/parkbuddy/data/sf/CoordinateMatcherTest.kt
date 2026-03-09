package dev.bongballe.parkbuddy.data.sf

import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.data.repository.utils.LocationUtils
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.Weekday
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Test

class CoordinateMatcherTest {

  @Test
  fun `findMatch returns match when point is near centerline`() {
    // North-South line at lng -122.4, from lat 37.7 to 37.8
    val sweepingData =
      listOf(
        createStreetCleaningResponse(
          cnn = "123",
          side = "L",
          coords = listOf(listOf(-122.4, 37.7), listOf(-122.4, 37.8)),
        )
      )
    val matcher = CoordinateMatcher(sweepingData)

    // Point 5m to the West (left side of a N-going line)
    val parking = Geometry(type = "Point", coordinates = listOf(listOf(-122.40006, 37.75)))

    val match = matcher.findMatch(parking)

    assertThat(match).isNotNull()
    assertThat(match?.cnn).isEqualTo("123")
    assertThat(match?.side).isEqualTo(StreetSide.LEFT)
  }

  @Test
  fun `findMatch returns null when point is too far`() {
    val sweepingData =
      listOf(
        createStreetCleaningResponse(
          cnn = "123",
          side = "L",
          coords = listOf(listOf(-122.4, 37.7), listOf(-122.4, 37.8)),
        )
      )
    val matcher = CoordinateMatcher(sweepingData)
    val parking = Geometry(type = "Point", coordinates = listOf(listOf(-123.0, 38.0)))

    val match = matcher.findMatch(parking)

    assertThat(match).isNull()
  }

  @Test
  fun `determineSide correctly identifies left and right of N-S line`() {
    //  Line goes North (37.7 -> 37.8) at lng -122.4
    //  West is LEFT, East is RIGHT
    val line =
      Geometry(
        type = "LineString",
        coordinates = listOf(listOf(-122.4, 37.7), listOf(-122.4, 37.8)),
      )

    val leftSide = LocationUtils.determineSide(37.75, -122.401, line)
    assertThat(leftSide).isEqualTo(StreetSide.LEFT)

    val rightSide = LocationUtils.determineSide(37.75, -122.399, line)
    assertThat(rightSide).isEqualTo(StreetSide.RIGHT)
  }

  @Test
  fun `findMatch selects correct side schedule when point is on right`() {
    val coords = listOf(listOf(-122.4, 37.7), listOf(-122.4, 37.8))
    val leftSchedule = createStreetCleaningResponse(cnn = "123", side = "L", coords = coords)
    val rightSchedule = createStreetCleaningResponse(cnn = "123", side = "R", coords = coords)
    val matcher = CoordinateMatcher(listOf(leftSchedule, rightSchedule))

    // Point clearly to the East (right side), ~5m offset
    val rightParking = Geometry(type = "Point", coordinates = listOf(listOf(-122.39994, 37.75)))

    val match = matcher.findMatch(rightParking)

    assertThat(match?.cnn).isEqualTo("123")
    assertThat(match?.side).isEqualTo(StreetSide.RIGHT)
    assertThat(match?.schedules).containsExactly(rightSchedule)
  }

  @Test
  fun `matchPolyline finds segment within threshold`() {
    // Real Delancey St centerline
    val sweepingData =
      listOf(
        createStreetCleaningResponse(
          cnn = "115001",
          side = "R",
          coords =
            listOf(
              listOf(-122.391165593868, 37.784774780511),
              listOf(-122.391142128898, 37.784636950053),
              listOf(-122.390856721213, 37.784410569136),
            ),
        )
      )
    val matcher = CoordinateMatcher(sweepingData)

    // Regulation polyline ~13m from centerline (real data, objectid=1017)
    val regPoly =
      Geometry(
        type = "LineString",
        coordinates =
          listOf(listOf(-122.39101255, 37.784412774), listOf(-122.391266737, 37.784614392)),
      )

    val matches = matcher.matchPolyline(regPoly, thresholdMeters = 20.0)
    assertThat(matches).isNotEmpty()
    assertThat(matches[0].cnn).isEqualTo("115001")
  }

  @Test
  fun `matchPolyline returns empty when polyline is beyond threshold`() {
    val sweepingData =
      listOf(
        createStreetCleaningResponse(
          cnn = "123",
          side = "R",
          coords = listOf(listOf(-122.4, 37.7), listOf(-122.4, 37.8)),
        )
      )
    val matcher = CoordinateMatcher(sweepingData)

    // Polyline 500m+ away
    val farPoly =
      Geometry(
        type = "LineString",
        coordinates = listOf(listOf(-122.41, 37.7), listOf(-122.41, 37.8)),
      )

    val matches = matcher.matchPolyline(farPoly, thresholdMeters = 20.0)
    assertThat(matches).isEmpty()
  }

  @Test
  fun `findAllMatchesForCnn returns both sides`() {
    val coords = listOf(listOf(-122.4, 37.7), listOf(-122.4, 37.8))
    val left = createStreetCleaningResponse(cnn = "123", side = "L", coords = coords)
    val right = createStreetCleaningResponse(cnn = "123", side = "R", coords = coords)
    val matcher = CoordinateMatcher(listOf(left, right))

    val matches = matcher.findAllMatchesForCnn("123")
    assertThat(matches).hasSize(2)
    assertThat(matches.map { it.side }.toSet()).containsExactly(StreetSide.LEFT, StreetSide.RIGHT)
  }

  @Test
  fun `findAllMatchesForCnn returns empty for unknown CNN`() {
    val coords = listOf(listOf(-122.4, 37.7), listOf(-122.4, 37.8))
    val matcher =
      CoordinateMatcher(
        listOf(createStreetCleaningResponse(cnn = "123", side = "R", coords = coords))
      )

    val matches = matcher.findAllMatchesForCnn("999")
    assertThat(matches).isEmpty()
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
      fromhour = "8",
      tohour = "10",
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
