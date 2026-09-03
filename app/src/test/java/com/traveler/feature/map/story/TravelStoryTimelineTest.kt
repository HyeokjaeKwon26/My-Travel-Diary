package com.traveler.feature.map.story

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.media.StoryDurationProfile
import com.traveler.core.model.*
import com.traveler.feature.map.renderer.TravelMapRenderModel
import org.junit.Assert.*
import org.junit.Test

class TravelStoryTimelineTest {

    private fun createSyntheticTrip(): TravelMapRenderModel {
        val visitErie = Visit(
            id = "v_erie",
            location = GeoPoint(42.1292, -80.0851),
            startTimestampEpochMs = 1723810000000L,
            endTimestampEpochMs = 1723813600000L,
            confidence = 0.9f
        )
        val driveToNiagara = MovementSegment(
            id = "s_niagara",
            startTimestampEpochMs = 1723813600000L,
            endTimestampEpochMs = 1723820000000L,
            startPoint = GeoPoint(42.1292, -80.0851),
            endPoint = GeoPoint(43.0962, -79.0377),
            distanceMeters = 150_000.0,
            durationMillis = 6400000L,
            transport = TransportPrediction(TransportMode.CAR, 0.95f, "Highway")
        )
        val visitNiagara = Visit(
            id = "v_niagara",
            location = GeoPoint(43.0962, -79.0377),
            startTimestampEpochMs = 1723820000000L,
            endTimestampEpochMs = 1723830000000L,
            confidence = 0.9f
        )

        val eriePhoto = MediaItem(
            id = "p_erie",
            contentUriString = "content://media/1",
            fileName = "erie_view.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = 1723811000000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = GeoPoint(42.1292, -80.0851),
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.95f,
            matchedVisitId = "v_erie"
        )
        val niagaraPhoto = MediaItem(
            id = "p_niagara",
            contentUriString = "content://media/2",
            fileName = "niagara_falls.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = 1723825000000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = GeoPoint(43.0962, -79.0377),
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.95f,
            matchedVisitId = "v_niagara"
        )

        return TravelMapRenderModel(
            visits = listOf(visitErie, visitNiagara),
            segments = listOf(driveToNiagara),
            photos = listOf(eriePhoto, niagaraPhoto)
        )
    }

    // 1. Strict Monotonicity: Story time is non-decreasing across progress (P0-05 & P1)
    @Test
    fun testStoryTimelineStrictMonotonicity() {
        val renderModel = createSyntheticTrip()
        val timeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)

        var prevTimeMs = Long.MIN_VALUE
        var negativeDeltas = 0

        val steps = 100
        for (i in 0..steps) {
            val progress = i.toFloat() / steps.toFloat()
            val state = timeline.evaluate(progress)
            if (state.storyTimeMs < prevTimeMs) {
                negativeDeltas++
            }
            prevTimeMs = state.storyTimeMs
        }

        assertEquals("Negative story time delta count must be exactly 0", 0, negativeDeltas)
    }

    // 2. Photo Moments: Erie photo never active during Niagara (P0-08)
    @Test
    fun testEriePhotoNeverActiveDuringNiagara() {
        val renderModel = createSyntheticTrip()
        val timeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)

        // Evaluate near end of story (Niagara context)
        val stateNiagara = timeline.evaluate(0.90f)
        assertNotEquals("Erie photo must not appear during Niagara context", "p_erie", stateNiagara.activePhoto?.id)
        assertEquals("p_niagara", stateNiagara.activePhoto?.id)
    }

    // 3. Profiles: Standard baseline vs Short content compression (P1)
    @Test
    fun testProfilesContentCompression() {
        val renderModel = createSyntheticTrip()
        val standard = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)
        val short = TravelStoryTimeline.build(renderModel, StoryDurationProfile.SHORT)

        assertTrue(
            "Short duration (${short.totalStoryDurationSeconds}s) must be shorter than Standard (${standard.totalStoryDurationSeconds}s)",
            short.totalStoryDurationSeconds < standard.totalStoryDurationSeconds
        )
    }
}
