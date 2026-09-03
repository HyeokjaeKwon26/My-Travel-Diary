package com.traveler.core.common.geo

import kotlin.math.*

data class WorldPoint(val x: Double, val y: Double)

object WebMercator {

    const val MIN_LAT = -85.05112878
    const val MAX_LAT = 85.05112878

    /**
     * Projects a WGS84 GeoPoint to normalized Web Mercator coordinates in [0.0 .. 1.0].
     */
    fun project(point: GeoPoint): WorldPoint {
        return project(point.latitude, point.longitude)
    }

    /**
     * Projects latitude and longitude to normalized Web Mercator coordinates in [0.0 .. 1.0].
     */
    fun project(latitude: Double, longitude: Double): WorldPoint {
        val clampedLat = latitude.coerceIn(MIN_LAT, MAX_LAT)
        val x = (longitude + 180.0) / 360.0
        val sinLat = sin(Math.toRadians(clampedLat))
        val y = 0.5 - (ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI))
        return WorldPoint(x, y.coerceIn(0.0, 1.0))
    }

    /**
     * Unprojects normalized Web Mercator coordinates in [0.0 .. 1.0] back to a WGS84 GeoPoint.
     */
    fun unproject(worldPoint: WorldPoint): GeoPoint {
        val lng = worldPoint.x * 360.0 - 180.0
        val n = PI - 2.0 * PI * worldPoint.y
        val lat = Math.toDegrees(atan(sinh(n)))
        return GeoPoint(lat.coerceIn(MIN_LAT, MAX_LAT), lng.coerceIn(-180.0, 180.0))
    }

    /**
     * Unwraps target longitude relative to reference longitude to choose the shortest path across the antimeridian.
     * E.g. Tokyo (+139.7) -> SF (-122.4): target becomes +237.6 (+360) rather than wrapping around Europe/Atlantic.
     * E.g. 179E (+179) -> 179W (-179): target becomes +181.0.
     */
    fun unwrapLongitude(referenceLng: Double, targetLng: Double): Double {
        var diff = targetLng - referenceLng
        while (diff > 180.0) diff -= 360.0
        while (diff < -180.0) diff += 360.0
        return referenceLng + diff
    }

    /**
     * Generates intermediate spherical great-circle points between start and end using true 3D spherical SLERP.
     * Guaranteed to produce exact spherical geodesic arcs across high latitudes (e.g. NY -> Seoul northern arc).
     */
    fun generateGreatCirclePath(start: GeoPoint, end: GeoPoint, steps: Int = 32): List<GeoPoint> {
        if (steps <= 1) return listOf(start, end)

        val points = ArrayList<GeoPoint>(steps + 1)
        for (i in 0..steps) {
            val fraction = i.toDouble() / steps
            val pt = GeodesicUtils.interpolate(start, end, fraction)
            points.add(pt)
        }
        return points
    }
}
