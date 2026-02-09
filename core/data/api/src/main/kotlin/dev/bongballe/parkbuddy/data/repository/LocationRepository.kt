package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.model.Location

interface LocationRepository {

  class PermissionException : Exception()
  class EmptyLocation : Exception()

  suspend fun getCurrentLocation(): Result<Location>
}
