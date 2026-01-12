package dev.bongballe.parkbuddy.repository

import dev.bongballe.parkbuddy.model.StreetCleaningSegmentModel
import kotlinx.coroutines.flow.Flow

interface StreetCleaningRepository {
  fun getAllSegments(): Flow<List<StreetCleaningSegmentModel>>

  fun getWatchedSegments(): Flow<List<StreetCleaningSegmentModel>>

  suspend fun refreshData()

  suspend fun setWatchStatus(id: Long, isWatched: Boolean)
}
