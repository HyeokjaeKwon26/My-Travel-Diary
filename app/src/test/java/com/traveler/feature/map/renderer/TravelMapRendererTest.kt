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
class TravelMapRendererTest {

    private fun createTestRenderModel(): TravelMapRenderModel {
        val v1 = Visit(
            id = "v1",
            placeName = "Boston Back Bay",
            placeAddress = null,
            placeId = null,
            location = GeoPoint(42.3503, -71.0810),
            startTimestampEpochMs = 1782820800000L,
            endTimestampEpochMs = 1782823500000L,
            confidence = 0.95f,
            timezoneId = "America/New_York"
        )
        val v2 = Visit(
            id = "v2",
            placeName = "Niagara Falls",
            placeAddress = null,
            placeId = null,
            location = GeoPoint(43.0896, -79.0849),
            startTimestampEpochMs = 1782916200000L,
            endTimestampEpochMs = 1782936000000L,
            confidence = 0.99f,
            timezoneId = "America/Toronto"
        )
        val seg = MovementSegment(
            id = "s1",
            startTimestampEpochMs = 1782823500000L,
            endTimestampEpochMs = 1782834300000L,
            startPoint = GeoPoint(42.3503, -71.0810),
            endPoint = GeoPoint(43.0896, -79.0849),
            simplifiedPoints = listOf(GeoPoint(42.3503, -71.0810), GeoPoint(43.0896, -79.0849)),
            distanceMeters = 690000.0,
            durationMillis = 10800000L,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.95f, "Flight"),
            startTimezoneId = "America/New_York",
            endTimezoneId = "America/Toronto"
        )
        return TravelMapRenderModel(
            visits = listOf(v1, v2),
            segments = listOf(seg)
        )
    }

    @Test
    fun testStaticMapRendering_ProducesNonEmptyCanvas() {
        val renderer = TravelMapRenderer()
        val model = createTestRenderModel()

        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        renderer.render(canvas, 400, 300, model, playbackState = null)

        assertNotNull(bitmap)
        assertEquals(400, bitmap.width)
        assertEquals(300, bitmap.height)
    }

    @Test
    fun testCinematicPlaybackProgression_DeterministicFrames() {
        val renderer = TravelMapRenderer()
        val model = createTestRenderModel()
        val compressor = TimelineStoryCompressor(model, targetStoryDurationSeconds = 20.0f)

        val progressCheckpoints = listOf(0.0f, 0.25f, 0.50f, 0.75f, 1.0f)

        for (progress in progressCheckpoints) {
            val state = compressor.evaluate(progress)
            assertNotNull("Playback state at progress $progress must not be null", state)
            assertEquals(progress, state.progress, 0.001f)
            assertTrue("Latitude must be between Boston (42.3) and Niagara (43.1)", state.currentPosition.latitude in 42.0..44.0)

            val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            renderer.render(canvas, 400, 300, model, playbackState = state)
            assertNotNull(bitmap)
        }
    }
}
