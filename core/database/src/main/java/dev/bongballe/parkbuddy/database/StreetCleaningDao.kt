package dev.bongballe.parkbuddy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import dev.bongballe.parkbuddy.database.entity.CleaningScheduleEntity
import dev.bongballe.parkbuddy.database.entity.StreetSegmentEntity
import dev.bongballe.parkbuddy.database.entity.WatchedSegmentEntity
import dev.bongballe.parkbuddy.database.model.PopulatedStreetSegment
import dev.bongballe.parkbuddy.model.StreetSide
import kotlinx.coroutines.flow.Flow

@Dao
interface StreetCleaningDao {
  @Transaction
  @RewriteQueriesToDropUnusedColumns
  @Query("SELECT * FROM street_segments")
  fun getAllSegments(): Flow<List<PopulatedStreetSegment>>

  @Transaction
  @RewriteQueriesToDropUnusedColumns
  @Query(
    "SELECT * FROM street_segments INNER JOIN watched_segments ON street_segments.cnn = watched_segments.cnn AND street_segments.side = watched_segments.side"
  )
  fun getWatchedSegments(): Flow<List<PopulatedStreetSegment>>

  @Transaction
  @RewriteQueriesToDropUnusedColumns
  @Query(
    "SELECT * FROM street_segments WHERE streetName LIKE '%' || :query || '%' OR limits LIKE '%' || :query || '%'"
  )
  fun searchSegments(query: String): Flow<List<PopulatedStreetSegment>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSegments(segments: List<StreetSegmentEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSchedules(schedules: List<CleaningScheduleEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertWatched(watched: WatchedSegmentEntity)

  @Query("DELETE FROM watched_segments WHERE cnn = :cnn AND side = :side")
  suspend fun deleteWatched(cnn: String, side: StreetSide)

  @Query("DELETE FROM cleaning_schedules") suspend fun clearAllSchedules()

  @Query("DELETE FROM street_segments") suspend fun clearAllStreetSegments()
}
