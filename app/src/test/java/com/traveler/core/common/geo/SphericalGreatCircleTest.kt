package com.traveler.core.common.geo

import org.junit.Assert.*
import org.junit.Test

class SphericalGreatCircleTest {

    @Test
    fun newYorkToSeoul_geodesicArcRisesToHighNorthernLatitude() {
        val ny = GeoPoint(40.7128, -74.0060)
        val seoul = GeoPoint(37.5665, 126.9780)

        val path = WebMercator.generateGreatCirclePath(ny, seoul, steps = 50)
        assertEquals(51, path.size)

        assertEquals(ny.latitude, path.first().latitude, 0.001)
        assertEquals(ny.longitude, path.first().longitude, 0.001)

        assertEquals(seoul.latitude, path.last().latitude, 0.001)
        assertEquals(seoul.longitude, path.last().longitude, 0.001)

        // On a true spherical great-circle, flight from NY to Seoul goes over Canada/Alaska and reaches > 60°N
        val maxLat = path.maxOf { it.latitude }
        assertTrue("Geodesic arc from NY to Seoul must rise into high northern latitudes (> 60°N), actual: $maxLat", maxLat > 60.0)

        // Ensure all points have valid normalized coordinate ranges
        for (pt in path) {
            assertTrue(pt.latitude in -90.0..90.0)
            assertTrue(pt.longitude in -180.0..180.0)
        }
    }

    @Test
    fun tokyoToSanFrancisco_crossesAntimeridianCorrectly() {
        val tokyo = GeoPoint(35.6762, 139.6503)
        val sf = GeoPoint(37.7749, -122.4194)

        val path = WebMercator.generateGreatCirclePath(tokyo, sf, steps = 30)
        assertEquals(31, path.size)

        assertEquals(tokyo.latitude, path.first().latitude, 0.001)
        assertEquals(tokyo.longitude, path.first().longitude, 0.001)

        assertEquals(sf.latitude, path.last().latitude, 0.001)
        assertEquals(sf.longitude, path.last().longitude, 0.001)

        val midPoint = path[15]
        assertTrue("Midpoint latitude should rise above 40°N across North Pacific", midPoint.latitude > 40.0)
    }
}
