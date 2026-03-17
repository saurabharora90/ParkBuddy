package dev.bongballe.parkbuddy.core.bluetooth

import dev.bongballe.parkbuddy.model.BluetoothDeviceUiModel

interface BluetoothController {
  fun getPairedDevices(): List<BluetoothDeviceUiModel>
}
