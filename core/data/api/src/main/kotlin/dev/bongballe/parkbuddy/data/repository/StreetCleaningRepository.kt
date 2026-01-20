package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.model.StreetCleaningSegmentModel
import kotlinx.coroutines.flow.Flow

interface StreetCleaningRepository {
  fun getAllSegments(): Flow<List<StreetCleaningSegmentModel>>

  fun getWatchedSegments(): Flow<List<StreetCleaningSegmentModel>>

  fun searchSegments(query: String): Flow<List<StreetCleaningSegmentModel>>

  /**
   * Refreshes the street cleaning data
   * @return true if data was refreshed, false otherwise
   */
  suspend fun refreshData() : Boolean

  suspend fun setWatchStatus(id: String, isWatched: Boolean)
}
