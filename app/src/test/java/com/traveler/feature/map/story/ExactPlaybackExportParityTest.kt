package com.traveler.feature.map.story

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.media.*
import com.traveler.core.model.*
import com.traveler.feature.map.renderer.TravelMapRenderModel
import com.traveler.feature.video.TravelVideoExporter
import org.junit.Assert.*
import org.junit.Test

class ExactPlaybackExportParityTest {

    private fun createPhoto(id: String, timestampEpochMs: Long, visitId: String): MediaItem {
        return MediaItem(
            id = id,
            contentUriString = "content://media/$id",
            fileName = "$id.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = timestampEpochMs,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = GeoPoint(40.7580, -73.9855),
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.95f,
            matchedVisitId = visitId,
            isRepresentative = true
        )
    }

    @Test
    fun testExactInteractiveAndExportParityAcrossStory() {
        val baseTime = 1723800000000L

        val v1 = Visit(id = "v1", placeName = "NYC", location = GeoPoint(40.7128, -74.0060), startTimestampEpochMs = baseTime, endTimestampEpochMs = baseTime + 7200_000L, confidence = 0.9f)
        val s1 = MovementSegment(
            id = "s1",
            startTimestampEpochMs = baseTime + 7200_000L,
            endTimestampEpochMs = baseTime + 14400_000L,
            startPoint = GeoPoint(40.7128, -74.0060),
            endPoint = GeoPoint(42.1292, -80.0851),
            distanceMeters = 600_000.0,
            durationMillis = 7200_000L,
            transport = TransportPrediction(TransportMode.CAR, 0.95f, "Highway")
        )
        val v2 = Visit(id = "v2", placeName = "Erie", location = GeoPoint(42.1292, -80.0851), startTimestampEpochMs = baseTime + 14400_000L, endTimestampEpochMs = baseTime + 21600_000L, confidence = 0.9f)

        val photo1 = createPhoto("p_nyc", baseTime + 3600_000L, "v1")
        val photo2 = createPhoto("p_erie", baseTime + 18000_000L, "v2")

        val renderModel = TravelMapRenderModel(
            visits = listOf(v1, v2),
            segments = listOf(s1),
            photos = listOf(photo1, photo2)
        )

        val trip = Trip(
            id = "trip_parity",
            title = "East Adventure",
            startDateIso = "2026-08-16",
            endDateIso = "2026-08-21",
            totalDistanceMeters = 600_000.0,
            days = emptyList(),
            totalMediaCount = 2
        )

        // 1. Build interactive timeline (no title/end card)
        val interactiveTimeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)

        // 2. Build export timeline (with title & end cards)
        val exportTimeline = TravelVideoExporter.buildExportTimeline(trip, renderModel, StoryDurationProfile.STANDARD)

        val titleCardDuration = exportTimeline.titleCard?.durationSeconds ?: 0.0f
        val journeyDuration = interactiveTimeline.totalStoryDurationSeconds

        // Verify Title Card Phase during export at t = 0.5s
        val titleCardState = exportTimeline.evaluateAtStoryTime(0.5f)
        assertTrue("Title card should be active during initial export seconds", titleCardState.isTitleCardActive)
        assertFalse("End card should not be active during initial export seconds", titleCardState.isEndCardActive)

        // Sample points across the journey (start, 10%, 25%, 50%, 75%, photo holds, arrival)
        val testFractions = listOf(0.0f, 0.05f, 0.10f, 0.25f, 0.35f, 0.50f, 0.65f, 0.75f, 0.85f, 0.95f, 0.98f)

        for (frac in testFractions) {
            val interactiveTime = frac * journeyDuration
            val exportTime = titleCardDuration + interactiveTime

            val iState = interactiveTimeline.evaluateAtStoryTime(interactiveTime)
            val eState = exportTimeline.evaluateAtStoryTime(exportTime)

            // P0-08 ~ P0-11: Assert Exact Parity during journey episodes
            assertEquals("Traveler lat at frac $frac", iState.currentPosition.latitude, eState.currentPosition.latitude, 0.0001)
            assertEquals("Traveler lng at frac $frac", iState.currentPosition.longitude, eState.currentPosition.longitude, 0.0001)
            assertEquals("Transport mode at frac $frac", iState.currentTransportMode, eState.currentTransportMode)
            assertEquals("Heading at frac $frac", iState.currentHeadingDegrees, eState.currentHeadingDegrees, 0.1f)
            assertEquals("Active visit at frac $frac", iState.currentVisit?.id, eState.currentVisit?.id)
            assertEquals("Active photo at frac $frac", iState.activePhoto?.id, eState.activePhoto?.id)
            assertEquals("Camera center lat at frac $frac", iState.cameraCenter.latitude, eState.cameraCenter.latitude, 0.0001)
            assertEquals("Camera center lng at frac $frac", iState.cameraCenter.longitude, eState.cameraCenter.longitude, 0.0001)
            assertEquals("Camera span lat at frac $frac", iState.cameraSpanLat, eState.cameraSpanLat, 0.0001)
            assertEquals("Camera span lng at frac $frac", iState.cameraSpanLng, eState.cameraSpanLng, 0.0001)
        }

        // Verify End Card Phase during export at totalDuration - 0.5s
        val endCardState = exportTimeline.evaluateAtStoryTime(exportTimeline.totalStoryDurationSeconds - 0.5f)
        assertTrue("End card should be active during final export seconds", endCardState.isEndCardActive)
        assertFalse("Title card should not be active during final export seconds", endCardState.isTitleCardActive)
    }
}
