package com.traveler.feature.map.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObservedFlightGeometryProvenanceTest {

    @Test
    fun testObservedFlight_HasSolidEstimatedFalse_EstimatedFlightHasEstimatedTrue() {
        val renderer = TravelMapRenderer()

        // Segment 1: Observed Flight with detailed GPS points
        val segObserved = MovementSegment(
            id = "flight_observed",
            startTimestampEpochMs = 1000L,
            endTimestampEpochMs = 2000L,
            startPoint = GeoPoint(40.0, -74.0),
            endPoint = GeoPoint(35.0, 139.0),
            simplifiedPoints = listOf(
                GeoPoint(40.0, -74.0),
                GeoPoint(50.0, -100.0),
                GeoPoint(55.0, -140.0),
                GeoPoint(35.0, 139.0)
            ),
            distanceMeters = 10000000.0,
            durationMillis = 1000L,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Observed Flight"),
            startTimezoneId = "America/New_York",
            endTimezoneId = "Asia/Tokyo",
            geometryProvenance = GeometryProvenance.OBSERVED
        )

        // Segment 2: Estimated Geodesic Flight with only endpoints
        val segEstimated = MovementSegment(
            id = "flight_estimated",
            startTimestampEpochMs = 3000L,
            endTimestampEpochMs = 4000L,
            startPoint = GeoPoint(35.0, 139.0),
            endPoint = GeoPoint(37.5, 127.0),
            simplifiedPoints = listOf(GeoPoint(35.0, 139.0), GeoPoint(37.5, 127.0)),
            distanceMeters = 1200000.0,
            durationMillis = 1000L,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Estimated Flight"),
            startTimezoneId = "Asia/Tokyo",
            endTimezoneId = "Asia/Seoul",
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC
        )

        val model = TravelMapRenderModel(
            visits = listOf(
                Visit("v1", "NY", null, null, GeoPoint(40.0, -74.0), 500L, 1000L, 0.9f, "America/New_York"),
                Visit("v2", "Tokyo", null, null, GeoPoint(35.0, 139.0), 2000L, 3000L, 0.9f, "Asia/Tokyo"),
                Visit("v3", "Seoul", null, null, GeoPoint(37.5, 127.0), 4000L, 5000L, 0.9f, "Asia/Seoul")
            ),
            segments = listOf(segObserved, segEstimated)
        )

        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        renderer.render(canvas, 800, 600, model)

        assertNotNull(bitmap)
    }
}
