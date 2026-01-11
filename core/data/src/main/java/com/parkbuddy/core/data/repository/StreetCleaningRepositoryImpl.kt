package com.parkbuddy.core.data.repository

import com.parkbuddy.core.data.database.StreetCleaningDao
import com.parkbuddy.core.data.database.StreetCleaningSegment
import com.parkbuddy.core.data.network.SfOpenDataApi
import com.parkbuddy.core.domain.model.StreetCleaningSegmentModel
import com.parkbuddy.core.domain.repository.StreetCleaningRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StreetCleaningRepositoryImpl @Inject constructor(
    private val dao: StreetCleaningDao,
    private val api: SfOpenDataApi,
    private val moshi: Moshi
) : StreetCleaningRepository {

    override fun getAllSegments(): Flow<List<StreetCleaningSegmentModel>> {
        return dao.getAllSegments().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getWatchedSegments(): Flow<List<StreetCleaningSegmentModel>> {
        return dao.getWatchedSegments().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun refreshData() {
        try {
            val response = api.getStreetCleaningData()
            val entities = response.mapNotNull { dto ->
                // Serialize geometry to String
                val geometryAdapter = moshi.adapter<List<List<Double>>>(
                    Types.newParameterizedType(List::class.java, List::class.java, Double::class.javaObjectType)
                )
                val locationData = dto.geometry?.coordinates?.let { geometryAdapter.toJson(it) }

                if (locationData != null) {
                    StreetCleaningSegment(
                        schedule = "${dto.weekday} ${dto.fromhour}-${dto.tohour}",
                        locationData = locationData,
                        isWatched = false
                    )
                } else {
                    null
                }
            }
            dao.insertSegments(entities)
        } catch (e: Exception) {
            // Handle error, maybe log it
            e.printStackTrace()
        }
    }

    override suspend fun setWatchStatus(id: Long, isWatched: Boolean) {
        dao.updateWatchStatus(id, isWatched)
    }

    private fun StreetCleaningSegment.toDomainModel(): StreetCleaningSegmentModel {
        return StreetCleaningSegmentModel(
            id = id,
            schedule = schedule,
            locationData = locationData,
            isWatched = isWatched
        )
    }
}