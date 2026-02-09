package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {

  fun getReminders(): Flow<List<ReminderMinutes>>

  suspend fun addReminder(minutesBefore: ReminderMinutes)

  suspend fun removeReminder(minutesBefore: ReminderMinutes)

  suspend fun scheduleReminders(spot: ParkingSpot)
  suspend fun clearAllReminders()
}
