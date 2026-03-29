package dev.bongballe.parkbuddy.data.sf.model.arcgis

import dev.bongballe.parkbuddy.model.Geometry
import kotlinx.serialization.Serializable

/**
 * Generic envelope for ArcGIS Feature Query responses.
 *
 * ArcGIS REST API returns features in this structure regardless of layer. The [T] parameter is the
 * attribute type (different per layer). Pagination is signaled by [exceededTransferLimit]: when
 * true, there are more records to fetch.
 */
@Serializable
data class ArcGisFeatureResponse<T>(
  val features: List<ArcGisFeature<T>> = emptyList(),
  val exceededTransferLimit: Boolean = false,
)

@Serializable data class ArcGisFeature<T>(val attributes: T, val geometry: ArcGisGeometry? = null)

/**
 * ArcGIS geometry types. Polylines use `paths`, points use `x`/`y`.
 *
 * We use a single class with all nullable fields rather than a sealed hierarchy because
 * kotlinx-serialization needs to deserialize this polymorphically based on which fields are
 * present, and the ArcGIS response doesn't include a type discriminator in the geometry object
 * itself.
 */
@Serializable
data class ArcGisGeometry(
  val paths: List<List<List<Double>>>? = null,
  val x: Double? = null,
  val y: Double? = null,
  val points: List<List<Double>>? = null,
)

/** Converts ArcGIS polyline geometry to our domain [Geometry] model. */
fun ArcGisGeometry.toLineGeometry(): Geometry? {
  val coords = paths?.flatMap { it } ?: return null
  if (coords.size < 2) return null
  return Geometry("LineString", coords)
}
