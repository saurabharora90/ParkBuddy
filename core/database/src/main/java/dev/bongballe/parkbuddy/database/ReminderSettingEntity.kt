package dev.bongballe.parkbuddy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_settings")
data class ReminderSettingEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val minutesBefore: Int, // e.g. 1440 for 24 hours
  val isEnabled: Boolean = true,
)
