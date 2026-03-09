package dev.parkbuddy.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.parkbuddy.core.ui.resources.Res
import dev.parkbuddy.core.ui.resources.ic_access_time
import dev.parkbuddy.core.ui.resources.ic_add
import dev.parkbuddy.core.ui.resources.ic_bluetooth
import dev.parkbuddy.core.ui.resources.ic_bluetooth_connected
import dev.parkbuddy.core.ui.resources.ic_bluetooth_disabled
import dev.parkbuddy.core.ui.resources.ic_check
import dev.parkbuddy.core.ui.resources.ic_check_circle
import dev.parkbuddy.core.ui.resources.ic_cleaning_services
import dev.parkbuddy.core.ui.resources.ic_delete
import dev.parkbuddy.core.ui.resources.ic_directions_car
import dev.parkbuddy.core.ui.resources.ic_edit_road
import dev.parkbuddy.core.ui.resources.ic_error
import dev.parkbuddy.core.ui.resources.ic_favorite
import dev.parkbuddy.core.ui.resources.ic_info
import dev.parkbuddy.core.ui.resources.ic_keyboard_arrow_down
import dev.parkbuddy.core.ui.resources.ic_location_city
import dev.parkbuddy.core.ui.resources.ic_location_on
import dev.parkbuddy.core.ui.resources.ic_map
import dev.parkbuddy.core.ui.resources.ic_notifications_active
import dev.parkbuddy.core.ui.resources.ic_notifications_off
import dev.parkbuddy.core.ui.resources.ic_open_in_new
import dev.parkbuddy.core.ui.resources.ic_person
import dev.parkbuddy.core.ui.resources.ic_radio_button_unchecked
import dev.parkbuddy.core.ui.resources.ic_remove_circle_outline
import dev.parkbuddy.core.ui.resources.ic_safety_check
import dev.parkbuddy.core.ui.resources.ic_share
import dev.parkbuddy.core.ui.resources.ic_visibility
import dev.parkbuddy.core.ui.resources.ic_warning
import org.jetbrains.compose.resources.vectorResource

object ParkBuddyIcons {
  val AccessTime: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_access_time)

  val Add: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_add)

  val Bluetooth: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_bluetooth)

  val BluetoothConnected: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_bluetooth_connected)

  val BluetoothDisabled: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_bluetooth_disabled)

  val Check: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_check)

  val CheckCircle: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_check_circle)

  val CleaningServices: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cleaning_services)

  val Delete: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_delete)

  val DirectionsCar: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_directions_car)

  val EditRoad: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_edit_road)

  val Error: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_error)

  val Favorite: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_favorite)

  val Info: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_info)

  val KeyboardArrowDown: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_keyboard_arrow_down)

  val LocationCity: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_location_city)

  val LocationOn: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_location_on)

  val Map: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_map)

  val NotificationsActive: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_notifications_active)

  val NotificationsOff: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_notifications_off)

  val OpenInNew: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_open_in_new)

  val Person: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_person)

  val RadioButtonUnchecked: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_radio_button_unchecked)

  val RemoveCircleOutline: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_remove_circle_outline)

  val SafetyCheck: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_safety_check)

  val Share: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_share)

  val Visibility: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_visibility)

  val Warning: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_warning)
}
