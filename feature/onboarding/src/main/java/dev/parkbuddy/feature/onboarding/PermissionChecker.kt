package dev.parkbuddy.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionChecker {
  fun areLocationPermissionsGranted(context: Context): Boolean {
    val fineLocationGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    val backgroundLocationGranted =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
      } else {
        fineLocationGranted
      }

    return fineLocationGranted && backgroundLocationGranted
  }

  fun areBluetoothPermissionsGranted(context: Context): Boolean {
    val bluetoothPermissions =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
      } else {
        listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
      }

    return bluetoothPermissions.all { permission ->
      ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
  }

  fun areAllPermissionsGranted(context: Context): Boolean {
    return areLocationPermissionsGranted(context) && areBluetoothPermissionsGranted(context)
  }
}
