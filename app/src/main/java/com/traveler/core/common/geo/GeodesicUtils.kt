package com.traveler.core.common.geo

import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Pure Kotlin geographical coordinate representation.
 * Independent of android.location.Location for testability.
 */
@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be in [-90, 90], got: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be in [-180, 180], got: $longitude" }
    }
}

object GeodesicUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0 // WGS84 mean radius

    /**
     * Calculates the great-circle distance between two points using the Haversine formula.
     * Returns distance in meters.
     */
    fun distanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val deltaLatRad = Math.toRadians(p2.latitude - p1.latitude)
        val deltaLonRad = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(deltaLatRad / 2.0).pow(2.0) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLonRad / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates initial bearing (azimuth / heading) in degrees [0, 360) from p1 to p2.
     */
    fun initialBearing(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val deltaLonRad = Math.toRadians(p2.longitude - p1.longitude)

        val y = sin(deltaLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)
        val bearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad)
        return (bearingDeg + 360.0) % 360.0
    }

    /**
     * Spherical Linear Interpolation (SLERP) along the great-circle path between p1 and p2.
     * fraction in [0.0, 1.0] (0.0 = p1, 1.0 = p2).
     */
    fun interpolate(p1: GeoPoint, p2: GeoPoint, fraction: Double): GeoPoint {
        if (fraction <= 0.0) return p1
        if (fraction >= 1.0) return p2

        val lat1 = Math.toRadians(p1.latitude)
        val lon1 = Math.toRadians(p1.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        val lon2 = Math.toRadians(p2.longitude)

        val deltaLat = lat2 - lat1
        val deltaLon = lon2 - lon1

        val a = sin(deltaLat / 2.0).pow(2.0) +
                cos(lat1) * cos(lat2) * sin(deltaLon / 2.0).pow(2.0)
        val d = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))

        if (d < 1e-9) {
            // Points are practically identical
            return p1
        }

        val sinD = sin(d)
        val aWeight = sin((1.0 - fraction) * d) / sinD
        val bWeight = sin(fraction * d) / sinD

        val x = aWeight * cos(lat1) * cos(lon1) + bWeight * cos(lat2) * cos(lon2)
        val y = aWeight * cos(lat1) * sin(lon1) + bWeight * cos(lat2) * sin(lon2)
        val z = aWeight * sin(lat1) + bWeight * sin(lat2)

        val interpolatedLat = atan2(z, sqrt(x * x + y * y))
        val interpolatedLon = atan2(y, x)

        val interpolatedAlt = if (p1.altitudeMeters != null && p2.altitudeMeters != null) {
            p1.altitudeMeters + (p2.altitudeMeters - p1.altitudeMeters) * fraction
        } else p1.altitudeMeters ?: p2.altitudeMeters

        return GeoPoint(
            latitude = Math.toDegrees(interpolatedLat),
            longitude = Math.toDegrees(interpolatedLon),
            altitudeMeters = interpolatedAlt
        )
    }

    /**
     * Calculates total distance along a sequence of points in meters.
     */
    fun pathDistanceMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += distanceMeters(points[i], points[i + 1])
        }
        return total
    }

    /**
     * Calculates the shortest geodesic distance in meters from a point to a line segment (start -> end).
     * Handles endpoints, intermediate projections, and antimeridian crossing accurately.
     */
    fun distancePointToSegmentMeters(point: GeoPoint, segmentStart: GeoPoint, segmentEnd: GeoPoint): Double {
        val totalSegmentDist = distanceMeters(segmentStart, segmentEnd)
        if (totalSegmentDist < 1e-3) {
            return distanceMeters(point, segmentStart)
        }

        val meanLatRad = Math.toRadians((segmentStart.latitude + segmentEnd.latitude + point.latitude) / 3.0)
        val cosMeanLat = cos(meanLatRad).coerceAtLeast(1e-6)

        fun normalizeDeltaLng(dLng: Double): Double {
            var d = dLng % 360.0
            if (d > 180.0) d -= 360.0
            if (d < -180.0) d += 360.0
            return d
        }

        val dLngB = normalizeDeltaLng(segmentEnd.longitude - segmentStart.longitude)
        val dLngP = normalizeDeltaLng(point.longitude - segmentStart.longitude)

        val vx = Math.toRadians(dLngB) * cosMeanLat * EARTH_RADIUS_METERS
        val vy = Math.toRadians(segmentEnd.latitude - segmentStart.latitude) * EARTH_RADIUS_METERS

        val wx = Math.toRadians(dLngP) * cosMeanLat * EARTH_RADIUS_METERS
        val wy = Math.toRadians(point.latitude - segmentStart.latitude) * EARTH_RADIUS_METERS

        val lenSq = vx * vx + vy * vy
        if (lenSq < 1e-6) {
            return distanceMeters(point, segmentStart)
        }

        val t = (wx * vx + wy * vy) / lenSq

        return when {
            t <= 0.0 -> distanceMeters(point, segmentStart)
            t >= 1.0 -> distanceMeters(point, segmentEnd)
            else -> {
                val closestPoint = interpolate(segmentStart, segmentEnd, t)
                distanceMeters(point, closestPoint)
            }
        }
    }
}
