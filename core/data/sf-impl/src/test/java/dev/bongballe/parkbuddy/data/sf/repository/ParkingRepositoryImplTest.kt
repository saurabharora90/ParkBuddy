package dev.bongballe.parkbuddy.data.sf.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.data.sf.database.ParkBuddyDatabase
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
          ioDispatcher = UnconfinedTestDispatcher(),
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
      assertThat(spots[0].objectId).isEqualTo("1")
      assertThat(spots[0].rppArea).isEqualTo("A")
      assertThat(spots[0].sweepingSchedules).hasSize(1)
      assertThat(spots[0].sweepingSchedules[0].weekday.name).isEqualTo("Mon")
    }
  }
}
