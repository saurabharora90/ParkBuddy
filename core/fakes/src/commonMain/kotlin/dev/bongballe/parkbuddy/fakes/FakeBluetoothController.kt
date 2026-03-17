package dev.bongballe.parkbuddy.fakes

import dev.bongballe.parkbuddy.core.bluetooth.BluetoothController
import dev.bongballe.parkbuddy.model.BluetoothDeviceUiModel

class FakeBluetoothController : BluetoothController {
  var devices = emptyList<BluetoothDeviceUiModel>()

  override fun getPairedDevices(): List<BluetoothDeviceUiModel> = devices
}
