package dev.bongballe.parkbuddy.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dev.bongballe.parkbuddy.model.Location
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.tasks.await

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class LocationRepositoryImpl(private val context: Context) : LocationRepository {

  override suspend fun getCurrentLocation(): Result<Location> {
    if (
      ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      return Result.failure(LocationRepository.PermissionException())
    }

    val locationClient = LocationServices.getFusedLocationProviderClient(context)
    val cts = CancellationTokenSource()

    val location =
      locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()

    if (location == null) {
      return Result.failure(LocationRepository.EmptyLocation())
    }

    return Result.success(Location(location.latitude, location.longitude))
  }
}
