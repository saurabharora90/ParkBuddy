package dev.bongballe.parkbuddy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StreetCleaningDao {
  @Query("SELECT * FROM street_cleaning_segments")
  fun getAllSegments(): Flow<List<StreetCleaningSegment>>

  @Query("SELECT * FROM street_cleaning_segments WHERE isWatched = 1")
  fun getWatchedSegments(): Flow<List<StreetCleaningSegment>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSegments(segments: List<StreetCleaningSegment>)

  @Query("UPDATE street_cleaning_segments SET isWatched = :isWatched WHERE id = :id")
  suspend fun updateWatchStatus(id: Long, isWatched: Boolean)
}
