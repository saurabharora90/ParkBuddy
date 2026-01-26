package dev.bongballe.parkbuddy.data.sf.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.bongballe.parkbuddy.data.sf.database.entity.ParkingSpotEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.SweepingScheduleEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.UserPreferencesEntity
import dev.bongballe.parkbuddy.data.sf.database.model.PopulatedParkingSpot
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for parking spots, sweeping schedules, and user preferences.
 *
 * ## Data Flow
 * 1. On app startup (or manual refresh), repository fetches data from city data APIs
 * 2. Parking regulations are matched to sweeping schedules via coordinate proximity
 * 3. Data is stored via [insertSpots] and [insertSchedules]
 * 4. UI observes data via Flow-returning methods
 *
 * ## Watched Streets
 * Streets are "watched" based on user's selected RPP zone ([UserPreferencesEntity.rppZone]). Use
 * [getSpotsByZone] to get all watched spots for reminder scheduling.
 *
 * @see ParkingSpotEntity
 * @see SweepingScheduleEntity
 * @see UserPreferencesEntity
 */
@Dao
interface ParkingDao {

  // ==================== Parking Spots ====================

  /**
   * Get all parking spots with their sweeping schedules. Use [Transaction] to ensure atomic read of
   * spot + related schedules.
   */
  @Transaction
  @Query("SELECT * FROM parking_spots")
  fun getAllSpots(): Flow<List<PopulatedParkingSpot>>

  /**
   * Get parking spots in a specific RPP zone with their sweeping schedules. Used to get "watched"
   * streets when user has selected a zone.
   *
   * @param zone RPP zone letter (e.g., "N", "A")
   */
  @Transaction
  @Query("SELECT * FROM parking_spots WHERE rppArea = :zone")
  fun getSpotsByZone(zone: String): Flow<List<PopulatedParkingSpot>>

  /** Get a single parking spot by ID with its sweeping schedules. */
  @Transaction
  @Query("SELECT * FROM parking_spots WHERE objectId = :objectId")
  suspend fun getSpotById(objectId: String): PopulatedParkingSpot?

  /**
   * Get parking spot entities only (without schedules) for a zone. Lighter query when schedules
   * aren't needed.
   */
  @Query("SELECT * FROM parking_spots WHERE rppArea = :zone")
  fun getSpotEntitiesByZone(zone: String): Flow<List<ParkingSpotEntity>>

  /** Count spots in a zone. Used for "X streets watched" display. */
  @Query("SELECT COUNT(*) FROM parking_spots WHERE rppArea = :zone")
  fun countSpotsByZone(zone: String): Flow<Int>

  /**
   * Get all unique RPP zone letters that have parking spots. Used to populate zone picker dropdown.
   */
  @Query("SELECT DISTINCT rppArea FROM parking_spots WHERE rppArea IS NOT NULL ORDER BY rppArea")
  fun getAllRppZones(): Flow<List<String>>

  /** Insert or replace parking spots. Called during data refresh. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSpots(spots: List<ParkingSpotEntity>)

  /** Insert or replace sweeping schedules. Called during data refresh. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSchedules(schedules: List<SweepingScheduleEntity>)

  /**
   * Delete all parking spots. Called before data refresh to ensure clean state. Cascades to delete
   * related sweeping schedules via foreign key.
   */
  @Query("DELETE FROM parking_spots") suspend fun clearAllSpots()

  /** Delete all sweeping schedules. Called before data refresh. */
  @Query("DELETE FROM sweeping_schedules") suspend fun clearAllSchedules()

  // ==================== User Preferences ====================

  /** Get user preferences (single row, id=1). Returns null if no preferences have been set yet. */
  @Query("SELECT * FROM user_preferences WHERE id = 1")
  fun getUserPreferences(): Flow<UserPreferencesEntity?>

  /** Insert or update user preferences. */
  @Upsert suspend fun upsertUserPreferences(preferences: UserPreferencesEntity)
}
