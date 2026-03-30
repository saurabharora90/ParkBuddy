package dev.bongballe.parkbuddy.data.sf.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.data.sf.database.ParkBuddyDatabase
import dev.bongballe.parkbuddy.data.sf.model.MeterPolicyResponse
import dev.bongballe.parkbuddy.data.sf.model.MeterScheduleResponse
import dev.bongballe.parkbuddy.data.sf.model.StreetCenterlineResponse
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisBlockfaceAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisBlockfaceRateAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisCleaningAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisFeature
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisGeometry
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisMeterAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisRegulationAttrs
import dev.bongballe.parkbuddy.data.sf.network.FakeSfOpenDataApi
import dev.bongballe.parkbuddy.data.sf.network.FakeSfmtaArcGisApi
import dev.bongballe.parkbuddy.fakes.FakeDataFileReader
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.Weekday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ParkingRepositoryImplTest {

  private fun runRepoTest(
    block:
      suspend TestScope.(
        repository: ParkingRepositoryImpl,
        arcGis: FakeSfmtaArcGisApi,
        socrata: FakeSfOpenDataApi,
        fileReader: FakeDataFileReader,
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

      val arcGis = FakeSfmtaArcGisApi()
      val socrata = FakeSfOpenDataApi()
      val fileReader = FakeDataFileReader()

      val repository =
        ParkingRepositoryImpl(
          dao = db.parkingDao(),
          arcGis = arcGis,
          socrata = socrata,
          fileReader = fileReader,
          json = Json { ignoreUnknownKeys = true },
          ioDispatcher = UnconfinedTestDispatcher(),
        )

      try {
        block(repository, arcGis, socrata, fileReader)
      } finally {
        db.close()
      }
    }

  // Real coordinates from SFMTA ArcGIS (shared between ArcGis and centerline fixtures)

  private val spearStCoords =
    listOf(listOf(-122.389428942091, 37.788837780037), listOf(-122.388271869523, 37.787913311939))

  private val delanceyCoords =
    listOf(
      listOf(-122.391165593868, 37.784774780511),
      listOf(-122.391142128898, 37.784636950053),
      listOf(-122.390856721213, 37.784410569136),
    )

  private val howardCoords =
    listOf(listOf(-122.405718739597, 37.780878374149), listOf(-122.407159488015, 37.779738905439))

  private val spearStGeometry = ArcGisGeometry(paths = listOf(spearStCoords))
  private val delanceyGeometry = ArcGisGeometry(paths = listOf(delanceyCoords))

  private val delanceyRegGeometry =
    ArcGisGeometry(
      paths =
        listOf(listOf(listOf(-122.39101255, 37.784412774), listOf(-122.391266737, 37.784614392)))
    )

  private val howardGeometry = ArcGisGeometry(paths = listOf(howardCoords))

  private val howardBlockfaceGeometry =
    ArcGisGeometry(
      paths = listOf(listOf(listOf(-122.40578, 37.78085), listOf(-122.40710, 37.77978)))
    )

  private fun centerline(cnn: String, streetName: String, coords: List<List<Double>>) =
    StreetCenterlineResponse(
      cnn = cnn,
      streetname = streetName,
      line = Geometry(type = "LineString", coordinates = coords),
    )

  private val spearStCenterline = centerline("12048000", "SPEAR ST", spearStCoords)
  private val delanceyCenterline = centerline("115001", "DELANCEY ST", delanceyCoords)
  private val howardCenterline = centerline("7042000", "HOWARD ST", howardCoords)

  private fun cleaningFeature(
    cnn: String,
    side: String,
    street: String,
    weekday: String,
    fromHour: String = "00:00",
    toHour: String = "06:00",
    geometry: ArcGisGeometry,
  ) =
    ArcGisFeature(
      attributes =
        ArcGisCleaningAttrs(
          cnn = cnn,
          cnnRightLeft = side,
          corridor = street,
          weekday = weekday,
          fromHour = fromHour,
          toHour = toHour,
          week1 = "Y",
          week2 = "Y",
          week3 = "Y",
          week4 = "Y",
          week5 = "Y",
          holidays = "N",
        ),
      geometry = geometry,
    )

  private fun meterFeature(
    postId: String,
    blockfaceId: Double,
    cnn: String,
    streetName: String,
    activeMeterFlag: String = "M",
  ) =
    ArcGisFeature(
      attributes =
        ArcGisMeterAttrs(
          postId = postId,
          blockfaceId = blockfaceId,
          streetSegCtrlnId = cnn.toDoubleOrNull(),
          streetName = streetName,
          activeMeterFlag = activeMeterFlag,
        ),
      geometry = null,
    )

  // ── Tests ──

  @Test
  fun `sweeping-only street stored for cleaning reminders`() =
    runRepoTest { repository, arcGis, socrata, _ ->
      socrata.streetCenterlines = listOf(spearStCenterline)
      arcGis.streetCleaning =
        listOf(
          cleaningFeature("12048000", "R", "Spear St", "Wed", "00:00", "02:00", spearStGeometry)
        )

      repository.refreshData()

      repository.getAllSpots().test {
        val spots = awaitItem()
        assertThat(spots).isNotEmpty()
        val spot = spots.first()
        assertThat(spot.sweepingSchedules).hasSize(1)
        assertThat(spot.sweepingSchedules[0].weekday).isEqualTo(Weekday.Wed)
        assertThat(spot.sweepingSchedules[0].toHour).isEqualTo(2)
      }
    }

  @Test
  fun `meter joined via CNN with Socrata policy schedules`() =
    runRepoTest { repository, arcGis, socrata, _ ->
      socrata.streetCenterlines = listOf(howardCenterline)
      arcGis.streetCleaning =
        listOf(
          cleaningFeature("7042000", "R", "Howard St", "Fri", "03:00", "05:00", howardGeometry)
        )

      arcGis.meteredBlockfaces =
        listOf(
          ArcGisFeature(
            ArcGisBlockfaceAttrs(blockfaceId = 470001.0, streetName = "HOWARD ST"),
            howardBlockfaceGeometry,
          )
        )

      arcGis.meters = listOf(meterFeature("470-09440", 470001.0, "7042000", "HOWARD ST"))

      socrata.meterPolicies =
        listOf(
          MeterPolicyResponse("470-09440", "Mo", "8:00", "18:00", "OP", "3.00", "30"),
          MeterPolicyResponse("470-09440", "Tu", "8:00", "18:00", "OP", "3.00", "30"),
        )

      val success = repository.refreshData()
      assertThat(success).isTrue()

      repository.getAllSpots().test {
        val spots = awaitItem()
        val right = spots.first { it.objectId.contains("RIGHT") }
        assertThat(right.hasMeters).isTrue()
        assertThat(right.sweepingSchedules).hasSize(1)

        val metered = right.timeline.filter { it.type is IntervalType.Metered }
        assertThat(metered).isNotEmpty()
        assertThat((metered.first().type as IntervalType.Metered).timeLimitMinutes).isEqualTo(30)
        assertThat(metered.first().startTime.hour).isEqualTo(8)
      }
    }

  @Test
  fun `regulation spatial-matched to backbone`() = runRepoTest { repository, arcGis, socrata, _ ->
    socrata.streetCenterlines = listOf(delanceyCenterline)
    arcGis.streetCleaning =
      listOf(cleaningFeature("115001", "R", "Delancey St", "Fri", geometry = delanceyGeometry))

    arcGis.timeLimitedRegulations =
      listOf(
        ArcGisFeature(
          ArcGisRegulationAttrs(
            objectId = 1017,
            regulation = "Time limited",
            days = "M-Su",
            hrsBegin = 800.0,
            hrsEnd = 2200.0,
            hrLimit = 2.0,
            rppArea1 = "Y",
          ),
          delanceyRegGeometry,
        )
      )

    repository.refreshData()

    repository.getAllSpots().test {
      val spots = awaitItem()
      val regulated = spots.filter { it.timeline.any { i -> i.type is IntervalType.Limited } }
      assertThat(regulated).isNotEmpty()
      val spot = regulated.first()
      assertThat(spot.rppAreas).containsExactly("Y")
      val limited = spot.timeline.first { it.type is IntervalType.Limited }
      assertThat((limited.type as IntervalType.Limited).timeLimitMinutes).isEqualTo(120)
      assertThat(limited.startTime.hour).isEqualTo(8)
      assertThat(limited.endTime.hour).isEqualTo(22)
    }
  }

  @Test
  fun `no stopping regulation produces forbidden interval`() =
    runRepoTest { repository, arcGis, socrata, _ ->
      socrata.streetCenterlines = listOf(delanceyCenterline)
      arcGis.streetCleaning =
        listOf(cleaningFeature("115001", "R", "Delancey St", "Fri", geometry = delanceyGeometry))

      arcGis.otherRegulations =
        listOf(
          ArcGisFeature(
            ArcGisRegulationAttrs(
              objectId = 4850,
              regulation = "No Stopping",
              days = "M-F",
              hrsBegin = 700.0,
              hrsEnd = 900.0,
              hrLimit = 0.0,
            ),
            delanceyRegGeometry,
          )
        )

      repository.refreshData()

      repository.getAllSpots().test {
        val spots = awaitItem()
        val spot = spots.first { it.timeline.any { i -> i.type is IntervalType.Forbidden } }
        val forbidden = spot.timeline.first { it.type is IntervalType.Forbidden }
        assertThat(forbidden.startTime.hour).isEqualTo(7)
        assertThat(forbidden.endTime.hour).isEqualTo(9)
      }
    }

  @Test
  fun `meter with no schedule data still creates spot with sweeping`() =
    runRepoTest { repository, arcGis, socrata, _ ->
      socrata.streetCenterlines = listOf(spearStCenterline)
      arcGis.streetCleaning =
        listOf(
          cleaningFeature("12048000", "R", "Spear St", "Wed", "00:00", "02:00", spearStGeometry)
        )

      arcGis.meters = listOf(meterFeature("658-02120", 658012.0, "12048000", "SPEAR ST"))

      val success = repository.refreshData()
      assertThat(success).isTrue()

      repository.getAllSpots().test {
        val spots = awaitItem()
        assertThat(spots).isNotEmpty()
        // Meter has no schedule data, so no metered intervals, but spot exists with sweeping
        val spot = spots.first { it.sweepingSchedules.isNotEmpty() }
        assertThat(spot.sweepingSchedules).hasSize(1)
        // The meter sets the regulation to METERED, which creates the context, but
        // without schedule data there are no timeline intervals
        assertThat(spot.timeline.filter { it.type is IntervalType.Metered }).isEmpty()
      }
    }

  @Test
  fun `decommissioned meters filtered out`() = runRepoTest { repository, arcGis, _, _ ->
    arcGis.meters =
      listOf(meterFeature("dead", 999.0, "9999000", "GHOST ST", activeMeterFlag = "U"))

    val success = repository.refreshData()
    assertThat(success).isFalse()
  }

  @Test
  fun `pay or permit regulation produces metered interval`() =
    runRepoTest { repository, arcGis, socrata, _ ->
      socrata.streetCenterlines = listOf(delanceyCenterline)
      arcGis.streetCleaning =
        listOf(cleaningFeature("115001", "R", "Delancey St", "Fri", geometry = delanceyGeometry))

      arcGis.otherRegulations =
        listOf(
          ArcGisFeature(
            ArcGisRegulationAttrs(
              objectId = 12806,
              regulation = "Pay or Permit",
              days = "M-Sa",
              hrsBegin = 900.0,
              hrsEnd = 2100.0,
              hrLimit = 0.0,
              rppArea1 = "HV",
            ),
            delanceyRegGeometry,
          )
        )

      repository.refreshData()

      repository.getAllSpots().test {
        val spots = awaitItem()
        val spot = spots.first { it.timeline.any { i -> i.type is IntervalType.Metered } }
        val metered = spot.timeline.first { it.type is IntervalType.Metered }
        assertThat((metered.type as IntervalType.Metered).timeLimitMinutes).isEqualTo(0)
        assertThat(metered.exemptPermitZones).containsExactly("HV")
      }
    }

  @Test
  fun `blockface rate HTML fallback when no Socrata policies`() =
    runRepoTest { repository, arcGis, socrata, _ ->
      socrata.streetCenterlines = listOf(howardCenterline)
      arcGis.streetCleaning =
        listOf(
          cleaningFeature("7042000", "R", "Howard St", "Fri", "03:00", "05:00", howardGeometry)
        )

      arcGis.meters = listOf(meterFeature("470-09440", 470001.0, "7042000", "HOWARD ST"))
      arcGis.meteredBlockfaces =
        listOf(
          ArcGisFeature(
            ArcGisBlockfaceAttrs(
              blockfaceId = 470001.0,
              streetName = "HOWARD ST",
              strSegOrientation = "R",
            ),
            howardBlockfaceGeometry,
          )
        )

      arcGis.blockfaceRates =
        listOf(
          ArcGisFeature(
            ArcGisBlockfaceRateAttrs(
              blockfaceId = 470001,
              streetName = "Howard St",
              rate = "\$2.25 per hour",
              rateSched =
                "<html><body><table>" +
                  "<tr><th>Time of day</th><th>Rate</th><th>General metered parking time limit</th>" +
                  "<tr><td>12am - 7am<td>Free<td>None</td>" +
                  "<tr><td>7am - 6pm<td>\$2.25 per hour<td>None</td>" +
                  "<tr><td>6pm - 12am<td>Free<td>None</td>" +
                  "</table></body></html>",
            ),
            null,
          )
        )

      repository.refreshData()

      repository.getAllSpots().test {
        val spots = awaitItem()
        val right = spots.first { it.objectId.contains("RIGHT") }
        assertThat(right.hasMeters).isTrue()
        val metered = right.timeline.filter { it.type is IntervalType.Metered }
        assertThat(metered).isNotEmpty()
        assertThat(metered.first().startTime.hour).isEqualTo(7)
        assertThat(metered.first().endTime.hour).isEqualTo(18)
      }
    }

  @Test
  fun `legacy meter schedules used when no Socrata policies`() =
    runRepoTest { repository, arcGis, socrata, _ ->
      socrata.streetCenterlines = listOf(howardCenterline)
      arcGis.streetCleaning =
        listOf(
          cleaningFeature("7042000", "R", "Howard St", "Fri", "03:00", "05:00", howardGeometry)
        )

      arcGis.meters = listOf(meterFeature("470-09440", 470001.0, "7042000", "HOWARD ST"))
      arcGis.meteredBlockfaces =
        listOf(
          ArcGisFeature(
            ArcGisBlockfaceAttrs(
              blockfaceId = 470001.0,
              streetName = "HOWARD ST",
              strSegOrientation = "R",
            ),
            howardBlockfaceGeometry,
          )
        )

      // No Socrata policies for this meter, but legacy schedule exists
      socrata.meterSchedules =
        listOf(
          MeterScheduleResponse(
            postId = "470-09440",
            daysApplied = "Mo,Tu,We,Th,Fr,Sa",
            fromTime = "7:00 AM",
            toTime = "6:00 PM",
            timeLimit = "120 minutes",
            scheduleType = "Operating Schedule",
            appliedColorRule = "Grey - General metered parking",
          )
        )

      val success = repository.refreshData()
      assertThat(success).isTrue()

      repository.getAllSpots().test {
        val spots = awaitItem()
        val right = spots.first { it.objectId.contains("RIGHT") }
        assertThat(right.hasMeters).isTrue()

        val metered = right.timeline.filter { it.type is IntervalType.Metered }
        assertThat(metered).isNotEmpty()
        assertThat((metered.first().type as IntervalType.Metered).timeLimitMinutes).isEqualTo(120)
        assertThat(metered.first().startTime.hour).isEqualTo(7)
        assertThat(metered.first().endTime.hour).isEqualTo(18)
      }
    }

  @Test
  fun `Socrata policies preferred over legacy schedules`() =
    runRepoTest { repository, arcGis, socrata, _ ->
      socrata.streetCenterlines = listOf(howardCenterline)
      arcGis.streetCleaning =
        listOf(
          cleaningFeature("7042000", "R", "Howard St", "Fri", "03:00", "05:00", howardGeometry)
        )

      arcGis.meters = listOf(meterFeature("470-09440", 470001.0, "7042000", "HOWARD ST"))
      arcGis.meteredBlockfaces =
        listOf(
          ArcGisFeature(
            ArcGisBlockfaceAttrs(
              blockfaceId = 470001.0,
              streetName = "HOWARD ST",
              strSegOrientation = "R",
            ),
            howardBlockfaceGeometry,
          )
        )

      // Both exist: policy says 30 min, legacy says 120 min. Policy should win.
      socrata.meterPolicies =
        listOf(
          MeterPolicyResponse("470-09440", "Mo", "8:00", "18:00", "OP", "3.00", "30"),
          MeterPolicyResponse("470-09440", "Tu", "8:00", "18:00", "OP", "3.00", "30"),
        )

      socrata.meterSchedules =
        listOf(
          MeterScheduleResponse(
            postId = "470-09440",
            daysApplied = "Mo,Tu,We,Th,Fr,Sa",
            fromTime = "7:00 AM",
            toTime = "6:00 PM",
            timeLimit = "120 minutes",
            scheduleType = "Operating Schedule",
          )
        )

      repository.refreshData()

      repository.getAllSpots().test {
        val spots = awaitItem()
        val right = spots.first { it.objectId.contains("RIGHT") }
        val metered = right.timeline.filter { it.type is IntervalType.Metered }
        assertThat(metered).isNotEmpty()
        // Policy's 30 min should win over legacy's 120 min
        assertThat((metered.first().type as IntervalType.Metered).timeLimitMinutes).isEqualTo(30)
        // Policy's 8AM should win over legacy's 7AM
        assertThat(metered.first().startTime.hour).isEqualTo(8)
      }
    }

  @Test
  fun `returns false when no data at all`() = runRepoTest { repository, _, _, _ ->
    val success = repository.refreshData()
    assertThat(success).isFalse()
  }

  @Test
  fun `regulation matches via centerline backbone without sweeping or meters`() =
    runRepoTest { repository, arcGis, socrata, _ ->
      // A street with only centerline geometry and a regulation, no sweeping or meters
      val valenciaCoords = listOf(listOf(-122.421081, 37.764303), listOf(-122.420517, 37.762576))
      socrata.streetCenterlines = listOf(centerline("13300000", "VALENCIA ST", valenciaCoords))

      arcGis.otherRegulations =
        listOf(
          ArcGisFeature(
            ArcGisRegulationAttrs(
              objectId = 9999,
              regulation = "No parking any time",
              hrsBegin = 0.0,
              hrsEnd = 0.0,
            ),
            ArcGisGeometry(
              paths = listOf(listOf(listOf(-122.420900, 37.763800), listOf(-122.420750, 37.763200)))
            ),
          )
        )

      repository.refreshData()

      repository.getAllSpots().test {
        val spots = awaitItem()
        assertThat(spots).isNotEmpty()
        val spot = spots.first()
        // Matched to real CNN, not an orphan reg_ virtual CNN
        assertThat(spot.objectId).doesNotContain("reg_")
        assertThat(spot.objectId).contains("13300000")
        val forbidden = spot.timeline.filter { it.type is IntervalType.Forbidden }
        assertThat(forbidden).isNotEmpty()
      }
    }

  @Test
  fun `sweeping attaches correctly to centerline-backed CNN`() =
    runRepoTest { repository, arcGis, socrata, _ ->
      socrata.streetCenterlines = listOf(spearStCenterline)
      arcGis.streetCleaning =
        listOf(
          cleaningFeature("12048000", "L", "Spear St", "Mon", "06:00", "08:00", spearStGeometry),
          cleaningFeature("12048000", "R", "Spear St", "Tue", "06:00", "08:00", spearStGeometry),
        )

      repository.refreshData()

      repository.getAllSpots().test {
        val spots = awaitItem()
        assertThat(spots).hasSize(2) // LEFT and RIGHT
        val left = spots.first { it.objectId.contains("LEFT") }
        val right = spots.first { it.objectId.contains("RIGHT") }
        assertThat(left.sweepingSchedules).hasSize(1)
        assertThat(left.sweepingSchedules[0].weekday).isEqualTo(Weekday.Mon)
        assertThat(right.sweepingSchedules).hasSize(1)
        assertThat(right.sweepingSchedules[0].weekday).isEqualTo(Weekday.Tues)
      }
    }

  // ── Exclusion tests ──

  @Test
  fun `excluded CNN produces forbidden interval`() =
    runRepoTest { repository, arcGis, socrata, fileReader ->
      socrata.streetCenterlines = listOf(spearStCenterline)
      arcGis.streetCleaning =
        listOf(
          cleaningFeature("12048000", "R", "Spear St", "Wed", "00:00", "02:00", spearStGeometry)
        )

      fileReader.write("exclusions.json", """[{"cnn": "12048000", "street": "SPEAR ST"}]""")

      repository.refreshData()

      repository.getAllSpots().test {
        val spots = awaitItem()
        assertThat(spots).isNotEmpty()
        val spot = spots.first()
        val forbidden = spot.timeline.filter { it.type is IntervalType.Forbidden }
        assertThat(forbidden).isNotEmpty()
        val f = forbidden.first()
        assertThat(f.startTime.hour).isEqualTo(0)
        assertThat(f.endTime.hour).isEqualTo(23)
      }
    }

  @Test
  fun `non-excluded CNN is unaffected`() = runRepoTest { repository, arcGis, socrata, fileReader ->
    socrata.streetCenterlines = listOf(spearStCenterline, delanceyCenterline)
    arcGis.streetCleaning =
      listOf(
        cleaningFeature("12048000", "R", "Spear St", "Wed", "00:00", "02:00", spearStGeometry),
        cleaningFeature("115001", "R", "Delancey St", "Fri", geometry = delanceyGeometry),
      )

    // Only exclude Spear St, not Delancey
    fileReader.write("exclusions.json", """[{"cnn": "12048000", "street": "SPEAR ST"}]""")

    repository.refreshData()

    repository.getAllSpots().test {
      val spots = awaitItem()
      val delancey = spots.filter { it.streetName?.contains("Delancey", ignoreCase = true) == true }
      assertThat(delancey).isNotEmpty()
      for (spot in delancey) {
        val forbidden = spot.timeline.filter { it.type is IntervalType.Forbidden }
        assertThat(forbidden).isEmpty()
      }
    }
  }

  @Test
  fun `missing exclusions file is handled gracefully`() =
    runRepoTest { repository, arcGis, socrata, _ ->
      socrata.streetCenterlines = listOf(spearStCenterline)
      arcGis.streetCleaning =
        listOf(
          cleaningFeature("12048000", "R", "Spear St", "Wed", "00:00", "02:00", spearStGeometry)
        )

      // No exclusions.json written, should still work
      repository.refreshData()

      repository.getAllSpots().test {
        val spots = awaitItem()
        assertThat(spots).isNotEmpty()
        val spot = spots.first()
        // No forbidden intervals since no exclusions
        val forbidden = spot.timeline.filter { it.type is IntervalType.Forbidden }
        assertThat(forbidden).isEmpty()
      }
    }
}
