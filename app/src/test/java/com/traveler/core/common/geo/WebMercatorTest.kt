package com.traveler.core.common.geo

import org.junit.Assert.*
import org.junit.Test

class WebMercatorTest {

    @Test
    fun testProjectionAndUnprojection_EquatorAndMeridian() {
        val origin = GeoPoint(0.0, 0.0)
        val world = WebMercator.project(origin)
        assertEquals(0.5, world.x, 0.0001)
        assertEquals(0.5, world.y, 0.0001)

        val unprojected = WebMercator.unproject(world)
        assertEquals(0.0, unprojected.latitude, 0.0001)
        assertEquals(0.0, unprojected.longitude, 0.0001)
    }

    @Test
    fun testProjectionAndUnprojection_GlobalCities() {
        val cities = listOf(
            GeoPoint(37.5665, 126.9780),  // Seoul
            GeoPoint(40.7128, -74.0060),  // New York
            GeoPoint(51.5074, -0.1278),   // London
            GeoPoint(-33.8688, 151.2093), // Sydney
            GeoPoint(35.6762, 139.6503)   // Tokyo
        )

        for (city in cities) {
            val projected = WebMercator.project(city)
            assertTrue("World X must be in [0, 1]", projected.x in 0.0..1.0)
            assertTrue("World Y must be in [0, 1]", projected.y in 0.0..1.0)

            val unprojected = WebMercator.unproject(projected)
            assertEquals(city.latitude, unprojected.latitude, 0.0001)
            assertEquals(city.longitude, unprojected.longitude, 0.0001)
        }
    }

    @Test
    fun testAntimeridianUnwrap_179EastTo179West() {
        // 179E (+179.0) to 179W (-179.0) should be 2 degrees across Pacific (unwrapped to 181.0)
        val unwrapped = WebMercator.unwrapLongitude(179.0, -179.0)
        assertEquals(181.0, unwrapped, 0.0001)
    }

    @Test
    fun testAntimeridianUnwrap_TokyoToSanFrancisco() {
        val tokyoLng = 139.6503
        val sfLng = -122.4194

        // Shortest Pacific route should unwrap SF to ~ 237.58 rather than -122.42
        val unwrappedSf = WebMercator.unwrapLongitude(tokyoLng, sfLng)
        assertTrue("Unwrapped SF longitude should be positive eastern offset across Pacific", unwrappedSf > 180.0)
        assertEquals(tokyoLng + (360.0 - 139.6503 - 122.4194), unwrappedSf, 0.001)
    }

    @Test
    fun testAntimeridianUnwrap_AucklandToHonolulu() {
        val aucklandLng = 174.7633
        val honoluluLng = -157.8583

        val unwrappedHonolulu = WebMercator.unwrapLongitude(aucklandLng, honoluluLng)
        assertTrue("Honolulu should unwrap to > 180.0 across the Pacific", unwrappedHonolulu > 180.0)
        assertEquals(aucklandLng + (360.0 - 174.7633 - 157.8583), unwrappedHonolulu, 0.001)
    }

    @Test
    fun testGreatCirclePathGeneration_AntimeridianCrossing() {
        val tokyo = GeoPoint(35.6762, 139.6503)
        val sf = GeoPoint(37.7749, -122.4194)

        val path = WebMercator.generateGreatCirclePath(tokyo, sf, steps = 10)
        assertEquals(11, path.size)
        assertEquals(tokyo.latitude, path.first().latitude, 0.001)
        assertEquals(sf.latitude, path.last().latitude, 0.001)

        // Verify valid GeoPoint coordinates across antimeridian
        for (pt in path) {
            assertTrue("Latitude in valid range", pt.latitude in -85.0..85.0)
            assertTrue("Longitude in valid range", pt.longitude in -180.0..180.0)
        }
    }
}
