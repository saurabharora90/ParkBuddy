package dev.parkbuddy.feature.reminders

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.analytics.AnalyticsTracker
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.bongballe.parkbuddy.data.repository.ReminderNotificationManager
import dev.bongballe.parkbuddy.data.repository.ReminderRepository
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.bongballe.parkbuddy.qualifier.WithScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.BroadcastReceiverKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class, binding<BroadcastReceiver>())
@BroadcastReceiverKey(BluetoothConnectionReceiver::class)
@Inject
class BluetoothConnectionReceiver(
  private val preferencesRepository: PreferencesRepository,
  private val parkingRepository: ParkingRepository,
  private val reminderRepository: ReminderRepository,
  private val notificationManager: ReminderNotificationManager,
  private val analyticsTracker: AnalyticsTracker,
  @WithScope(AppScope::class) private val coroutineScope: CoroutineScope,
  @WithDispatcherType(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
      val device =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
          @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

      device?.let { disconnectedDevice ->
        val pendingResult = goAsync()

        coroutineScope.launch(ioDispatcher) {
          try {
            val savedAddress = preferencesRepository.bluetoothDeviceAddress.firstOrNull()
            val isAutoTrackingEnabled =
              preferencesRepository.isAutoTrackingEnabled.firstOrNull() ?: true

            if (savedAddress == disconnectedDevice.address && isAutoTrackingEnabled) {
              val parkingManager =
                ParkingManager(
                  context,
                  parkingRepository,
                  preferencesRepository,
                  reminderRepository,
                  notificationManager,
                  analyticsTracker,
                )
              parkingManager.processParkingEvent()
            }
          } catch (e: Exception) {
            analyticsTracker.logNonFatal(e, "Error in BluetoothConnectionReceiver")
          } finally {
            pendingResult.finish()
          }
        }
      }
    }
  }
}
