package com.parkbuddy.core.domain.repository

import com.parkbuddy.core.domain.model.StreetCleaningSegmentModel
import kotlinx.coroutines.flow.Flow

interface StreetCleaningRepository {
    fun getAllSegments(): Flow<List<StreetCleaningSegmentModel>>
    fun getWatchedSegments(): Flow<List<StreetCleaningSegmentModel>>
    suspend fun refreshData()
    suspend fun setWatchStatus(id: Long, isWatched: Boolean)
}