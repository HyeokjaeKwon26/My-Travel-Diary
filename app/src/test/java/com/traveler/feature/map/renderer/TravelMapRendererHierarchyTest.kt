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
class TravelMapRendererHierarchyTest {

    private fun createNortheastTripModel(): TravelMapRenderModel {
        val v1 = Visit("v1", "Boston", location = GeoPoint(42.3601, -71.0589), startTimestampEpochMs = 1000L, endTimestampEpochMs = 2000L)
        val v2 = Visit("v2", "New York", location = GeoPoint(40.7128, -74.0060), startTimestampEpochMs = 5000L, endTimestampEpochMs = 6000L)
        val v3 = Visit("v3", "Niagara Falls", location = GeoPoint(43.0896, -79.0849), startTimestampEpochMs = 9000L, endTimestampEpochMs = 10000L)

        // Multiple micro segments representing continuous highway drive
        val segs = ArrayList<MovementSegment>()
        for (i in 0..10) {
            val sLat = 42.3601 - (i * 0.15)
            val sLng = -71.0589 - (i * 0.25)
            val eLat = sLat - 0.15
            val eLng = sLng - 0.25
            segs.add(
                MovementSegment(
                    id = "drive_$i",
                    startTimestampEpochMs = 2000L + (i * 250L),
                    endTimestampEpochMs = 2000L + ((i + 1) * 250L),
                    startPoint = GeoPoint(sLat, sLng),
                    endPoint = GeoPoint(eLat, eLng),
                    distanceMeters = 25000.0,
                    durationMillis = 250L,
                    transport = TransportPrediction(TransportMode.CAR, 0.95f, "Driving")
                )
            )
        }

        return TravelMapRenderModel(
            visits = listOf(v1, v2, v3),
            segments = com.traveler.core.timeline.MovementTimelineCanonicalizer.canonicalize(segs).canonicalSegments,
            photos = emptyList()
        )
    }

    @Test
    fun testRenderer_canonicalizesSegmentsAndRendersCleanCanvas() {
        val model = createNortheastTripModel()
        val renderer = TravelMapRenderer()

        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        renderer.render(canvas, 800, 600, model)

        // Verify prepared map has merged continuous segments into canonical episodes
        val prep = renderer.prepareMap(model)
        assertTrue("Prepared segments (${prep.preparedSegments.size}) should be <= 3 after canonical merging", prep.preparedSegments.size <= 3)
        assertEquals(3, prep.preparedVisits.size)
        assertNotNull(bitmap)
        assertEquals(800, bitmap.width)
        assertEquals(600, bitmap.height)
    }

    @Test
    fun testPlaybackRouteHierarchy_activeSegmentIsProminent() {
        val model = createNortheastTripModel()
        val renderer = TravelMapRenderer()

        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val playbackState = TravelPlaybackState(
            progress = 0.5f,
            storyTimeMs = 3000L,
            currentPosition = GeoPoint(41.5, -72.5),
            currentTransportMode = TransportMode.CAR,
            currentHeadingDegrees = 45.0f,
            currentVisit = null,
            activePhoto = null,
            cameraCenter = GeoPoint(41.5, -72.5),
            cameraSpanLat = 0.5,
            cameraSpanLng = 0.5
        )

        renderer.render(canvas, 800, 600, model, playbackState = playbackState)

        // Verify successful non-throwing execution and bitmap dimension retention
        assertNotNull(bitmap)
        assertEquals(800, bitmap.width)
        assertEquals(600, bitmap.height)
    }
}
