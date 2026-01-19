package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.data.network.SfOpenDataApi
import dev.bongballe.parkbuddy.database.StreetCleaningDao
import dev.bongballe.parkbuddy.database.StreetCleaningSegment
import dev.bongballe.parkbuddy.model.StreetCleaningSegmentModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class StreetCleaningRepositoryImpl(
  private val dao: StreetCleaningDao,
  private val api: SfOpenDataApi,
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
        response.map { dto ->
          StreetCleaningSegment(
            schedule = "${dto.weekday} ${dto.fromhour}-${dto.tohour}",
            locationData = dto.geometry,
            isWatched = false,
          )
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
