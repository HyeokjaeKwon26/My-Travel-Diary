package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VehicleHeadingCalculatorTest {

    @Test
    fun shortestAngleDeltaDegrees_handlesAntimeridianWrap() {
        // 359° -> 1° should be +2°, not -358°
        assertEquals(2.0f, VehicleHeadingCalculator.shortestAngleDeltaDegrees(359.0f, 1.0f), 0.001f)
        // 1° -> 359° should be -2°, not +358°
        assertEquals(-2.0f, VehicleHeadingCalculator.shortestAngleDeltaDegrees(1.0f, 359.0f), 0.001f)
        // 10° -> 20° = +10°
        assertEquals(10.0f, VehicleHeadingCalculator.shortestAngleDeltaDegrees(10.0f, 20.0f), 0.001f)
        // 20° -> 10° = -10°
        assertEquals(-10.0f, VehicleHeadingCalculator.shortestAngleDeltaDegrees(20.0f, 10.0f), 0.001f)
    }

    @Test
    fun smoothHeading_clampsMaximumStep() {
        val current = 10.0f
        val target = 50.0f
        val maxStep = 15.0f

        val stepped = VehicleHeadingCalculator.smoothHeading(current, target, maxStep)
        assertEquals(25.0f, stepped, 0.001f)
    }

    @Test
    fun straightMovements_calculateCorrectHeadings() {
        // Heading East along Equator (0,0) -> (0, 1) -> 90 degrees
        val eastPath = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
        val eastDist = listOf(0.0, GeodesicUtils.distanceMeters(eastPath[0], eastPath[1]))
        val eastHeading = VehicleHeadingCalculator.calculateLookaheadHeading(
            path = eastPath,
            cumulativeDistances = eastDist,
            totalDistanceMeters = eastDist.last(),
            currentDistanceMeters = 1000.0,
            mode = TransportMode.CAR
        )
        assertEquals(90.0f, eastHeading, 1.0f)

        // Heading North (0,0) -> (1, 0) -> 0 degrees
        val northPath = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))
        val northDist = listOf(0.0, GeodesicUtils.distanceMeters(northPath[0], northPath[1]))
        val northHeading = VehicleHeadingCalculator.calculateLookaheadHeading(
            path = northPath,
            cumulativeDistances = northDist,
            totalDistanceMeters = northDist.last(),
            currentDistanceMeters = 1000.0,
            mode = TransportMode.CAR
        )
        assertEquals(0.0f, northHeading, 1.0f)
    }

    @Test
    fun noisyStraightRoad_lookaheadFiltersJitter() {
        // Road heading East (approx 90 deg) with micro-jitter (1m north/south)
        val path = mutableListOf<GeoPoint>()
        var cumDist = 0.0
        val dists = mutableListOf(0.0)

        for (i in 0..100) {
            val lng = -71.0 + (i * 0.0001) // ~8m per step
            val lat = 42.0 + (if (i % 2 == 0) 0.00001 else -0.00001) // micro jitter ~1m
            val pt = GeoPoint(lat, lng)
            path.add(pt)
            if (i > 0) {
                cumDist += GeodesicUtils.distanceMeters(path[i - 1], pt)
                dists.add(cumDist)
            }
        }

        // Calculate heading at mid-route
        val heading = VehicleHeadingCalculator.calculateLookaheadHeading(
            path = path,
            cumulativeDistances = dists,
            totalDistanceMeters = cumDist,
            currentDistanceMeters = cumDist * 0.5,
            mode = TransportMode.CAR // 80m lookahead smooths out 1m jitter
        )

        // Overall bearing should be very close to 90 degrees East (+- 5 deg) despite 1m alternating zig-zags
        assertTrue("Heading $heading should be close to 90 deg East", abs(heading - 90.0f) < 5.0f)
    }

    @Test
    fun repeatedCoordinates_doesNotSpinOrReturnNaN() {
        val pt = GeoPoint(42.3601, -71.0589)
        val path = listOf(pt, pt, pt)
        val dists = listOf(0.0, 0.0, 0.0)

        val heading = VehicleHeadingCalculator.calculateLookaheadHeading(
            path = path,
            cumulativeDistances = dists,
            totalDistanceMeters = 0.0,
            currentDistanceMeters = 0.0,
            mode = TransportMode.CAR,
            previousStableHeading = 45.0f
        )

        assertEquals(45.0f, heading, 0.001f)
    }
}
