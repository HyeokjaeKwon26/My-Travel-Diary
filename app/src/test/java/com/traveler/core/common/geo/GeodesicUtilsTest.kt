package com.traveler.core.common.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GeodesicUtilsTest {

    @Test
    fun testHaversineDistance_NewYorkToBoston() {
        // NYC (Times Square): 40.7580, -73.9855
        // Boston (Logan Airport): 42.3656, -71.0096
        val nyc = GeoPoint(40.7580, -73.9855)
        val boston = GeoPoint(42.3656, -71.0096)

        val distanceMeters = GeodesicUtils.distanceMeters(nyc, boston)
        val distanceKm = distanceMeters / 1000.0

        // Real world great-circle distance is ~306 km
        assertTrue("Distance should be ~306 km, got: $distanceKm", distanceKm in 300.0..315.0)
    }

    @Test
    fun testSLERPInterpolation_Midpoint() {
        val start = GeoPoint(0.0, 0.0)
        val end = GeoPoint(10.0, 10.0)

        val mid = GeodesicUtils.interpolate(start, end, 0.5)

        assertTrue(mid.latitude in 4.9..5.1)
        assertTrue(mid.longitude in 4.9..5.1)
    }

    @Test
    fun testInitialBearing() {
        // Point heading directly East: bearing should be ~90 degrees
        val p1 = GeoPoint(0.0, 0.0)
        val p2 = GeoPoint(0.0, 10.0)

        val bearing = GeodesicUtils.initialBearing(p1, p2)
        assertEquals(90.0, bearing, 0.1)
    }

    @Test
    fun testDouglasPeucker_SimplifiesCollinearPoints() {
        val points = listOf(
            GeoPoint(40.7500, -73.9800),
            GeoPoint(40.7501, -73.9801),
            GeoPoint(40.7502, -73.9802),
            GeoPoint(40.7503, -73.9803),
            GeoPoint(40.7504, -73.9804)
        )

        val simplified = DouglasPeucker.simplify(points, epsilonMeters = 5.0)
        // Collinear points along straight path should be reduced to start and end
        assertEquals(2, simplified.size)
        assertEquals(40.7500, simplified.first().latitude, 0.0001)
        assertEquals(40.7504, simplified.last().latitude, 0.0001)
    }
}
