package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.data.network.SfOpenDataApi
import dev.bongballe.parkbuddy.database.StreetCleaningDao
import dev.bongballe.parkbuddy.database.entity.CleaningScheduleEntity
import dev.bongballe.parkbuddy.database.entity.StreetSegmentEntity
import dev.bongballe.parkbuddy.database.entity.WatchedSegmentEntity
import dev.bongballe.parkbuddy.database.model.PopulatedStreetSegment
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.StreetCleaningSegmentModel
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.Weekday
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class StreetCleaningRepositoryImpl(
  private val dao: StreetCleaningDao,
  private val api: SfOpenDataApi,
) : StreetCleaningRepository {

  private val json = Json { ignoreUnknownKeys = true }

  override fun getAllSegments(): Flow<List<StreetCleaningSegmentModel>> {
    return dao.getAllSegments().map { entities -> entities.map { it.toDomainModel() } }
  }

  override fun getWatchedSegments(): Flow<List<StreetCleaningSegmentModel>> {
    return dao.getWatchedSegments().map { entities -> entities.map { it.toDomainModel() } }
  }

  override fun searchSegments(query: String): Flow<List<StreetCleaningSegmentModel>> {
    return dao.searchSegments(query).map { entities -> entities.map { it.toDomainModel() } }
  }

  override suspend fun refreshData(): Boolean {
    var offset = 0
    val limit = 1000
    var hasMoreData = true

    val segments = mutableListOf<StreetSegmentEntity>()
    val schedules = mutableListOf<CleaningScheduleEntity>()

    while (hasMoreData) {
      val result = api.getStreetCleaningData(limit = limit, offset = offset)
      if (result.isFailure) {
        return false
      }
      val body = result.getOrNull() ?: emptyList()
      if (body.isEmpty()) {
        hasMoreData = false
      } else {
        val validResponses = body.filter { it.cnn.isNotEmpty() && it.geometry != null }

        // Deduplicate segments by cnn + side (both sides of street are separate segments)
        segments.addAll(
          validResponses
            .distinctBy { "${it.cnn}_${it.cnnRightLeft}" }
            .mapNotNull { dto ->
              try {
                dto.geometry?.let { geomElement ->
                  val geom = json.decodeFromJsonElement<Geometry>(geomElement)
                  StreetSegmentEntity(
                    cnn = dto.cnn,
                    streetName = dto.streetName,
                    limits = dto.limits,
                    blockSide = dto.blockSide,
                    side = StreetSide.fromApiValue(dto.cnnRightLeft),
                    geometry = geom,
                  )
                }
              } catch (ignore: Exception) {
                null
              }
            }
        )

        schedules.addAll(
          validResponses.map { dto ->
            CleaningScheduleEntity(
              cnn = dto.cnn,
              side = StreetSide.fromApiValue(dto.cnnRightLeft),
              weekday = dto.weekday,
              fromHour = dto.fromhour,
              toHour = dto.tohour,
              week1 = dto.servicedOnFirstWeekOfMonth,
              week2 = dto.servicedOnSecondWeekOfMonth,
              week3 = dto.servicedOnThirdWeekOfMonth,
              week4 = dto.servicedOnFourthWeekOfMonth,
              week5 = dto.servicedOnFifthWeekOfMonth,
              isHoliday = dto.servicedOnHolidays,
            )
          }
        )
        offset += limit
      }
    }

    if (segments.isNotEmpty()) {
      // Clear all old data to prevent stale entries. Watched segments are preserved.
      dao.clearAllStreetSegments()
      dao.clearAllSchedules()

      dao.insertSegments(segments)
      dao.insertSchedules(schedules)
    }

    return true
  }

  override suspend fun setWatchStatus(id: String, isWatched: Boolean) {
    val (cnn, sideName) = id.split("_")
    val side = StreetSide.valueOf(sideName)
    if (isWatched) {
      dao.insertWatched(WatchedSegmentEntity(cnn = cnn, side = side))
    } else {
      dao.deleteWatched(cnn = cnn, side = side)
    }
  }

  private fun formatSchedule(schedule: CleaningScheduleEntity): String {
    val day =
      when (schedule.weekday) {
        Weekday.Mon -> "Mon"
        Weekday.Tues -> "Tue"
        Weekday.Wed -> "Wed"
        Weekday.Thu -> "Thu"
        Weekday.Fri -> "Fri"
        Weekday.Sat -> "Sat"
        Weekday.Sun -> "Sun"
        Weekday.Holiday -> "Holiday"
      }

    val from = formatHour(schedule.fromHour)
    val to = formatHour(schedule.toHour)

    return "$day $from-$to"
  }

  private fun formatHour(hourStr: String): String {
    val h = hourStr.toInt()
    return String.format(Locale.US, "%02d:00", h)
  }

  private fun PopulatedStreetSegment.toDomainModel(): StreetCleaningSegmentModel {
    val scheduleDesc =
      schedules
        .sortedBy { it.weekday.ordinal }
        .joinToString(", ") { formatSchedule(it) }
        .ifEmpty { "No Schedule" }

    val w1 = schedules.any { it.week1 }
    val w2 = schedules.any { it.week2 }
    val w3 = schedules.any { it.week3 }
    val w4 = schedules.any { it.week4 }
    val w5 = schedules.any { it.week5 }
    val holidays = schedules.any { it.isHoliday }

    return StreetCleaningSegmentModel(
      cnn = segment.cnn,
      streetName = segment.streetName,
      limits = segment.limits,
      blockSide = segment.blockSide,
      side = segment.side,
      schedule = scheduleDesc,
      locationData = segment.geometry,
      isWatched = watchStatus != null,
      weeks = listOf(w1, w2, w3, w4, w5),
      servicedOnHolidays = holidays,
    )
  }
}
