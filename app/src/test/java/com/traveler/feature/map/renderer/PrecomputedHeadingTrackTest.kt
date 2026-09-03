package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PrecomputedHeadingTrackTest {

    @Test
    fun headingTrack_evaluatesDeterministicallyAcrossSeeks() {
        val p1 = GeoPoint(40.0, -74.0)
        val p2 = GeoPoint(40.0, -73.0) // Heading East (90 deg)
        val p3 = GeoPoint(41.0, -73.0) // Heading North (0 deg)

        val path = listOf(p1, p2, p3)
        val d1 = 0.0
        val d2 = GeodesicUtils.distanceMeters(p1, p2)
        val d3 = d2 + GeodesicUtils.distanceMeters(p2, p3)
        val cumDist = listOf(d1, d2, d3)

        val track = VehicleHeadingCalculator.buildPrecomputedHeadingTrack(
            path = path,
            cumulativeDistances = cumDist,
            totalDistanceMeters = d3,
            mode = TransportMode.CAR
        )

        // Evaluate at 25% distance (first leg, East)
        val h1 = track.evaluate(d2 * 0.5)
        assertTrue("Heading $h1 should be approx 90 deg East", abs(h1 - 90f) < 5.0f)

        // Seek forward to 85% distance (second leg, North)
        val h2 = track.evaluate(d2 + (d3 - d2) * 0.5)
        assertTrue("Heading $h2 should be approx 0 deg / 360 deg North", h2 < 10.0f || h2 > 350.0f)

        // Seek backward to 25% distance -> must produce identical result regardless of seek history
        val h3 = track.evaluate(d2 * 0.5)
        assertEquals(h1, h3, 0.001f)
    }

    @Test
    fun headingTrack_unwrapsAntimeridianAnglesCleanly() {
        // Test angle wrapping around 359 -> 1
        assertEquals(2.0f, VehicleHeadingCalculator.shortestAngleDeltaDegrees(359.0f, 1.0f), 0.001f)
        assertEquals(-2.0f, VehicleHeadingCalculator.shortestAngleDeltaDegrees(1.0f, 359.0f), 0.001f)
    }
}
