package dev.bongballe.parkbuddy.testing

import dev.bongballe.parkbuddy.data.repository.ReminderRepository
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeReminderRepository : ReminderRepository {
  private val _reminders = MutableStateFlow<List<ReminderMinutes>>(emptyList())
  override fun getReminders(): Flow<List<ReminderMinutes>> = _reminders.asStateFlow()

  override suspend fun addReminder(minutesBefore: ReminderMinutes) {
    _reminders.value = _reminders.value + minutesBefore
  }

  override suspend fun removeReminder(minutesBefore: ReminderMinutes) {
    _reminders.value = _reminders.value - minutesBefore
  }

  var scheduledSpot: ParkingSpot? = null
  override suspend fun scheduleReminders(spot: ParkingSpot) {
    scheduledSpot = spot
  }

  var clearAllRemindersCalled = false
  override suspend fun clearAllReminders() {
    clearAllRemindersCalled = true
  }
}
