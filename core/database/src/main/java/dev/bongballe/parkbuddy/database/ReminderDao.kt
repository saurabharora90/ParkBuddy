package dev.bongballe.parkbuddy.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
  @Query("SELECT * FROM reminder_settings ORDER BY minutesBefore ASC")
  fun getReminders(): Flow<List<ReminderSetting>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertReminder(reminder: ReminderSetting)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertReminders(reminders: List<ReminderSetting>)

  @Delete suspend fun deleteReminder(reminder: ReminderSetting)

  @Query("DELETE FROM reminder_settings") suspend fun clearReminders()

  @Query("DELETE FROM reminder_settings WHERE minutesBefore = :minutes")
  suspend fun deleteReminderByMinutes(minutes: Int)
}
