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
import dev.bongballe.parkbuddy.model.Weekday
import dev.bongballe.parkbuddy.testing.FakeAnalyticsTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
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
        api: FakeSfOpenDataApi,
        analyticsTracker: FakeAnalyticsTracker,
        db: ParkBuddyDatabase,
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
      val analyticsTracker = FakeAnalyticsTracker()

      val repository =
        ParkingRepositoryImpl(
          dao = db.parkingDao(),
          api = api,
          analyticsTracker = analyticsTracker,
          defaultDispatcher = UnconfinedTestDispatcher(),
        )

      try {
        block(repository, api, analyticsTracker, db)
      } finally {
        db.close()
      }
    }

  @Test
  fun `refreshData fetches and matches data correctly`() = runRepoTest { repository, api, _, _ ->
    val geometry =
      Geometry(
        type = "LineString",
        coordinates = listOf(listOf(-122.4, 37.7), listOf(-122.4, 37.8)),
      )
    val geometryJson = Json.encodeToJsonElement(geometry)

    api.parkingRegulations =
      listOf(
        ParkingRegulationResponse(
          objectId = "1",
          regulation = "Time Limited",
          shape = geometryJson,
          neighborhood = "Test",
          hrLimit = "2",
          hrsBegin = "0800",
          hrsEnd = "1800",
          days = "M-F",
          rppArea1 = "A",
        )
      )

    api.streetCleaningData =
      listOf(
        StreetCleaningResponse(
          cnn = "123",
          cnnRightLeft = "L",
          weekday = Weekday.Mon,
          fromhour = "08:00",
          tohour = "10:00",
          geometry = geometryJson,
          streetName = "Test St",
          limits = "100-200",
          servicedOnFirstWeekOfMonth = true,
          servicedOnSecondWeekOfMonth = true,
          servicedOnThirdWeekOfMonth = true,
          servicedOnFourthWeekOfMonth = true,
          servicedOnFifthWeekOfMonth = true,
          servicedOnHolidays = false,
        )
      )

    val success = repository.refreshData()
    assertThat(success).isTrue()

    repository.getAllSpots().test {
      val spots = awaitItem()
      assertThat(spots).hasSize(1)
      assertThat(spots[0].objectId).isEqualTo("reg_1")
      assertThat(spots[0].rppArea).isEqualTo("A")
      assertThat(spots[0].sweepingSchedules).hasSize(1)
      assertThat(spots[0].sweepingSchedules[0].weekday.name).isEqualTo("Mon")
    }
  }

  @Test
  fun `refreshData handles meter inventory and deduplicates correctly`() =
    runRepoTest { repository, api, _, _ ->
      val geometry =
        Geometry(
          type = "LineString",
          coordinates = listOf(listOf(-122.4, 37.7), listOf(-122.4, 37.8)),
        )
      val geometryJson = Json.encodeToJsonElement(geometry)

      // CNN 123 has a regulation
      api.parkingRegulations =
        listOf(
          ParkingRegulationResponse(
            objectId = "reg1",
            regulation = "Time Limited",
            shape = geometryJson,
            neighborhood = "Test",
            rppArea1 = "A",
          )
        )

      // CNN 123 also has meters (should be deduplicated)
      // CNN 456 only has meters (should be added as gap-filler)
      api.parkingMeterInventory =
        listOf(
          ParkingMeterResponse(
            objectId = "meter1",
            streetSegCtrlnId = "123",
            streetName = "Test St",
            neighborhood = "Test",
          ),
          ParkingMeterResponse(
            objectId = "meter2",
            streetSegCtrlnId = "456",
            streetName = "Metered St",
            neighborhood = "Commercial",
          ),
        )

      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "123",
            cnnRightLeft = "R",
            weekday = Weekday.Mon,
            geometry = geometryJson,
            streetName = "Test St",
          ),
          StreetCleaningResponse(
            cnn = "456",
            cnnRightLeft = "R",
            weekday = Weekday.Tues,
            geometry = geometryJson,
            streetName = "Metered St",
          ),
        )

      val success = repository.refreshData()
      assertThat(success).isTrue()

      repository.getAllSpots().test {
        val spots = awaitItem().sortedBy { it.objectId }
        // Should have 2 spots: reg_reg1 and meter_456_R
        // meter_123_R should be deduplicated because reg_reg1 covers it
        assertThat(spots).hasSize(2)
        assertThat(spots[0].objectId).isEqualTo("meter_456_R")
        assertThat(spots[0].regulation.name).isEqualTo("METERED")
        assertThat(spots[1].objectId).isEqualTo("reg_reg1")
        assertThat(spots[1].rppArea).isEqualTo("A")
      }
    }

  @Test
  fun `refreshData fetches and stores meter schedules correctly`() =
    runRepoTest { repository, api, _, _ ->
      val geometry =
        Geometry(
          type = "LineString",
          coordinates = listOf(listOf(-122.4, 37.7), listOf(-122.4, 37.8)),
        )
      val geometryJson = Json.encodeToJsonElement(geometry)

      api.parkingMeterInventory =
        listOf(
          ParkingMeterResponse(
            objectId = "m1",
            postId = "post-123",
            streetSegCtrlnId = "789",
            streetName = "Metered St",
          )
        )

      api.meterSchedules =
        listOf(
          MeterScheduleResponse(
            postId = "post-123",
            daysApplied = "Mo,Tu",
            fromTime = "9:00 AM",
            toTime = "6:00 PM",
            timeLimit = "120 minutes",
            scheduleType = "Operating Schedule",
          ),
          MeterScheduleResponse(
            postId = "post-123",
            daysApplied = "Mo,Tu",
            fromTime = "7:00 AM",
            toTime = "9:00 AM",
            timeLimit = "0 minutes",
            scheduleType = "Tow",
          ),
        )

      api.streetCleaningData =
        listOf(
          StreetCleaningResponse(
            cnn = "789",
            cnnRightLeft = "L",
            weekday = Weekday.Mon,
            geometry = geometryJson,
          )
        )

      val success = repository.refreshData()
      assertThat(success).isTrue()

      repository.getAllSpots().test {
        val spots = awaitItem()
        assertThat(spots).hasSize(1)
        val spot = spots[0]
        assertThat(spot.regulation.name).isEqualTo("METERED")
        assertThat(spot.meterSchedules).hasSize(2)

        val operating = spot.meterSchedules.find { !it.isTowZone }!!
        assertThat(operating.timeLimitMinutes).isEqualTo(120)
        assertThat(operating.startTime.hour).isEqualTo(9)

        val tow = spot.meterSchedules.find { it.isTowZone }!!
        assertThat(tow.isTowZone).isTrue()
        assertThat(tow.startTime.hour).isEqualTo(7)
      }
    }
}
