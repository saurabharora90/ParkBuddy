package dev.bongballe.parkbuddy.core.bluetooth.impl

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import dev.bongballe.parkbuddy.core.bluetooth.BluetoothController
import dev.bongballe.parkbuddy.core.bluetooth.BluetoothDeviceUiModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class BluetoothControllerImpl(private val context: Context) : BluetoothController {

  override fun getPairedDevices(): List<BluetoothDeviceUiModel> {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter = bluetoothManager?.adapter

    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
      return emptyList()
    }

    val pairedDevices: Set<BluetoothDevice> =
      if (
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
          PackageManager.PERMISSION_GRANTED
      ) {
        emptySet()
      } else {
        bluetoothAdapter.bondedDevices ?: emptySet()
      }

    return pairedDevices.map { device ->
      BluetoothDeviceUiModel(name = device.name ?: "Unknown Device", address = device.address)
    }
  }
}
