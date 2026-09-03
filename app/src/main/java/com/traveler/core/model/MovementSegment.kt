package com.traveler.core.model

import com.traveler.core.common.geo.GeoPoint
import kotlinx.serialization.Serializable

@Serializable
enum class GeometryProvenance {
    OBSERVED,
    SIMPLIFIED_OBSERVED,
    ENDPOINT_INTERPOLATED,
    ESTIMATED_GEODESIC,
    CONTINUITY_ESTIMATE,
    UNKNOWN
}

@Serializable
data class MovementSegment(
    val id: String,
    val startTimestampEpochMs: Long,
    val endTimestampEpochMs: Long,
    val startPoint: GeoPoint,
    val endPoint: GeoPoint,
    val rawPoints: List<LocationPoint> = emptyList(),
    val simplifiedPoints: List<GeoPoint> = emptyList(),
    val distanceMeters: Double,
    val durationMillis: Long,
    val transport: TransportPrediction,
    val startTimezoneId: String? = null,
    val endTimezoneId: String? = null,
    val userOverrideMode: TransportMode? = null,
    val isUserOverride: Boolean = false,
    val geometryProvenance: GeometryProvenance = GeometryProvenance.UNKNOWN
) {
    val effectiveMode: TransportMode
        get() = userOverrideMode ?: transport.mode

    val averageSpeedKmh: Double
        get() {
            val hours = durationMillis / 3_600_000.0
            return if (hours > 0.0) (distanceMeters / 1000.0) / hours else 0.0
        }
}
