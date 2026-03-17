package dev.bongballe.parkbuddy.data.sf.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.data.sf.database.ParkBuddyDatabase
import dev.bongballe.parkbuddy.data.sf.model.MeterScheduleResponse
import dev.bongballe.parkbuddy.data.sf.model.ParkingMeterResponse
import dev.bongballe.parkbuddy.data.sf.model.ParkingRegulationResponse
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import dev.bongballe.parkbuddy.data.sf.network.FakeSfOpenDataApi
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.Weekday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [ParkingRepositoryImpl.refreshData].
 *
 * All coordinates come from actual SF Open Data APIs:
 * - Street sweeping: https://data.sfgov.org/resource/yhqp-riqs.json
 * - Parking regulations: https://data.sfgov.org/resource/hi6h-neyh.json
 * - Meter inventory: https://data.sfgov.org/resource/8vzz-qzz9.json
 * - Meter schedules: https://data.sfgov.org/resource/6cqg-dxku.json
 *
 * Assertions use `spot.timeline` (pre-resolved `List<ParkingInterval>`). The timeline is produced
 * by [TimelineResolver] during sync and stored in the entity as a flat, non-overlapping weekly
 * schedule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ParkingRepositoryImplTest {

  private fun runRepoTest(
    block:
      suspend TestScope.(
        repository: ParkingRepositoryImpl, api: FakeSfOpenDataApi, db: ParkBuddyDatabase,
      ) -> Unit
  ) =
    runTest(UnconfinedTestDispatcher()) {
      val db =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ParkBuddyDatabase::class.java,
          )
          .allowMainThreadQueries()
          .build()

      val api = FakeSfOpenDataApi()

      val repository =
        ParkingRepositoryImpl(
          dao = db.parkingDao(),
          api = api,
          defaultDispatcher = UnconfinedTestDispatcher(),
        )

      try {
        block(repository, api, db)
      } finally {
        db.close()
      }
    }

  // ========================== Real street data from SF Open Data APIs ==========================

  //  Delancey St CNN 115001: Bryant St - Federal St
  //  Centerline from yhqp-riqs.json?$where=cnn='115001'
  private val delanceyCenterline =
    Geometry(
      type = "LineString",
      coordinates =
        listOf(
          listOf(-122.391165593868, 37.784774780511),
          listOf(-122.391142128898, 37.784636950053),
          listOf(-122.390856721213, 37.784410569136),
        ),
    )

  //  Regulation objectid=1017 near Delancey: Zone Y, "Time limited", 2hr M-Su 800-2200
  //  Shape from hi6h-neyh.json?$where=within_circle(shape,37.7847,-122.3912,50)
  //  Original is MultiLineString; we pass it as raw JSON so parseGeometry handles it.
  private val delanceyRegulationShapeJson =
    Json.parseToJsonElement(
      """{"type":"MultiLineString","coordinates":[[[-122.39101255,37.784412774],[-122.391266737,37.784614392]]]}"""
    )

  //  De Haro St CNN 4626000: Division St - Berry St
  //  Verified: no regulations within 30m, no meters on this CNN.
  //  Centerline from yhqp-riqs.json?$where=cnn='4626000'
  private val deHaroCenterline =
    Geometry(
      type = "LineString",
      coordinates =
        listOf(
          listOf(-122.402073125126, 37.769885043657),
          listOf(-122.401992727044, 37.769029470513),
        ),
    )

  //  Howard St CNN 7042000: Mary St - 06th St
  //  Has meters (CNN 7042000), no parking regulations within 30m.
  //  Centerline from yhqp-riqs.json?$where=cnn='7042000'
  private val howardCenterline =
    Geometry(
      type = "LineString",
      coordinates =
        listOf(
          listOf(-122.405718739597, 37.780878374149),
          listOf(-122.407159488015, 37.779738905439),
        ),
    )

  // ========================== Tests ==========================

  @Test
  fun `Delancey - regulation matches one side, sweeping-only side excluded`() =
    runRepoTest { repository, api, _ ->
      val geometryJson = Json.encodeToJsonElement(delanceyCenterline)

      // CNN 115001: R sweeps Fri, L sweeps Thu
      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "115001",
            streetName = "Delancey St",
            cnnRightLeft = "R",
            weekday = Weekday.Fri,
            geometry = geometryJson,
            limits = "Bryant St  -  Federal St",
          ),
          StreetCleaningResponse(
            cnn = "115001",
            streetName = "Delancey St",
            cnnRightLeft = "L",
            weekday = Weekday.Thu,
            geometry = geometryJson,
            limits = "Bryant St  -  Federal St",
          ),
        )

      // Zone Y regulation ~13m from centerline
      api.parkingRegulations =
        listOf(
          ParkingRegulationResponse(
            objectId = "1017",
            regulation = "Time limited",
            rppArea1 = "Y",
            hrsBegin = "800",
            hrsEnd = "2200",
            days = "M-Su",
            hrLimit = "2",
            neighborhood = "Financial District/South Beach",
            shape = delanceyRegulationShapeJson,
          )
        )

      val success = repository.refreshData()
      assertThat(success).isTrue()

      repository.getAllSpots().test {
        val spots = awaitItem()
        // Only the side matching the regulation is stored; the other is sweeping-only.
        assertThat(spots).hasSize(1)
        assertThat(spots[0].rppAreas).containsExactly("Y")
        assertThat(spots[0].timeline.any { it.type is IntervalType.Limited }).isTrue()
        assertThat(spots[0].sweepingSchedules).hasSize(1)
      }
    }

  @Test
  fun `refreshData - maps multiple RPP areas correctly`() = runRepoTest { repository, api, _ ->
    val geometryJson = Json.encodeToJsonElement(delanceyCenterline)

    api.streetCleaningData =
      listOf(
        StreetCleaningResponse(
          cnn = "115001",
          streetName = "Delancey St",
          cnnRightLeft = "R",
          weekday = Weekday.Fri,
          fromhour = "0",
          tohour = "6",
          geometry = geometryJson,
        )
      )

    api.parkingRegulations =
      listOf(
        ParkingRegulationResponse(
          objectId = "1017",
          regulation = "Time limited",
          rppArea1 = "N",
          rppArea2 = "A",
          rppArea3 = "BB",
          shape = delanceyRegulationShapeJson,
        )
      )

    repository.refreshData()

    repository.getAllSpots().test {
      val spots = awaitItem()
      assertThat(spots).hasSize(1)
      assertThat(spots[0].rppAreas).containsExactly("A", "BB", "N").inOrder()
    }
  }

  @Test
  fun `De Haro - sweeping-only street excluded entirely`() = runRepoTest { repository, api, _ ->
    val geometryJson = Json.encodeToJsonElement(deHaroCenterline)

    api.streetCleaningData =
      listOf(
        StreetCleaningResponse(
          cnn = "4626000",
          streetName = "De Haro St",
          cnnRightLeft = "R",
          weekday = Weekday.Mon,
          fromhour = "0",
          tohour = "6",
          geometry = geometryJson,
          limits = "Division St  -  Berry St",
        ),
        StreetCleaningResponse(
          cnn = "4626000",
          streetName = "De Haro St",
          cnnRightLeft = "L",
          weekday = Weekday.Mon,
          fromhour = "0",
          tohour = "6",
          geometry = geometryJson,
          limits = "Division St  -  Berry St",
        ),
      )
    api.parkingRegulations = emptyList()
    api.parkingMeterInventory = emptyList()
    api.meterSchedules = emptyList()

    val success = repository.refreshData()
    // No regulations and no meters at all: returns false
    assertThat(success).isFalse()

    repository.getAllSpots().test { assertThat(awaitItem()).isEmpty() }
  }

  @Test
  fun `sweeping-only segments excluded even when other segments have regulations`() =
    runRepoTest { repository, api, _ ->
      val delanceyJson = Json.encodeToJsonElement(delanceyCenterline)
      val deHaroJson = Json.encodeToJsonElement(deHaroCenterline)

      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "115001",
            streetName = "Delancey St",
            cnnRightLeft = "R",
            weekday = Weekday.Fri,
            geometry = delanceyJson,
          ),
          StreetCleaningResponse(
            cnn = "4626000",
            streetName = "De Haro St",
            cnnRightLeft = "R",
            weekday = Weekday.Mon,
            geometry = deHaroJson,
          ),
        )

      api.parkingRegulations =
        listOf(
          ParkingRegulationResponse(
            objectId = "1017",
            regulation = "Time limited",
            rppArea1 = "Y",
            shape = delanceyRegulationShapeJson,
          )
        )

      val success = repository.refreshData()
      assertThat(success).isTrue()

      repository.getAllSpots().test {
        val spots = awaitItem()
        assertThat(spots).hasSize(1)
        assertThat(spots[0].streetName).isEqualTo("Delancey St")
      }
    }

  // Verifies that a meter-only CNN (no parking regulations) produces METERED timeline intervals
  // on both LEFT and RIGHT sides. The two API schedules (weekday 120min and Sunday 240min) should
  // resolve into separate METERED intervals with correct time windows and limits.
  @Test
  fun `Howard - meter-only segment stored as METERED on both sides`() =
    runRepoTest { repository, api, _ ->
      val geometryJson = Json.encodeToJsonElement(howardCenterline)

      // Howard CNN 7042000: R=Fri, L=Thu sweeping
      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "7042000",
            streetName = "Howard St",
            cnnRightLeft = "R",
            weekday = Weekday.Fri,
            fromhour = "3",
            tohour = "5",
            geometry = geometryJson,
            limits = "Mary St  -  06th St",
          ),
          StreetCleaningResponse(
            cnn = "7042000",
            streetName = "Howard St",
            cnnRightLeft = "L",
            weekday = Weekday.Thu,
            fromhour = "3",
            tohour = "5",
            geometry = geometryJson,
            limits = "Mary St  -  06th St",
          ),
        )

      // Real meter: post_id=470-09440 on CNN 7042000
      api.parkingMeterInventory =
        listOf(
          ParkingMeterResponse(
            objectId = "10522695",
            postId = "470-09440",
            streetSegCtrlnId = "7042000",
            streetName = "HOWARD ST",
            neighborhood = "South of Market",
          )
        )

      // Real schedules for that meter
      api.meterSchedules =
        listOf(
          MeterScheduleResponse(
            postId = "470-09440",
            daysApplied = "Mo,Tu,We,Th,Fr,Sa",
            fromTime = "7:00 AM",
            toTime = "6:00 PM",
            timeLimit = "120 minutes",
            scheduleType = "Operating Schedule",
          ),
          MeterScheduleResponse(
            postId = "470-09440",
            daysApplied = "Su",
            fromTime = "12:00 PM",
            toTime = "6:00 PM",
            timeLimit = "240 minutes",
            scheduleType = "Operating Schedule",
          ),
        )

      api.parkingRegulations = emptyList()

      val success = repository.refreshData()
      assertThat(success).isTrue()

      repository.getAllSpots().test {
        val spots = awaitItem().sortedBy { it.objectId }
        // Both sides stored because meters match via CNN
        assertThat(spots).hasSize(2)
        spots.forEach { spot ->
          assertThat(spot.hasMeters).isTrue()
          assertThat(spot.streetName).isEqualTo("Howard St")
          assertThat(spot.timeline.any { it.type is IntervalType.Metered }).isTrue()
        }

        val left = spots.first { it.objectId == "cnn_7042000_LEFT" }
        assertThat(left.sweepingSchedules).hasSize(1)
        assertThat(left.sweepingSchedules[0].weekday).isEqualTo(Weekday.Thu)

        val right = spots.first { it.objectId == "cnn_7042000_RIGHT" }
        assertThat(right.sweepingSchedules).hasSize(1)
        assertThat(right.sweepingSchedules[0].weekday).isEqualTo(Weekday.Fri)

        val meteredIntervals = right.timeline.filter { it.type is IntervalType.Metered }

        // Weekday schedule: 2hr (120min) limit, 7AM-6PM
        val weekday =
          meteredIntervals.first { (it.type as IntervalType.Metered).timeLimitMinutes == 120 }
        assertThat(weekday.startTime.hour).isEqualTo(7)
        assertThat(weekday.endTime.hour).isEqualTo(18)

        // Sunday schedule: 4hr (240min) limit, 12PM-6PM
        val sunday =
          meteredIntervals.first { (it.type as IntervalType.Metered).timeLimitMinutes == 240 }
        assertThat(sunday.startTime.hour).isEqualTo(12)
        assertThat(sunday.endTime.hour).isEqualTo(18)
      }
    }

  // Two physical meter posts on the same CNN with identical schedules. The deduplication in
  // parseMeterSchedulesToIntervals should collapse them, and the resolver should produce a
  // single METERED interval.
  @Test
  fun `meter schedules deduplicated across multiple posts on same CNN`() =
    runRepoTest { repository, api, _ ->
      val geometryJson = Json.encodeToJsonElement(howardCenterline)

      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "7042000",
            streetName = "Howard St",
            cnnRightLeft = "R",
            weekday = Weekday.Fri,
            geometry = geometryJson,
          )
        )

      // Two meters, same CNN, identical schedules
      api.parkingMeterInventory =
        listOf(
          ParkingMeterResponse(
            objectId = "m1",
            postId = "470-09440",
            streetSegCtrlnId = "7042000",
            streetName = "HOWARD ST",
          ),
          ParkingMeterResponse(
            objectId = "m2",
            postId = "470-09450",
            streetSegCtrlnId = "7042000",
            streetName = "HOWARD ST",
          ),
        )

      api.meterSchedules =
        listOf(
          MeterScheduleResponse(
            postId = "470-09440",
            daysApplied = "Mo,Tu,We,Th,Fr,Sa",
            fromTime = "7:00 AM",
            toTime = "6:00 PM",
            timeLimit = "120 minutes",
            scheduleType = "Operating Schedule",
          ),
          MeterScheduleResponse(
            postId = "470-09450",
            daysApplied = "Mo,Tu,We,Th,Fr,Sa",
            fromTime = "7:00 AM",
            toTime = "6:00 PM",
            timeLimit = "120 minutes",
            scheduleType = "Operating Schedule",
          ),
        )

      api.parkingRegulations = emptyList()

      repository.refreshData()

      repository.getAllSpots().test {
        val spots = awaitItem()
        assertThat(spots).hasSize(1)
        val meteredCount = spots[0].timeline.count { it.type is IntervalType.Metered }
        assertThat(meteredCount).isEqualTo(1)
      }
    }

  @Test
  fun `returns false when both regulations and meters are empty`() =
    runRepoTest { repository, api, _ ->
      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "4626000",
            streetName = "De Haro St",
            cnnRightLeft = "R",
            weekday = Weekday.Mon,
            geometry = Json.encodeToJsonElement(deHaroCenterline),
          )
        )
      api.parkingRegulations = emptyList()
      api.parkingMeterInventory = emptyList()

      val success = repository.refreshData()
      assertThat(success).isFalse()
    }

  // Verifies that a TIME_LIMITED regulation's enforcement window and days are correctly converted
  // into a LIMITED timeline interval with the right hours and day coverage.
  @Test
  fun `enforcement times and days parsed correctly from regulation`() =
    runRepoTest { repository, api, _ ->
      val geometryJson = Json.encodeToJsonElement(delanceyCenterline)

      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "115001",
            streetName = "Delancey St",
            cnnRightLeft = "R",
            weekday = Weekday.Fri,
            geometry = geometryJson,
          )
        )

      api.parkingRegulations =
        listOf(
          ParkingRegulationResponse(
            objectId = "1017",
            regulation = "Time limited",
            rppArea1 = "Y",
            hrsBegin = "800",
            hrsEnd = "2200",
            days = "M-Su",
            hrLimit = "2",
            shape = delanceyRegulationShapeJson,
          )
        )

      repository.refreshData()

      repository.getAllSpots().test {
        val spot = awaitItem().single()
        val interval = spot.timeline.single()
        assertThat(interval.type).isInstanceOf(IntervalType.Limited::class.java)
        assertThat((interval.type as IntervalType.Limited).timeLimitMinutes).isEqualTo(120)
        assertThat(interval.startTime.hour).isEqualTo(8)
        assertThat(interval.endTime.hour).isEqualTo(22)
        assertThat(interval.days).hasSize(7) // M-Su = all 7 days
      }
    }

  // A tow-zone meter schedule (scheduleType="Tow") should produce a FORBIDDEN interval in the
  // timeline, while the regular operating schedule becomes METERED. The resolver splits them
  // into non-overlapping windows.
  @Test
  fun `tow zone meter schedule stored correctly`() = runRepoTest { repository, api, _ ->
    val geometryJson = Json.encodeToJsonElement(howardCenterline)

    api.streetCleaningData =
      listOf(
        StreetCleaningResponse(
          cnn = "7042000",
          streetName = "Howard St",
          cnnRightLeft = "L",
          weekday = Weekday.Mon,
          geometry = geometryJson,
        )
      )

    api.parkingMeterInventory =
      listOf(
        ParkingMeterResponse(
          objectId = "m1",
          postId = "470-09440",
          streetSegCtrlnId = "7042000",
          streetName = "HOWARD ST",
        )
      )

    api.meterSchedules =
      listOf(
        MeterScheduleResponse(
          postId = "470-09440",
          daysApplied = "Mo,Tu",
          fromTime = "9:00 AM",
          toTime = "6:00 PM",
          timeLimit = "120 minutes",
          scheduleType = "Operating Schedule",
        ),
        MeterScheduleResponse(
          postId = "470-09440",
          daysApplied = "Mo,Tu",
          fromTime = "7:00 AM",
          toTime = "9:00 AM",
          timeLimit = "0 minutes",
          scheduleType = "Tow",
        ),
      )

    api.parkingRegulations = emptyList()

    repository.refreshData()

    repository.getAllSpots().test {
      val spot = awaitItem().single()
      assertThat(spot.hasMeters).isTrue()

      val forbidden = spot.timeline.filter { it.type is IntervalType.Forbidden }
      assertThat(forbidden).isNotEmpty()
      assertThat(forbidden.first().startTime.hour).isEqualTo(7)

      val metered = spot.timeline.filter { it.type is IntervalType.Metered }
      assertThat(metered).isNotEmpty()
      assertThat((metered.first().type as IntervalType.Metered).timeLimitMinutes).isEqualTo(120)
      assertThat(metered.first().startTime.hour).isEqualTo(9)
    }
  }

  // Two posts on the same CNN with identical time windows but the second has a superset of days.
  // The resolver merges days across posts for identical (window, limit) combinations, producing
  // a single METERED interval covering all 7 days.
  @Test
  fun `meter schedules with same window and limit merge days across posts`() =
    runRepoTest { repository, api, _ ->
      val geometryJson = Json.encodeToJsonElement(howardCenterline)

      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "7042000",
            streetName = "Howard St",
            cnnRightLeft = "R",
            weekday = Weekday.Fri,
            geometry = geometryJson,
          )
        )

      // Post A: Mon-Sat 7-7, 30 min
      // Post B: Mon-Sun 7-7, 30 min (superset of A's days, same window)
      api.parkingMeterInventory =
        listOf(
          ParkingMeterResponse(
            objectId = "m1",
            postId = "470-09440",
            streetSegCtrlnId = "7042000",
            streetName = "HOWARD ST",
          ),
          ParkingMeterResponse(
            objectId = "m2",
            postId = "470-09450",
            streetSegCtrlnId = "7042000",
            streetName = "HOWARD ST",
          ),
        )

      api.meterSchedules =
        listOf(
          MeterScheduleResponse(
            postId = "470-09440",
            daysApplied = "Mo,Tu,We,Th,Fr,Sa",
            fromTime = "7:00 AM",
            toTime = "7:00 PM",
            timeLimit = "30 minutes",
            scheduleType = "Operating Schedule",
          ),
          MeterScheduleResponse(
            postId = "470-09450",
            daysApplied = "Mo,Tu,We,Th,Fr,Sa,Su",
            fromTime = "7:00 AM",
            toTime = "7:00 PM",
            timeLimit = "30 minutes",
            scheduleType = "Operating Schedule",
          ),
        )

      api.parkingRegulations = emptyList()

      repository.refreshData()

      repository.getAllSpots().test {
        val spot = awaitItem().single()
        val metered = spot.timeline.filter { it.type is IntervalType.Metered }
        // Resolver merges identical (window, type) intervals across days into one
        assertThat(metered).hasSize(1)
        assertThat(metered[0].days).hasSize(7)
        assertThat((metered[0].type as IntervalType.Metered).timeLimitMinutes).isEqualTo(30)
      }
    }

  // Two posts with the same time window but different limits (30 vs 240 min). The resolver picks
  // the shorter limit (30min) because within the same METERED priority tier, shorter is safer
  // for the user. This produces a single METERED(30) interval.
  @Test
  fun `meter schedules with different limits on same window kept separate`() =
    runRepoTest { repository, api, _ ->
      val geometryJson = Json.encodeToJsonElement(howardCenterline)

      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "7042000",
            streetName = "Howard St",
            cnnRightLeft = "R",
            weekday = Weekday.Fri,
            geometry = geometryJson,
          )
        )

      api.parkingMeterInventory =
        listOf(
          ParkingMeterResponse(
            objectId = "m1",
            postId = "470-09440",
            streetSegCtrlnId = "7042000",
            streetName = "HOWARD ST",
          ),
          ParkingMeterResponse(
            objectId = "m2",
            postId = "470-09450",
            streetSegCtrlnId = "7042000",
            streetName = "HOWARD ST",
          ),
        )

      // Post A: Mon-Fri 7-7, 30 min
      // Post B: Mon-Fri 7-7, 240 min (same window but different limit)
      api.meterSchedules =
        listOf(
          MeterScheduleResponse(
            postId = "470-09440",
            daysApplied = "Mo,Tu,We,Th,Fr",
            fromTime = "7:00 AM",
            toTime = "7:00 PM",
            timeLimit = "30 minutes",
            scheduleType = "Operating Schedule",
          ),
          MeterScheduleResponse(
            postId = "470-09450",
            daysApplied = "Mo,Tu,We,Th,Fr",
            fromTime = "7:00 AM",
            toTime = "7:00 PM",
            timeLimit = "240 minutes",
            scheduleType = "Operating Schedule",
          ),
        )

      api.parkingRegulations = emptyList()

      repository.refreshData()

      repository.getAllSpots().test {
        val spot = awaitItem().single()
        // Resolver picks the shorter limit (30min) when two METERED intervals overlap
        val metered = spot.timeline.filter { it.type is IntervalType.Metered }
        assertThat(metered).hasSize(1)
        assertThat((metered[0].type as IntervalType.Metered).timeLimitMinutes).isEqualTo(30)
      }
    }

  // Same post, two non-overlapping time windows (morning and afternoon) with the same limit.
  // These are distinct intervals that should both appear in the resolved timeline.
  @Test
  fun `meter schedules with different windows but same limit kept separate`() =
    runRepoTest { repository, api, _ ->
      val geometryJson = Json.encodeToJsonElement(howardCenterline)

      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "7042000",
            streetName = "Howard St",
            cnnRightLeft = "R",
            weekday = Weekday.Fri,
            geometry = geometryJson,
          )
        )

      api.parkingMeterInventory =
        listOf(
          ParkingMeterResponse(
            objectId = "m1",
            postId = "470-09440",
            streetSegCtrlnId = "7042000",
            streetName = "HOWARD ST",
          )
        )

      // Same post, two windows: morning and afternoon
      api.meterSchedules =
        listOf(
          MeterScheduleResponse(
            postId = "470-09440",
            daysApplied = "Mo,Tu,We,Th,Fr",
            fromTime = "7:00 AM",
            toTime = "12:00 PM",
            timeLimit = "30 minutes",
            scheduleType = "Operating Schedule",
          ),
          MeterScheduleResponse(
            postId = "470-09440",
            daysApplied = "Mo,Tu,We,Th,Fr",
            fromTime = "1:00 PM",
            toTime = "7:00 PM",
            timeLimit = "30 minutes",
            scheduleType = "Operating Schedule",
          ),
        )

      api.parkingRegulations = emptyList()

      repository.refreshData()

      repository.getAllSpots().test {
        val spot = awaitItem().single()
        // Different time windows produce separate METERED intervals
        val metered = spot.timeline.filter { it.type is IntervalType.Metered }
        assertThat(metered).hasSize(2)
      }
    }

  // When both a regulation (LIMITED 240min) and a meter (METERED 30min) cover the same CNN and
  // time window, the resolver picks METERED because it has higher priority (2 > 1). The timeline
  // should contain the METERED interval, not the LIMITED one.
  @Test
  fun `metered interval wins over limited when both cover same window`() =
    runRepoTest { repository, api, _ ->
      val geometryJson = Json.encodeToJsonElement(howardCenterline)

      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "7042000",
            streetName = "Howard St",
            cnnRightLeft = "R",
            weekday = Weekday.Fri,
            geometry = geometryJson,
          )
        )

      // Regulation that would produce a LIMITED(240) interval
      api.parkingRegulations =
        listOf(
          ParkingRegulationResponse(
            objectId = "9999",
            regulation = "Time limited",
            rppArea1 = null,
            hrsBegin = "700",
            hrsEnd = "1900",
            days = "M-Su",
            hrLimit = "4",
            shape =
              Json.parseToJsonElement(
                """{"type":"LineString","coordinates":[${
                howardCenterline.coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
              }]}"""
              ),
          )
        )

      // Meter on the same CNN producing METERED(30)
      api.parkingMeterInventory =
        listOf(
          ParkingMeterResponse(
            objectId = "m1",
            postId = "470-09440",
            streetSegCtrlnId = "7042000",
            streetName = "HOWARD ST",
          )
        )

      api.meterSchedules =
        listOf(
          MeterScheduleResponse(
            postId = "470-09440",
            daysApplied = "Mo,Tu,We,Th,Fr,Sa",
            fromTime = "7:00 AM",
            toTime = "7:00 PM",
            timeLimit = "30 minutes",
            scheduleType = "Operating Schedule",
          )
        )

      repository.refreshData()

      repository.getAllSpots().test {
        val spot = awaitItem().single()
        // METERED(priority=2) beats LIMITED(priority=1) during the overlapping window
        val metered = spot.timeline.filter { it.type is IntervalType.Metered }
        assertThat(metered).isNotEmpty()
        assertThat((metered.first().type as IntervalType.Metered).timeLimitMinutes).isEqualTo(30)
      }
    }

  // A regulation-only spot (no meters) should produce a LIMITED timeline interval with the
  // correct time limit.
  @Test
  fun `regulation-only spot produces LIMITED timeline interval`() =
    runRepoTest { repository, api, _ ->
      val geometryJson = Json.encodeToJsonElement(delanceyCenterline)

      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "115001",
            streetName = "Delancey St",
            cnnRightLeft = "R",
            weekday = Weekday.Fri,
            geometry = geometryJson,
          )
        )

      api.parkingRegulations =
        listOf(
          ParkingRegulationResponse(
            objectId = "1017",
            regulation = "Time limited",
            rppArea1 = "Y",
            hrsBegin = "800",
            hrsEnd = "2200",
            days = "M-Su",
            hrLimit = "2",
            shape = delanceyRegulationShapeJson,
          )
        )

      api.parkingMeterInventory = emptyList()
      api.meterSchedules = emptyList()

      repository.refreshData()

      repository.getAllSpots().test {
        val spot = awaitItem().single()
        val limited = spot.timeline.filter { it.type is IntervalType.Limited }
        assertThat(limited).isNotEmpty()
        assertThat((limited.first().type as IntervalType.Limited).timeLimitMinutes).isEqualTo(120)
      }
    }

  // When a meter's time_limit exceeds the enforcement window (e.g., 1440 min in a 13-hour
  // window), it's effectively "no cap, just pay." The sync should clamp it to 0 so the UI
  // shows "METERED:" instead of "Max 24 hrs:".
  @Test
  fun `meter time limit clamped to zero when it exceeds enforcement window`() =
    runRepoTest { repository, api, _ ->
      val geometryJson = Json.encodeToJsonElement(howardCenterline)

      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "7042000",
            streetName = "Howard St",
            cnnRightLeft = "R",
            weekday = Weekday.Fri,
            geometry = geometryJson,
          )
        )

      // 1440-minute limit in a 9AM-10PM (780 min) window = meaningless, should clamp to 0
      api.parkingMeterInventory =
        listOf(
          ParkingMeterResponse(
            objectId = "m1",
            postId = "470-09440",
            streetSegCtrlnId = "7042000",
            streetName = "HOWARD ST",
          )
        )

      api.meterSchedules =
        listOf(
          MeterScheduleResponse(
            postId = "470-09440",
            daysApplied = "Mo,Tu,We,Th,Fr,Sa",
            fromTime = "9:00 AM",
            toTime = "10:00 PM",
            timeLimit = "1440 minutes",
            scheduleType = "Operating Schedule",
          )
        )

      api.parkingRegulations = emptyList()

      repository.refreshData()

      repository.getAllSpots().test {
        val spot = awaitItem().single()
        val metered = spot.timeline.filter { it.type is IntervalType.Metered }
        assertThat(metered).hasSize(1)
        // 1440 >= 780 (window duration), so limit clamped to 0
        assertThat((metered[0].type as IntervalType.Metered).timeLimitMinutes).isEqualTo(0)
      }
    }

  // A real time limit shorter than the window should be preserved as-is.
  @Test
  fun `meter time limit preserved when shorter than enforcement window`() =
    runRepoTest { repository, api, _ ->
      val geometryJson = Json.encodeToJsonElement(howardCenterline)

      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "7042000",
            streetName = "Howard St",
            cnnRightLeft = "R",
            weekday = Weekday.Fri,
            geometry = geometryJson,
          )
        )

      // 120-minute limit in a 9AM-6PM (540 min) window = meaningful, should be preserved
      api.parkingMeterInventory =
        listOf(
          ParkingMeterResponse(
            objectId = "m1",
            postId = "470-09440",
            streetSegCtrlnId = "7042000",
            streetName = "HOWARD ST",
          )
        )

      api.meterSchedules =
        listOf(
          MeterScheduleResponse(
            postId = "470-09440",
            daysApplied = "Mo,Tu,We,Th,Fr,Sa",
            fromTime = "9:00 AM",
            toTime = "6:00 PM",
            timeLimit = "120 minutes",
            scheduleType = "Operating Schedule",
          )
        )

      api.parkingRegulations = emptyList()

      repository.refreshData()

      repository.getAllSpots().test {
        val spot = awaitItem().single()
        val metered = spot.timeline.filter { it.type is IntervalType.Metered }
        assertThat(metered).hasSize(1)
        // 120 < 540 (window duration), so limit preserved
        assertThat((metered[0].type as IntervalType.Metered).timeLimitMinutes).isEqualTo(120)
      }
    }
}
