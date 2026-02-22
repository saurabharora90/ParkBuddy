package dev.bongballe.parkbuddy.data.repository

import android.app.AlarmManager
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.model.TimedRestriction
import dev.bongballe.parkbuddy.model.Weekday
import dev.bongballe.parkbuddy.testing.FakeParkingRepository
import dev.bongballe.parkbuddy.testing.FakePreferencesRepository
import dev.bongballe.parkbuddy.testing.FakeReminderNotificationManager
import dev.bongballe.parkbuddy.testing.createTestSpot
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ReminderRepositoryImplTest {

  private class FakeClock(var instant: kotlin.time.Instant) : kotlin.time.Clock {
    override fun now(): kotlin.time.Instant = instant
  }

  private class TestContext {
    val context: Context = ApplicationProvider.getApplicationContext()
    val notificationManager = FakeReminderNotificationManager()
    val preferencesRepository = FakePreferencesRepository()
    val parkingRepository = FakeParkingRepository()
    val clock = FakeClock(Clock.System.now())
    val repository =
      ReminderRepositoryImpl(
        context,
        notificationManager,
        preferencesRepository,
        parkingRepository,
        clock,
      )

    init {
      runBlocking { context.dataStore.edit { it.clear() } }
    }
  }

  @Test
  fun `scheduleReminders for timed parking sets time limit alarms`() = runTest {
    val context = TestContext()
    val now = Clock.System.now()
    context.clock.instant = now

    // Set up timed parking (default createTestSpot has a 2-hour timedRestriction)
    val spot = createTestSpot(id = "1")
    val parkedLocation =
      ParkedLocation(
        spotId = "1",
        location = dev.bongballe.parkbuddy.model.Location(0.0, 0.0),
        parkedAt = now,
      )
    context.preferencesRepository.setParkedLocation(parkedLocation)

    context.repository.scheduleReminders(spot, showNotification = true)

    val alarmManager = context.context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val shadowAlarmManager = shadowOf(alarmManager)

    // Should have 2 alarms: one 15-min warning and one final expiry
    assertThat(shadowAlarmManager.scheduledAlarms).hasSize(2)

    // Verify notification contains expiry info
    assertThat(context.notificationManager.lastSpotFoundNotification?.second).contains("Move by")
  }

  @Test
  fun `scheduleReminders for timed parking after hours sets future alarms`() = runTest {
    val context = TestContext()
    // 7 PM on Monday Jan 1st
    val now =
      kotlinx.datetime.LocalDateTime(2024, 1, 1, 19, 0).toInstant(TimeZone.currentSystemDefault())
    context.clock.instant = now

    // Set up timed parking with 8am-6pm enforcement
    val spot =
      createTestSpot(
        id = "1",
        timedRestriction =
          TimedRestriction(
            limitHours = 2,
            days =
              setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
              ),
            startTime = kotlinx.datetime.LocalTime(8, 0),
            endTime = kotlinx.datetime.LocalTime(18, 0),
          ),
      )

    val parkedLocation =
      ParkedLocation(
        spotId = "1",
        location = dev.bongballe.parkbuddy.model.Location(0.0, 0.0),
        parkedAt = now,
      )
    context.preferencesRepository.setParkedLocation(parkedLocation)

    context.repository.scheduleReminders(spot, showNotification = true)

    val alarmManager = context.context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val shadowAlarmManager = shadowOf(alarmManager)

    // Should have 2 alarms scheduled for tomorrow (warning at 9:45 AM, expiry at 10 AM)
    assertThat(shadowAlarmManager.scheduledAlarms).hasSize(2)

    // Notification should contain pending restriction info
    assertThat(context.notificationManager.lastSpotFoundNotification?.second)
      .contains("Limit starts tomorrow")
  }

  @Test
  fun `scheduleReminders with showNotification true shows notification`() = runTest {
    val context = TestContext()
    // Monday Jan 1st 2024
    val now =
      kotlinx.datetime.LocalDateTime(2024, 1, 1, 12, 0).toInstant(TimeZone.currentSystemDefault())
    context.clock.instant = now

    // Set up parked location
    val parkedLocation =
      ParkedLocation(
        spotId = "1",
        location = dev.bongballe.parkbuddy.model.Location(0.0, 0.0),
        parkedAt = now,
      )
    context.preferencesRepository.setParkedLocation(parkedLocation)

    val localNow = now.toLocalDateTime(TimeZone.currentSystemDefault())

    // Create a schedule for tomorrow
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
    assertThat(context.notificationManager.lastSpotFoundNotification?.first).isEqualTo("Main St")
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
    // Monday Jan 1st 2024
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

    // Create a schedule for tomorrow at 8 AM
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

    context.repository.addReminder(ReminderMinutes(60)) // 1 hour before 8 AM = 7 AM
    context.repository.scheduleReminders(spot, showNotification = false)

    val alarmManager = context.context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val shadowAlarmManager = shadowOf(alarmManager)

    // Verify an alarm was scheduled
    val alarms = shadowAlarmManager.scheduledAlarms
    assertThat(alarms).isNotEmpty()
  }

  @Test
  fun `clearAllReminders cancels alarms and notifications`() = runTest {
    val context = TestContext()
    context.repository.clearAllReminders()

    assertThat(context.notificationManager.cancelAllCalled).isTrue()
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
