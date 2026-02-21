package dev.bongballe.parkbuddy.testing

import dev.bongballe.parkbuddy.core.bluetooth.BluetoothController
import dev.bongballe.parkbuddy.core.bluetooth.BluetoothDeviceUiModel

class FakeBluetoothController : BluetoothController {
  var devices = emptyList<BluetoothDeviceUiModel>()

  override fun getPairedDevices(): List<BluetoothDeviceUiModel> = devices
}
