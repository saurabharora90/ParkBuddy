package dev.bongballe.parkbuddy.testing

import dev.bongballe.parkbuddy.data.repository.LocationRepository
import dev.bongballe.parkbuddy.model.Location

class FakeLocationRepository : LocationRepository {
  var locationResult: Result<Location> = Result.failure(LocationRepository.EmptyLocation())

  override suspend fun getCurrentLocation(): Result<Location> {
    return locationResult
  }
}
