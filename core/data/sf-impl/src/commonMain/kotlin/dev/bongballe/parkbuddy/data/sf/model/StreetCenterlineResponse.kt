package dev.bongballe.parkbuddy.data.sf.model

import dev.bongballe.parkbuddy.model.Geometry
import kotlinx.serialization.Serializable

/**
 * A street centerline from SF Open Data (Socrata dataset `3psu-pn9h`).
 *
 * This is the authoritative geometry source for the CNN street network. Every active street segment
 * in SF has a record here, unlike sweeping/meter data which only covers streets with those
 * services.
 */
@Serializable
data class StreetCenterlineResponse(
  val cnn: String = "",
  val streetname: String = "",
  val nhood: String? = null,
  val classcode: String? = null,
  val line: Geometry? = null,
)
