package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.model.Location
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosLocationRepository : LocationRepository {

  @OptIn(ExperimentalForeignApi::class)
  override suspend fun getCurrentLocation(): Result<Location> =
    suspendCancellableCoroutine { continuation ->
      val manager = CLLocationManager()
      manager.desiredAccuracy = kCLLocationAccuracyBest

      val status = CLLocationManager.authorizationStatus()
      if (
        status != kCLAuthorizationStatusAuthorizedWhenInUse &&
          status != kCLAuthorizationStatusAuthorizedAlways
      ) {
        continuation.resumeWith(
          Result.success(Result.failure(LocationRepository.PermissionException()))
        )
        return@suspendCancellableCoroutine
      }

      val delegate =
        object : NSObject(), CLLocationManagerDelegateProtocol {
          override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val clLocation = didUpdateLocations.lastOrNull() as? CLLocation
            if (clLocation != null) {
              val location =
                clLocation.coordinate.useContents {
                  Location(latitude = latitude, longitude = longitude)
                }
              continuation.resumeWith(Result.success(Result.success(location)))
            } else {
              continuation.resumeWith(
                Result.success(Result.failure(LocationRepository.EmptyLocation()))
              )
            }
            manager.stopUpdatingLocation()
          }

          override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            continuation.resumeWith(
              Result.success(Result.failure(LocationRepository.EmptyLocation()))
            )
          }
        }

      manager.delegate = delegate
      manager.requestLocation()

      continuation.invokeOnCancellation { manager.stopUpdatingLocation() }
    }
}
