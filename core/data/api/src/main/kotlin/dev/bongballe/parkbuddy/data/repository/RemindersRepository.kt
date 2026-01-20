package dev.bongballe.parkbuddy.data.repository

import kotlinx.coroutines.flow.Flow

interface RemindersRepository {
  fun getReminders(): Flow<List<Int>> // Return list of minutesBefore
  suspend fun addReminder(minutesBefore: Int)
  suspend fun removeReminder(minutesBefore: Int)
}
