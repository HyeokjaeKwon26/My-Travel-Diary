package com.traveler.core.common.geo

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ramer-Douglas-Peucker (RDP) algorithm for polyline simplification.
 * Reduces the number of points in a curve that is approximated by a series of points.
 */
object DouglasPeucker {

    /**
     * Simplifies a list of GeoPoints using the given epsilon tolerance in meters.
     */
    fun simplify(points: List<GeoPoint>, epsilonMeters: Double): List<GeoPoint> {
        if (points.size <= 2 || epsilonMeters <= 0.0) return points
        return rdpRecursive(points, epsilonMeters)
    }

    private fun rdpRecursive(points: List<GeoPoint>, epsilonMeters: Double): List<GeoPoint> {
        var dMax = 0.0
        var index = 0
        val end = points.size - 1

        for (i in 1 until end) {
            val d = perpendicularDistanceMeters(points[i], points[0], points[end])
            if (d > dMax) {
                index = i
                dMax = d
            }
        }

        return if (dMax > epsilonMeters) {
            val recResults1 = rdpRecursive(points.subList(0, index + 1), epsilonMeters)
            val recResults2 = rdpRecursive(points.subList(index, points.size), epsilonMeters)
            recResults1.dropLast(1) + recResults2
        } else {
            listOf(points.first(), points.last())
        }
    }

    /**
     * Calculates perpendicular distance from point p to the great-circle segment between lineStart and lineEnd in meters.
     */
    private fun perpendicularDistanceMeters(p: GeoPoint, lineStart: GeoPoint, lineEnd: GeoPoint): Double {
        val segmentLength = GeodesicUtils.distanceMeters(lineStart, lineEnd)
        if (segmentLength < 1e-6) {
            return GeodesicUtils.distanceMeters(p, lineStart)
        }

        // Cross-track distance formula on a sphere
        val d13 = GeodesicUtils.distanceMeters(lineStart, p) / 6371000.0 // angular distance
        val brng13 = Math.toRadians(GeodesicUtils.initialBearing(lineStart, p))
        val brng12 = Math.toRadians(GeodesicUtils.initialBearing(lineStart, lineEnd))

        val dxt = kotlin.math.asin(sin(d13) * sin(brng13 - brng12)) * 6371000.0
        return abs(dxt)
    }
}
