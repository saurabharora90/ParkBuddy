package dev.parkbuddy.feature.map

import dev.bongballe.parkbuddy.model.ProhibitionReason

/** Maps [ProhibitionReason] to a user-facing display string. */
internal fun ProhibitionReason.displayText(): String =
  when (this) {
    ProhibitionReason.TOW_AWAY -> "Tow Away Zone"
    ProhibitionReason.NO_PARKING -> "No Parking"
    ProhibitionReason.NO_STOPPING -> "No Stopping"
    ProhibitionReason.NO_OVERNIGHT -> "No Overnight Parking"
    ProhibitionReason.STREET_CLEANING -> "Street Cleaning"
    ProhibitionReason.COMMERCIAL -> "Commercial Only"
    ProhibitionReason.LOADING_ZONE -> "Loading Zone"
    ProhibitionReason.RESIDENTIAL_PERMIT -> "Residential Permit"
  }
