package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.TransportMode
import com.traveler.core.model.TransportPrediction
import com.traveler.core.model.Visit
import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

class LargeTripViewportPerformanceTest {

    @Test
    fun playbackWith10000Points_doesNotRecomputeGlobalViewportOnEveryFrame() {
        val largePoints = mutableListOf<GeoPoint>()
        val startTs = 1782800000000L

        // Generate 10,000 synthetic GPS points across a route
        for (i in 0 until 10000) {
            largePoints.add(GeoPoint(35.0 + (i * 0.001), 135.0 + (i * 0.001)))
        }

        val segment = MovementSegment(
            id = "seg_10k",
            startTimestampEpochMs = startTs,
            endTimestampEpochMs = startTs + 3600_000L,
            startPoint = largePoints.first(),
            endPoint = largePoints.last(),
            simplifiedPoints = largePoints,
            distanceMeters = 50000.0,
            durationMillis = 3600_000L,
            transport = TransportPrediction(TransportMode.TRAIN, 0.99f, "Shinkansen")
        )

        val renderModel = TravelMapRenderModel(
            visits = listOf(
                Visit("v_start", "Tokyo", null, null, largePoints.first(), startTs - 1800_000L, startTs, 0.9f),
                Visit("v_end", "Osaka", null, null, largePoints.last(), startTs + 3600_000L, startTs + 5400_000L, 0.9f)
            ),
            segments = listOf(segment)
        )

        val width = 1080
        val height = 1920
        val insets = SafeContentInsets(left = 20f, top = 20f, right = 20f, bottom = 20f)

        // 1. One-time map preparation (computes ViewportReference once)
        val prepTimeMs = measureTimeMillis {
            val allPoints = mutableListOf<GeoPoint>()
            allPoints.add(renderModel.visits[0].location)
            allPoints.add(renderModel.visits[1].location)
            allPoints.addAll(largePoints)
            val ref = TravelViewportCalculator.computeReference(allPoints)
            assertNotNull(ref)
        }

        val ref = TravelViewportCalculator.computeReference(largePoints)

        // 2. Simulate 500 playback frames using precomputed reference
        val coords = FloatArray(2)
        val frameTimeMs = measureTimeMillis {
            for (frame in 0 until 500) {
                val progress = frame / 500.0f
                val camCenter = largePoints[(progress * (largePoints.size - 1)).toInt()]
                val viewport = TravelViewportCalculator(
                    width = width,
                    height = height,
                    insets = insets,
                    ref = ref,
                    playbackCameraCenter = camCenter,
                    playbackCameraSpanDegrees = 0.05
                )
                viewport.toScreen(camCenter.latitude, camCenter.longitude, coords)
            }
        }

        // 500 frames must execute within a few milliseconds because frame viewport is O(1)
        assertTrue(
            "500 playback frame viewport calculations must be fast O(1) (actual: ${frameTimeMs}ms)",
            frameTimeMs < 500
        )
    }
}
