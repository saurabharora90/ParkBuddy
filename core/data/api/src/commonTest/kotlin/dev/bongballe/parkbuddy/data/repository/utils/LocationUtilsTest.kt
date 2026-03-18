package dev.bongballe.parkbuddy.data.repository.utils

import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.StreetSide
import org.junit.Test

class LocationUtilsTest {

  // ── determineSide: segment-local cross product ──

  /**
   * Street geometry running west→east (increasing longitude, roughly constant latitude).
   *
   * North (higher lat) ← LEFT ─────────●──────────── centerline (west → east) South (lower lat) ←
   * RIGHT
   */
  private val westToEastCenterline =
    Geometry(
      type = "LineString",
      coordinates =
        listOf(listOf(-122.420, 37.775), listOf(-122.419, 37.775), listOf(-122.418, 37.775)),
    )

  @Test
  fun `determineSide - point north of west-to-east street is LEFT`() {
    val side =
      LocationUtils.determineSide(
        latitude = 37.7751, // north of the line
        longitude = -122.419,
        lineGeometry = westToEastCenterline,
      )
    assertThat(side).isEqualTo(StreetSide.LEFT)
  }

  @Test
  fun `determineSide - point south of west-to-east street is RIGHT`() {
    val side =
      LocationUtils.determineSide(
        latitude = 37.7749, // south of the line
        longitude = -122.419,
        lineGeometry = westToEastCenterline,
      )
    assertThat(side).isEqualTo(StreetSide.RIGHT)
  }

  /**
   * Street geometry running south→north (increasing latitude, roughly constant longitude).
   *
   *         ▲ north
   *         │ centerline
   *   LEFT  │  RIGHT
   *  (west) │ (east)
   *         │
   *         south
   */
  private val southToNorthCenterline =
    Geometry(
      type = "LineString",
      coordinates =
        listOf(listOf(-122.419, 37.774), listOf(-122.419, 37.775), listOf(-122.419, 37.776)),
    )

  @Test
  fun `determineSide - point west of south-to-north street is LEFT`() {
    val side =
      LocationUtils.determineSide(
        latitude = 37.775,
        longitude = -122.4191, // west
        lineGeometry = southToNorthCenterline,
      )
    assertThat(side).isEqualTo(StreetSide.LEFT)
  }

  @Test
  fun `determineSide - point east of south-to-north street is RIGHT`() {
    val side =
      LocationUtils.determineSide(
        latitude = 37.775,
        longitude = -122.4189, // east
        lineGeometry = southToNorthCenterline,
      )
    assertThat(side).isEqualTo(StreetSide.RIGHT)
  }

  /**
   * Curved street: starts heading east, then bends north. The segment-local approach should use the
   * closest segment's direction, not the first-to-last vector.
   *
   *       ● end (north)
   *       │
   *       │ segment 2 (heading north)
   *       │
   *   ────● bend
   *     segment 1 (heading east)
   *   ●
   *  start
   */
  private val curvedCenterline =
    Geometry(
      type = "LineString",
      coordinates =
        listOf(
          listOf(-122.420, 37.774), // start (west)
          listOf(-122.418, 37.774), // bend (east)
          listOf(-122.418, 37.776), // end (north)
        ),
    )

  @Test
  fun `determineSide - point near curved segment uses local tangent, not first-to-last`() {
    // Point is east of the northward segment (segment 2). Using the local tangent of segment 2
    // (heading north), east is RIGHT. Using a naive first-to-last vector (heading NE), this point
    // could be misclassified.
    val side =
      LocationUtils.determineSide(
        latitude = 37.775, // near segment 2
        longitude = -122.4179, // east of the northward segment
        lineGeometry = curvedCenterline,
      )
    assertThat(side).isEqualTo(StreetSide.RIGHT)
  }

  @Test
  fun `determineSide - point near first segment of curve uses segment 1 tangent`() {
    // Point is south of the eastward segment (segment 1). East-heading segment: south is RIGHT.
    val side =
      LocationUtils.determineSide(
        latitude = 37.7739, // south of segment 1
        longitude = -122.419,
        lineGeometry = curvedCenterline,
      )
    assertThat(side).isEqualTo(StreetSide.RIGHT)
  }

  // ── determineSide: very close to centerline ──

  @Test
  fun `determineSide - point 1m north of centerline is still LEFT`() {
    // ~1m north ≈ 0.000009 degrees latitude
    val side =
      LocationUtils.determineSide(
        latitude = 37.775009,
        longitude = -122.419,
        lineGeometry = westToEastCenterline,
      )
    assertThat(side).isEqualTo(StreetSide.LEFT)
  }

  // ── calculateDistanceToPolyline ──

  @Test
  fun `calculateDistanceToPolyline - point on the line returns near zero`() {
    val dist = LocationUtils.calculateDistanceToPolyline(37.775, -122.419, westToEastCenterline)
    assertThat(dist).isLessThan(1.0)
  }

  @Test
  fun `calculateDistanceToPolyline - point 5m away returns approximately 5`() {
    // ~5m north ≈ 0.000045 degrees latitude
    val dist = LocationUtils.calculateDistanceToPolyline(37.775045, -122.419, westToEastCenterline)
    assertThat(dist).isWithin(1.0).of(5.0)
  }

  @Test
  fun `calculateDistanceToPolyline - empty geometry returns MAX_VALUE`() {
    val empty = Geometry("LineString", emptyList())
    val dist = LocationUtils.calculateDistanceToPolyline(37.775, -122.419, empty)
    assertThat(dist).isEqualTo(Double.MAX_VALUE)
  }

  // ── determineSide: edge cases ──

  @Test
  fun `determineSide - single segment geometry works`() {
    val singleSegment =
      Geometry(
        type = "LineString",
        coordinates = listOf(listOf(-122.420, 37.775), listOf(-122.418, 37.775)),
      )
    val side = LocationUtils.determineSide(37.7751, -122.419, singleSegment)
    assertThat(side).isEqualTo(StreetSide.LEFT)
  }

  @Test
  fun `determineSide - degenerate single point geometry returns RIGHT`() {
    val degenerate = Geometry("LineString", listOf(listOf(-122.419, 37.775)))
    val side = LocationUtils.determineSide(37.7751, -122.419, degenerate)
    assertThat(side).isEqualTo(StreetSide.RIGHT)
  }
}
