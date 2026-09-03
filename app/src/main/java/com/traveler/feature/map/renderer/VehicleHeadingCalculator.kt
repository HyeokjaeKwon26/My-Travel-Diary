package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.model.TransportMode
import kotlin.math.abs

data class HeadingSample(
    val distanceMeters: Double,
    val headingDegrees: Float
)

class PrecomputedHeadingTrack(
    val samples: List<HeadingSample>,
    val totalDistanceMeters: Double
) {
    fun evaluate(distanceMeters: Double): Float {
        if (samples.isEmpty()) return 0.0f
        if (samples.size == 1 || distanceMeters <= samples.first().distanceMeters) return samples.first().headingDegrees
        if (distanceMeters >= samples.last().distanceMeters) return samples.last().headingDegrees

        val idx = samples.binarySearchBy(distanceMeters) { it.distanceMeters }
        if (idx >= 0) return samples[idx].headingDegrees

        val insertIdx = -(idx + 1)
        val s1 = samples[insertIdx - 1]
        val s2 = samples[insertIdx]
        val span = s2.distanceMeters - s1.distanceMeters
        val frac = if (span > 0.0) ((distanceMeters - s1.distanceMeters) / span).toFloat().coerceIn(0f, 1f) else 0.0f

        val delta = VehicleHeadingCalculator.shortestAngleDeltaDegrees(s1.headingDegrees, s2.headingDegrees)
        val interpolated = (s1.headingDegrees + delta * frac + 360f).rem(360f)
        return interpolated
    }
}

object VehicleHeadingCalculator {

    /**
     * Look-ahead distance in meters per transport mode for stable tangent calculation (P0-07, P1-09).
     */
    fun getLookaheadMeters(mode: TransportMode): Double {
        return when (mode) {
            TransportMode.WALK, TransportMode.RUN -> 15.0
            TransportMode.BICYCLE -> 30.0
            TransportMode.CAR, TransportMode.BUS -> 80.0
            TransportMode.TRAIN, TransportMode.SUBWAY -> 200.0
            TransportMode.AIRPLANE -> 50_000.0
            TransportMode.FERRY -> 150.0
            else -> 50.0
        }
    }

    /**
     * Computes the shortest angular delta in degrees between two angles in range [-180, 180].
     * Properly handles antimeridian/wrap-around (e.g. 359° -> 1° returns +2°, 1° -> 359° returns -2°).
     */
    fun shortestAngleDeltaDegrees(fromDeg: Float, toDeg: Float): Float {
        val diff = (toDeg - fromDeg + 180f).rem(360f)
        val normalized = if (diff < 0f) diff + 360f else diff
        return normalized - 180f
    }

    /**
     * Smooths an angle towards a target angle with a maximum angular step.
     */
    fun smoothHeading(currentDeg: Float, targetDeg: Float, maxStepDeg: Float): Float {
        val delta = shortestAngleDeltaDegrees(currentDeg, targetDeg)
        val clampedDelta = delta.coerceIn(-maxStepDeg, maxStepDeg)
        val result = (currentDeg + clampedDelta).rem(360f)
        return if (result < 0f) result + 360f else result
    }

    /**
     * Calculates the look-ahead heading in degrees for a given point along a movement path.
     */
    fun calculateLookaheadHeading(
        path: List<GeoPoint>,
        cumulativeDistances: List<Double>,
        totalDistanceMeters: Double,
        currentDistanceMeters: Double,
        mode: TransportMode,
        previousStableHeading: Float = 0.0f
    ): Float {
        if (totalDistanceMeters <= 0.0 || path.distinct().size <= 1) {
            return previousStableHeading
        }
        val track = buildPrecomputedHeadingTrack(path, cumulativeDistances, totalDistanceMeters, mode)
        return track.evaluate(currentDistanceMeters)
    }

    /**
     * Precomputes a continuous, unwrapped, spatially-smoothed HeadingTrack along a route (P1-09).
     * Deterministic across seeks, scrubs, and restarts without depending on volatile per-frame state.
     */
    fun buildPrecomputedHeadingTrack(
        path: List<GeoPoint>,
        cumulativeDistances: List<Double>,
        totalDistanceMeters: Double,
        mode: TransportMode
    ): PrecomputedHeadingTrack {
        if (path.size < 2 || totalDistanceMeters < 1.0) {
            return PrecomputedHeadingTrack(
                samples = listOf(HeadingSample(0.0, 0.0f)),
                totalDistanceMeters = totalDistanceMeters
            )
        }

        val lookaheadMeters = getLookaheadMeters(mode)
        val sampleStep = maxOf(5.0, minOf(50.0, totalDistanceMeters / 100.0))
        val sampleCount = (totalDistanceMeters / sampleStep).toInt() + 1

        val rawHeadings = mutableListOf<Float>()
        val distances = mutableListOf<Double>()

        var lastStableBearing = 0.0f
        for (i in 0..sampleCount) {
            val d = (i * sampleStep).coerceAtMost(totalDistanceMeters)
            distances.add(d)

            val p1 = interpolatePointAtDistance(path, cumulativeDistances, d)
            val lookaheadD = (d + lookaheadMeters).coerceAtMost(totalDistanceMeters)

            val bearing = if (lookaheadD - d >= 1.5) {
                val p2 = interpolatePointAtDistance(path, cumulativeDistances, lookaheadD)
                GeodesicUtils.initialBearing(p1, p2).toFloat()
            } else {
                // Near end of path: look backward
                val lookbackD = (d - lookaheadMeters).coerceAtLeast(0.0)
                if (d - lookbackD >= 1.5) {
                    val pBack = interpolatePointAtDistance(path, cumulativeDistances, lookbackD)
                    GeodesicUtils.initialBearing(pBack, p1).toFloat()
                } else {
                    lastStableBearing
                }
            }

            lastStableBearing = bearing
            rawHeadings.add(bearing)
        }

        if (rawHeadings.isEmpty()) {
            return PrecomputedHeadingTrack(listOf(HeadingSample(0.0, 0.0f)), totalDistanceMeters)
        }

        // Unwrap angles across 0/360 boundary to allow continuous spatial filtering
        val unwrapped = DoubleArray(rawHeadings.size)
        unwrapped[0] = rawHeadings[0].toDouble()
        for (i in 1 until rawHeadings.size) {
            val delta = shortestAngleDeltaDegrees(unwrapped[i - 1].toFloat(), rawHeadings[i]).toDouble()
            unwrapped[i] = unwrapped[i - 1] + delta
        }

        // Apply 3-tap rolling Gaussian filter: 0.2, 0.6, 0.2
        val smoothedSamples = mutableListOf<HeadingSample>()
        for (i in rawHeadings.indices) {
            val hPrev = if (i > 0) unwrapped[i - 1] else unwrapped[i]
            val hCurr = unwrapped[i]
            val hNext = if (i < rawHeadings.size - 1) unwrapped[i + 1] else unwrapped[i]

            val avg = 0.2 * hPrev + 0.6 * hCurr + 0.2 * hNext
            val normalized = ((avg % 360.0) + 360.0) % 360.0
            smoothedSamples.add(HeadingSample(distances[i], normalized.toFloat()))
        }

        return PrecomputedHeadingTrack(smoothedSamples, totalDistanceMeters)
    }

    /**
     * Interpolates a GeoPoint along a polyline at an exact cumulative distance.
     */
    fun interpolatePointAtDistance(
        path: List<GeoPoint>,
        cumulativeDistances: List<Double>,
        targetDistance: Double
    ): GeoPoint {
        if (path.isEmpty()) return GeoPoint(0.0, 0.0)
        if (path.size == 1 || targetDistance <= 0.0) return path.first()
        if (targetDistance >= cumulativeDistances.last()) return path.last()

        val searchIdx = cumulativeDistances.binarySearch(targetDistance)
        val ptIdx = if (searchIdx >= 0) {
            searchIdx
        } else {
            val insert = -(searchIdx + 1) - 1
            insert.coerceIn(0, path.size - 2)
        }

        val p1 = path[ptIdx]
        val p2 = path[minOf(ptIdx + 1, path.size - 1)]
        val d1 = cumulativeDistances[ptIdx]
        val d2 = cumulativeDistances[minOf(ptIdx + 1, cumulativeDistances.size - 1)]
        val segDist = d2 - d1

        val fraction = if (segDist > 0.0) ((targetDistance - d1) / segDist).coerceIn(0.0, 1.0) else 0.0
        return GeodesicUtils.interpolate(p1, p2, fraction)
    }
}
