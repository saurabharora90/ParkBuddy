package dev.bongballe.parkbuddy.data.sf.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Database entity for user preferences.
 *
 * Single-row table (id is always 1) storing user settings.
 *
 * @property id Always 1 (single row pattern)
 * @property rppZone User's selected Residential Parking Permit zone (e.g., "N", "A"). When set, all
 *   parking spots in this zone are "watched" for cleaning reminders. Null means no zone selected.
 * @property reminderMinutes Comma-separated list of reminder times in minutes before cleaning.
 *   e.g., "60,1440" means remind 1 hour and 24 hours before. Empty string means no reminders
 *   configured.
 */
@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
  @PrimaryKey val id: Int = 1,
  val rppZone: String?,
  val reminderMinutes: String,
)
