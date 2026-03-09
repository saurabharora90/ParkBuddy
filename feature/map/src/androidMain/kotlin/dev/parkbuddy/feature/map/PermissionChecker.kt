package dev.parkbuddy.feature.map

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ActivityCompat
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

  fun areNotificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED
  }

  fun areExactAlarmsPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
  }

  fun isBatteryOptimizationDisabled(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
  }

  fun areAllPermissionsGranted(context: Context): Boolean {
    return areLocationPermissionsGranted(context) &&
      areBluetoothPermissionsGranted(context) &&
      areNotificationPermissionGranted(context) &&
      areExactAlarmsPermissionGranted(context) &&
      isBatteryOptimizationDisabled(context)
  }

  /**
   * Returns the raw permission strings that are missing AND can still be prompted via the runtime
   * dialog (shouldShowRequestPermissionRationale returns true). Empty means all missing permissions
   * are permanently denied and need Settings.
   */
  fun getPromptablePermissions(activity: Activity): List<String> = buildList {
    val candidates = buildList {
      if (
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) !=
          PackageManager.PERMISSION_GRANTED
      ) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
      } else if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
          ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
          ) != PackageManager.PERMISSION_GRANTED
      ) {
        add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
      }

      val btPerms =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
          listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
      btPerms
        .filter {
          ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        .let { addAll(it) }

      if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
      ) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
    candidates
      .filter { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
      .let { addAll(it) }
  }

  fun getMissingPermissionLabels(context: Context): List<String> = buildList {
    val hasFineLocation =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    if (!hasFineLocation) {
      add("Location")
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val hasBg =
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
      if (!hasBg) add("Background location")
    }

    if (!areBluetoothPermissionsGranted(context)) add("Bluetooth")
    if (!areNotificationPermissionGranted(context)) add("Notifications")
  }
}
