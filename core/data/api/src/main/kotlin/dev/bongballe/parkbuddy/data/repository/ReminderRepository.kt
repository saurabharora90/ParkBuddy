package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.model.ParkingSpot

interface ReminderRepository {
  suspend fun scheduleReminders(spot: ParkingSpot)
  suspend fun clearAllReminders()
}
