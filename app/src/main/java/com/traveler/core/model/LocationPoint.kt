package com.traveler.core.model

import com.traveler.core.common.geo.GeoPoint
import kotlinx.serialization.Serializable

@Serializable
enum class LocationSource {
    TIMELINE_JSON,
    GPX,
    GEOJSON,
    GPS_EXIF,
    DEVICE_SENSOR,
    MANUAL_USER
}

@Serializable
data class LocationPoint(
    val id: String,
    val timestampEpochMs: Long,
    val coordinate: GeoPoint,
    val source: LocationSource = LocationSource.TIMELINE_JSON
)
