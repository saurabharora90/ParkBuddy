package dev.bongballe.parkbuddy.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.fakes.FakeParkingRepository
import dev.bongballe.parkbuddy.fakes.FakePreferencesRepository
import dev.bongballe.parkbuddy.fakes.FakeReminderNotificationManager
import dev.bongballe.parkbuddy.fixtures.WEEKDAYS
import dev.bongballe.parkbuddy.fixtures.createSpot
import dev.bongballe.parkbuddy.fixtures.createSweepingSchedule
import dev.bongballe.parkbuddy.fixtures.toWeekday
import dev.bongballe.parkbuddy.model.Location
import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.bongballe.parkbuddy.model.Weekday
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderRepositoryImplTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private val zone = TimeZone.of("America/Los_Angeles")

  private class FakeAlarmScheduler : AlarmScheduler {
    data class ScheduledAlarm(
      val index: Int,
      val triggerAt: Instant,
      val spotName: String,
      val spotId: String,
      val message: String,
    )

    val scheduledAlarms = mutableListOf<ScheduledAlarm>()
    var cancelAllCalled = false

    override fun setAlarm(
      index: Int,
      triggerAt: Instant,
      spotName: String,
      spotId: String,
      message: String,
    ) {
      scheduledAlarms.add(ScheduledAlarm(index, triggerAt, spotName, spotId, message))
    }

    override fun cancelAll() {
      cancelAllCalled = true
      scheduledAlarms.clear()
    }
  }

  private class FakeClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
  }

  private inner class TestContext {
    val dataStore: DataStore<Preferences> =
      PreferenceDataStoreFactory.createWithPath(
        produceFile = { tempFolder.newFile("test.preferences_pb").absolutePath.toPath() }
      )
    val alarmScheduler = FakeAlarmScheduler()
    val notificationManager = FakeReminderNotificationManager()
    val preferencesRepository = FakePreferencesRepository()
    val parkingRepository = FakeParkingRepository()
    val clock = FakeClock(Clock.System.now())
    val repository =
      ReminderRepositoryImpl(
        dataStore,
        alarmScheduler,
        notificationManager,
        preferencesRepository,
        parkingRepository,
        clock,
      )

    init {
      runBlocking { dataStore.edit { it.clear() } }
    }

    suspend fun parkAt(now: Instant, spotId: String = "1") {
      preferencesRepository.setParkedLocation(
        ParkedLocation(spotId = spotId, location = Location(0.0, 0.0), parkedAt = now)
      )
    }
  }

  private fun dateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
    LocalDateTime(year, month, day, hour, minute).toInstant(zone)

  private fun tomorrowWeekday(now: Instant): Weekday {
    val tomorrow = now.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY)
    return tomorrow.dayOfWeek.toWeekday()
  }

  @Test
  fun `scheduleReminders for timed parking sets time limit alarms`() = runTest {
    val context = TestContext()
    val now = Clock.System.now()
    context.clock.instant = now

    val spot = createSpot(id = "1")
    context.parkAt(now)

    context.repository.scheduleReminders(spot, showNotification = true)

    assertThat(context.alarmScheduler.scheduledAlarms).hasSize(2)
  }

  @Test
  fun `scheduleReminders for timed parking after hours sets future alarms`() = runTest {
    val context = TestContext()
    val now = dateTime(2024, 1, 1, 19, 0)
    context.clock.instant = now

    val spot =
      createSpot(
        id = "1",
        limitMinutes = 120,
        enforcementDays = WEEKDAYS,
        enforcementStart = LocalTime(8, 0),
        enforcementEnd = LocalTime(18, 0),
      )
    context.parkAt(now)

    context.repository.scheduleReminders(spot, showNotification = true)

    // 2 enforcement-start (15 min warning + at start) + 2 expiry (15 min warning + at expiry)
    assertThat(context.alarmScheduler.scheduledAlarms).hasSize(4)
  }

  @Test
  fun `scheduleReminders with showNotification true shows notification`() = runTest {
    val context = TestContext()
    val now = dateTime(2024, 1, 1, 12, 0)
    context.clock.instant = now
    context.parkAt(now)

    val schedule =
      createSweepingSchedule(tomorrowWeekday(now), fromHour = 8, toHour = 10, holidays = true)
    val spot =
      createSpot(id = "1").copy(sweepingSchedules = listOf(schedule), streetName = "Main St")

    context.repository.addReminder(ReminderMinutes(30))
    context.repository.scheduleReminders(spot, showNotification = true)

    assertThat(context.notificationManager.lastSpotFoundNotification).isNotNull()
  }

  @Test
  fun `scheduleReminders with showNotification false suppresses notification`() = runTest {
    val context = TestContext()
    val spot = createSpot(id = "1")
    context.parkAt(context.clock.now())

    context.repository.scheduleReminders(spot, showNotification = false)

    assertThat(context.notificationManager.lastSpotFoundNotification).isNull()
  }

  @Test
  fun `scheduleReminders sets alarms`() = runTest {
    val context = TestContext()
    val now = dateTime(2024, 1, 1, 12, 0)
    context.clock.instant = now
    context.parkAt(now)

    val schedule =
      createSweepingSchedule(tomorrowWeekday(now), fromHour = 8, toHour = 10, holidays = true)
    val spot = createSpot(id = "1").copy(sweepingSchedules = listOf(schedule))

    context.repository.addReminder(ReminderMinutes(60))
    context.repository.scheduleReminders(spot, showNotification = false)

    assertThat(context.alarmScheduler.scheduledAlarms).isNotEmpty()
  }

  @Test
  fun `scheduleReminders for ActiveTimed skips cleaning reminders`() = runTest {
    val context = TestContext()
    val now = dateTime(2024, 1, 1, 12, 0)
    context.clock.instant = now
    context.parkAt(now)

    val schedule =
      createSweepingSchedule(tomorrowWeekday(now), fromHour = 8, toHour = 10, holidays = true)
    val spot = createSpot(id = "1").copy(sweepingSchedules = listOf(schedule))

    context.repository.addReminder(ReminderMinutes(60))
    context.repository.scheduleReminders(spot, showNotification = false)

    assertThat(context.alarmScheduler.scheduledAlarms).hasSize(2)
  }

  @Test
  fun `scheduleReminders for PendingTimed includes cleaning when before enforcement`() = runTest {
    val context = TestContext()
    val now = dateTime(2024, 1, 1, 19, 0)
    context.clock.instant = now
    context.parkAt(now)

    val cleaningSchedule =
      createSweepingSchedule(Weekday.Tues, fromHour = 6, toHour = 7, holidays = true)
    val spot =
      createSpot(
          id = "1",
          limitMinutes = 120,
          enforcementDays = WEEKDAYS,
          enforcementStart = LocalTime(8, 0),
          enforcementEnd = LocalTime(18, 0),
        )
        .copy(sweepingSchedules = listOf(cleaningSchedule))

    context.repository.addReminder(ReminderMinutes(60))
    context.repository.scheduleReminders(spot, showNotification = false)

    // 1 cleaning + 2 enforcement-start (15 min warning + at start) + 2 expiry
    assertThat(context.alarmScheduler.scheduledAlarms).hasSize(5)
  }

  @Test
  fun `scheduleReminders for PendingTimed skips cleaning when after enforcement starts`() =
    runTest {
      val context = TestContext()
      val now = dateTime(2024, 1, 1, 19, 0)
      context.clock.instant = now
      context.parkAt(now)

      val cleaningSchedule =
        createSweepingSchedule(Weekday.Tues, fromHour = 9, toHour = 10, holidays = true)
      val spot =
        createSpot(
            id = "1",
            limitMinutes = 120,
            enforcementDays = WEEKDAYS,
            enforcementStart = LocalTime(8, 0),
            enforcementEnd = LocalTime(18, 0),
          )
          .copy(sweepingSchedules = listOf(cleaningSchedule))

      context.repository.addReminder(ReminderMinutes(60))
      context.repository.scheduleReminders(spot, showNotification = false)

      // 2 enforcement-start (15 min warning + at start) + 2 expiry (cleaning skipped)
      assertThat(context.alarmScheduler.scheduledAlarms).hasSize(4)
    }

  @Test
  fun `clearAllReminders cancels alarms and notifications`() = runTest {
    val context = TestContext()
    context.repository.clearAllReminders()

    assertThat(context.notificationManager.cancelAllCalled).isTrue()
    assertThat(context.alarmScheduler.cancelAllCalled).isTrue()
  }
}
