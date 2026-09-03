package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlobalMapRenderingEvidenceTest {

    private fun getBasemapStream(): java.io.InputStream? {
        val assetFile = File("src/main/assets/basemap_world.json")
        return if (assetFile.exists()) assetFile.inputStream() else null
    }

    @Test
    fun testNySeoulGreatCircleGeometry_ComputesValidRenderModel() {
        val nyCoord = GeoPoint(40.7128, -74.0060)
        val seoulCoord = GeoPoint(37.5665, 126.9780)

        val flightSegment = MovementSegment(
            id = "flight_ny_seoul",
            startTimestampEpochMs = 1782800000000L,
            endTimestampEpochMs = 1782850000000L,
            startPoint = nyCoord,
            endPoint = seoulCoord,
            simplifiedPoints = listOf(nyCoord, seoulCoord),
            distanceMeters = 11000000.0,
            durationMillis = 50000000L,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Flight"),
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC,
            startTimezoneId = "America/New_York",
            endTimezoneId = "Asia/Seoul"
        )

        val renderModel = TravelMapRenderModel(
            visits = listOf(
                Visit("v_ny", "New York", null, null, nyCoord, 1782790000000L, 1782800000000L, 0.95f, "America/New_York"),
                Visit("v_seoul", "Seoul", null, null, seoulCoord, 1782850000000L, 1782860000000L, 0.95f, "Asia/Seoul")
            ),
            segments = listOf(flightSegment)
        )

        assertNotNull(renderModel)
        assertEquals(2, renderModel.visits.size)
        assertEquals(1, renderModel.segments.size)
        assertEquals(GeometryProvenance.ESTIMATED_GEODESIC, renderModel.segments.first().geometryProvenance)
    }

    @Test
    fun testTokyoSanFranciscoAntimeridian_ComputesShortSpanGeometry() {
        val tokyoCoord = GeoPoint(35.6762, 139.6503)
        val sfCoord = GeoPoint(37.7749, -122.4194)

        val transpacificSegment = MovementSegment(
            id = "flight_tokyo_sf",
            startTimestampEpochMs = 1782800000000L,
            endTimestampEpochMs = 1782840000000L,
            startPoint = tokyoCoord,
            endPoint = sfCoord,
            simplifiedPoints = listOf(tokyoCoord, sfCoord),
            distanceMeters = 8280000.0,
            durationMillis = 40000000L,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Transpacific Flight"),
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC,
            startTimezoneId = "Asia/Tokyo",
            endTimezoneId = "America/Los_Angeles"
        )

        val renderModel = TravelMapRenderModel(
            visits = listOf(
                Visit("v_tokyo", "Tokyo Haneda", null, null, tokyoCoord, 1782790000000L, 1782800000000L, 0.95f, "Asia/Tokyo"),
                Visit("v_sf", "San Francisco SFO", null, null, sfCoord, 1782840000000L, 1782850000000L, 0.95f, "America/Los_Angeles")
            ),
            segments = listOf(transpacificSegment)
        )

        assertNotNull(renderModel)
        assertEquals(2, renderModel.visits.size)
        assertEquals(1, renderModel.segments.size)
    }
}
