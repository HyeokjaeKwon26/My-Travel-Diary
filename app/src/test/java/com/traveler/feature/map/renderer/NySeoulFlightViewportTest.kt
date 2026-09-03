package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.WebMercator
import com.traveler.core.model.*
import org.junit.Assert.*
import org.junit.Test

class NySeoulFlightViewportTest {

    @Test
    fun nyToSeoulFlight_arcFullyContainedInsideStaticViewport() {
        val nyCoord = GeoPoint(40.7128, -74.0060)   // JFK / NYC
        val seoulCoord = GeoPoint(37.5665, 126.9780) // ICN / Seoul

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
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC
        )

        val renderModel = TravelMapRenderModel(
            visits = listOf(
                Visit("v_ny", "New York", null, null, nyCoord, 1782790000000L, 1782800000000L, 0.95f),
                Visit("v_seoul", "Seoul", null, null, seoulCoord, 1782850000000L, 1782860000000L, 0.95f)
            ),
            segments = listOf(flightSegment)
        )

        val flightArc = WebMercator.generateGreatCirclePath(nyCoord, seoulCoord, steps = 32)
        val allPoints = mutableListOf<GeoPoint>()
        allPoints.add(nyCoord)
        allPoints.add(seoulCoord)
        allPoints.addAll(flightArc)

        val width = 1080
        val height = 1920
        val insets = SafeContentInsets(left = 40f, top = 60f, right = 40f, bottom = 80f)

        val viewportRef = TravelViewportCalculator.computeReference(allPoints)
        val viewport = TravelViewportCalculator(width, height, insets, viewportRef)

        val coords = FloatArray(2)
        for ((idx, pt) in flightArc.withIndex()) {
            viewport.toScreen(pt.latitude, pt.longitude, coords)
            val px = coords[0]
            val py = coords[1]

            assertTrue(
                "Flight point $idx ($pt) X must be inside screen bounds [0..$width], was $px",
                px in -10f..(width + 10f)
            )
            assertTrue(
                "Flight point $idx ($pt) Y (high latitude northern arc) must be inside safe screen bounds [${insets.top}..${height - insets.bottom}], was $py",
                py in (insets.top - 10f)..(height - insets.bottom + 10f)
            )
        }
    }
}
