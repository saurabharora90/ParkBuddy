package dev.bongballe.parkbuddy.core.bluetooth

interface BluetoothController {
  fun getPairedDevices(): List<BluetoothDeviceUiModel>
}
