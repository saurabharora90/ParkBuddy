package dev.bongballe.parkbuddy.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.model.Weekday
import dev.bongballe.parkbuddy.testing.FakeParkingRepository
import dev.bongballe.parkbuddy.testing.FakePreferencesRepository
import dev.bongballe.parkbuddy.testing.FakeReminderNotificationManager
import dev.bongballe.parkbuddy.testing.createTestSpot
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
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
  }

  @Test
  fun `scheduleReminders for timed parking sets time limit alarms`() = runTest {
    val context = TestContext()
    val now = Clock.System.now()
    context.clock.instant = now

    val spot = createTestSpot(id = "1")
    val parkedLocation =
      ParkedLocation(
        spotId = "1",
        location = dev.bongballe.parkbuddy.model.Location(0.0, 0.0),
        parkedAt = now,
      )
    context.preferencesRepository.setParkedLocation(parkedLocation)

    context.repository.scheduleReminders(spot, showNotification = true)

    assertThat(context.alarmScheduler.scheduledAlarms).hasSize(2)
  }

  @Test
  fun `scheduleReminders for timed parking after hours sets future alarms`() = runTest {
    val context = TestContext()
    val now =
      kotlinx.datetime.LocalDateTime(2024, 1, 1, 19, 0).toInstant(TimeZone.currentSystemDefault())
    context.clock.instant = now

    val spot =
      createTestSpot(
        id = "1",
        limitMinutes = 120,
        enforcementDays =
          setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
          ),
        enforcementStart = kotlinx.datetime.LocalTime(8, 0),
        enforcementEnd = kotlinx.datetime.LocalTime(18, 0),
      )

    val parkedLocation =
      ParkedLocation(
        spotId = "1",
        location = dev.bongballe.parkbuddy.model.Location(0.0, 0.0),
        parkedAt = now,
      )
    context.preferencesRepository.setParkedLocation(parkedLocation)

    context.repository.scheduleReminders(spot, showNotification = true)

    // 2 enforcement-start (15 min warning + at start) + 2 expiry (15 min warning + at expiry)
    assertThat(context.alarmScheduler.scheduledAlarms).hasSize(4)
  }

  @Test
  fun `scheduleReminders with showNotification true shows notification`() = runTest {
    val context = TestContext()
    val now =
      kotlinx.datetime.LocalDateTime(2024, 1, 1, 12, 0).toInstant(TimeZone.currentSystemDefault())
    context.clock.instant = now

    val parkedLocation =
      ParkedLocation(
        spotId = "1",
        location = dev.bongballe.parkbuddy.model.Location(0.0, 0.0),
        parkedAt = now,
      )
    context.preferencesRepository.setParkedLocation(parkedLocation)

    val localNow = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val tomorrow = localNow.date.plus(1, DateTimeUnit.DAY)
    val schedule =
      SweepingSchedule(
        weekday = tomorrow.dayOfWeek.toWeekday(),
        fromHour = 8,
        toHour = 10,
        week1 = true,
        week2 = true,
        week3 = true,
        week4 = true,
        week5 = true,
        holidays = true,
      )

    val spot =
      createTestSpot(id = "1").copy(sweepingSchedules = listOf(schedule), streetName = "Main St")

    context.repository.addReminder(ReminderMinutes(30))
    context.repository.scheduleReminders(spot, showNotification = true)

    assertThat(context.notificationManager.lastSpotFoundNotification).isNotNull()
  }

  @Test
  fun `scheduleReminders with showNotification false suppresses notification`() = runTest {
    val context = TestContext()
    val spot = createTestSpot(id = "1")
    val parkedLocation =
      ParkedLocation(
        spotId = "1",
        location = dev.bongballe.parkbuddy.model.Location(0.0, 0.0),
        parkedAt = context.clock.now(),
      )
    context.preferencesRepository.setParkedLocation(parkedLocation)

    context.repository.scheduleReminders(spot, showNotification = false)

    assertThat(context.notificationManager.lastSpotFoundNotification).isNull()
  }

  @Test
  fun `scheduleReminders sets alarms`() = runTest {
    val context = TestContext()
    val now =
      kotlinx.datetime.LocalDateTime(2024, 1, 1, 12, 0).toInstant(TimeZone.currentSystemDefault())
    context.clock.instant = now

    val parkedLocation =
      ParkedLocation(
        spotId = "1",
        location = dev.bongballe.parkbuddy.model.Location(0.0, 0.0),
        parkedAt = now,
      )
    context.preferencesRepository.setParkedLocation(parkedLocation)

    val localNow = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val tomorrow = localNow.date.plus(1, DateTimeUnit.DAY)
    val schedule =
      SweepingSchedule(
        weekday = tomorrow.dayOfWeek.toWeekday(),
        fromHour = 8,
        toHour = 10,
        week1 = true,
        week2 = true,
        week3 = true,
        week4 = true,
        week5 = true,
        holidays = true,
      )

    val spot = createTestSpot(id = "1").copy(sweepingSchedules = listOf(schedule))

    context.repository.addReminder(ReminderMinutes(60))
    context.repository.scheduleReminders(spot, showNotification = false)

    assertThat(context.alarmScheduler.scheduledAlarms).isNotEmpty()
  }

  @Test
  fun `scheduleReminders for ActiveTimed skips cleaning reminders`() = runTest {
    val context = TestContext()
    val now =
      kotlinx.datetime.LocalDateTime(2024, 1, 1, 12, 0).toInstant(TimeZone.currentSystemDefault())
    context.clock.instant = now

    val parkedLocation =
      ParkedLocation(
        spotId = "1",
        location = dev.bongballe.parkbuddy.model.Location(0.0, 0.0),
        parkedAt = now,
      )
    context.preferencesRepository.setParkedLocation(parkedLocation)

    val localNow = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val tomorrow = localNow.date.plus(1, DateTimeUnit.DAY)
    val cleaningSchedule =
      SweepingSchedule(
        weekday = tomorrow.dayOfWeek.toWeekday(),
        fromHour = 8,
        toHour = 10,
        week1 = true,
        week2 = true,
        week3 = true,
        week4 = true,
        week5 = true,
        holidays = true,
      )

    val spot = createTestSpot(id = "1").copy(sweepingSchedules = listOf(cleaningSchedule))

    context.repository.addReminder(ReminderMinutes(60))
    context.repository.scheduleReminders(spot, showNotification = false)

    assertThat(context.alarmScheduler.scheduledAlarms).hasSize(2)
  }

  @Test
  fun `scheduleReminders for PendingTimed includes cleaning when before enforcement`() = runTest {
    val context = TestContext()
    val now =
      kotlinx.datetime.LocalDateTime(2024, 1, 1, 19, 0).toInstant(TimeZone.currentSystemDefault())
    context.clock.instant = now

    val parkedLocation =
      ParkedLocation(
        spotId = "1",
        location = dev.bongballe.parkbuddy.model.Location(0.0, 0.0),
        parkedAt = now,
      )
    context.preferencesRepository.setParkedLocation(parkedLocation)

    val cleaningSchedule =
      SweepingSchedule(
        weekday = Weekday.Tues,
        fromHour = 6,
        toHour = 7,
        week1 = true,
        week2 = true,
        week3 = true,
        week4 = true,
        week5 = true,
        holidays = true,
      )

    val spot =
      createTestSpot(
          id = "1",
          limitMinutes = 120,
          enforcementDays =
            setOf(
              DayOfWeek.MONDAY,
              DayOfWeek.TUESDAY,
              DayOfWeek.WEDNESDAY,
              DayOfWeek.THURSDAY,
              DayOfWeek.FRIDAY,
            ),
          enforcementStart = kotlinx.datetime.LocalTime(8, 0),
          enforcementEnd = kotlinx.datetime.LocalTime(18, 0),
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
      val now =
        kotlinx.datetime.LocalDateTime(2024, 1, 1, 19, 0).toInstant(TimeZone.currentSystemDefault())
      context.clock.instant = now

      val parkedLocation =
        ParkedLocation(
          spotId = "1",
          location = dev.bongballe.parkbuddy.model.Location(0.0, 0.0),
          parkedAt = now,
        )
      context.preferencesRepository.setParkedLocation(parkedLocation)

      val cleaningSchedule =
        SweepingSchedule(
          weekday = Weekday.Tues,
          fromHour = 9,
          toHour = 10,
          week1 = true,
          week2 = true,
          week3 = true,
          week4 = true,
          week5 = true,
          holidays = true,
        )

      val spot =
        createTestSpot(
            id = "1",
            limitMinutes = 120,
            enforcementDays =
              setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
              ),
            enforcementStart = kotlinx.datetime.LocalTime(8, 0),
            enforcementEnd = kotlinx.datetime.LocalTime(18, 0),
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

  private fun DayOfWeek.toWeekday(): Weekday =
    when (this) {
      DayOfWeek.MONDAY -> Weekday.Mon
      DayOfWeek.TUESDAY -> Weekday.Tues
      DayOfWeek.WEDNESDAY -> Weekday.Wed
      DayOfWeek.THURSDAY -> Weekday.Thu
      DayOfWeek.FRIDAY -> Weekday.Fri
      DayOfWeek.SATURDAY -> Weekday.Sat
      DayOfWeek.SUNDAY -> Weekday.Sun
    }
}
