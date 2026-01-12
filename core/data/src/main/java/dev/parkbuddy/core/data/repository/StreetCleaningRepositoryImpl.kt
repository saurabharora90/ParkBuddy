package dev.parkbuddy.core.data.repository

import dev.parkbuddy.core.data.database.StreetCleaningDao
import dev.parkbuddy.core.data.database.StreetCleaningSegment
import dev.parkbuddy.core.data.network.SfOpenDataApi
import dev.bongballe.parkbuddy.model.StreetCleaningSegmentModel
import dev.bongballe.parkbuddy.repository.StreetCleaningRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Inject
class StreetCleaningRepositoryImpl(
  private val dao: StreetCleaningDao,
  private val api: SfOpenDataApi,
  private val json: Json,
) : StreetCleaningRepository {

  override fun getAllSegments(): Flow<List<StreetCleaningSegmentModel>> {
    return dao.getAllSegments().map { entities -> entities.map { it.toDomainModel() } }
  }

  override fun getWatchedSegments(): Flow<List<StreetCleaningSegmentModel>> {
    return dao.getWatchedSegments().map { entities -> entities.map { it.toDomainModel() } }
  }

  override suspend fun refreshData() {
    try {
      val response = api.getStreetCleaningData()
      val entities =
        response.mapNotNull { dto ->
          // Serialize geometry to String
          val locationData = dto.geometry?.coordinates?.let { json.encodeToString(it) }

          if (locationData != null) {
            StreetCleaningSegment(
              schedule = "${dto.weekday} ${dto.fromhour}-${dto.tohour}",
              locationData = locationData,
              isWatched = false,
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
      isWatched = isWatched,
    )
  }
}
