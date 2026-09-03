package com.traveler.feature.map.story

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.media.*
import com.traveler.core.model.*
import com.traveler.feature.map.renderer.TravelMapRenderModel
import org.junit.Assert.*
import org.junit.Test

class Pass21AlignmentRegressionTest {

    private fun createTestPhoto(
        id: String,
        timestampEpochMs: Long,
        matchedVisitId: String? = null,
        matchedSegmentId: String? = null,
        location: GeoPoint = GeoPoint(40.7128, -74.0060),
        isRepresentative: Boolean = false
    ): MediaItem {
        return MediaItem(
            id = id,
            contentUriString = "content://media/$id",
            fileName = "photo_$id.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = timestampEpochMs,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = location,
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.95f,
            matchedVisitId = matchedVisitId,
            matchedSegmentId = matchedSegmentId,
            isRepresentative = isRepresentative
        )
    }

    // 1. Same Story Timeline Proof: Interactive and Video Export timelines evaluate identically on core journey
    @Test
    fun testSameStoryTimelineProof() {
        val visit1 = Visit(
            id = "v1",
            location = GeoPoint(40.7128, -74.0060),
            startTimestampEpochMs = 1723800000000L,
            endTimestampEpochMs = 1723807200000L,
            confidence = 0.9f
        )
        val seg1 = MovementSegment(
            id = "s1",
            startTimestampEpochMs = 1723807200000L,
            endTimestampEpochMs = 1723810800000L,
            startPoint = GeoPoint(40.7128, -74.0060),
            endPoint = GeoPoint(40.7580, -73.9855),
            distanceMeters = 5000.0,
            durationMillis = 3600000L,
            transport = TransportPrediction(TransportMode.SUBWAY, 0.9f, "Subway")
        )
        val visit2 = Visit(
            id = "v2",
            location = GeoPoint(40.7580, -73.9855),
            startTimestampEpochMs = 1723810800000L,
            endTimestampEpochMs = 1723818000000L,
            confidence = 0.9f
        )

        val photo1 = createTestPhoto("p1", 1723803600000L, matchedVisitId = "v1")
        val photo2 = createTestPhoto("p2", 1723814400000L, matchedVisitId = "v2")

        val renderModel = TravelMapRenderModel(
            visits = listOf(visit1, visit2),
            segments = listOf(seg1),
            photos = listOf(photo1, photo2)
        )

        // Interactive timeline (no title/end card)
        val interactiveTimeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)

        // Export timeline (with title/end card)
        val titleCard = StoryTitleCard(title = "NYC Trip", dateRangeStr = "2026-08-16")
        val endCard = StoryEndCard(title = "NYC Trip", totalDaysStr = "1 Day", totalDistanceStr = "10 km", memoriesCountStr = "2 Memories")
        val exportTimeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD, titleCard, endCard)

        assertEquals(interactiveTimeline.episodes.size, exportTimeline.episodes.size)
        assertEquals(interactiveTimeline.photoMoments.size, exportTimeline.photoMoments.size)

        // Verify episodes have identical structure
        for (i in interactiveTimeline.episodes.indices) {
            val iEp = interactiveTimeline.episodes[i]
            val eEp = exportTimeline.episodes[i]
            assertEquals(iEp.startTimestampEpochMs, eEp.startTimestampEpochMs)
            assertEquals(iEp.endTimestampEpochMs, eEp.endTimestampEpochMs)
            assertEquals(iEp.durationStorySeconds, eEp.durationStorySeconds, 0.001f)
        }
    }

    // 2. Multiple representative moments in one long visit appear as distinct short windows (P0-06 ~ P0-08)
    @Test
    fun testMultipleRepresentativeMomentsInLongVisit() {
        val longVisit = Visit(
            id = "v_long",
            location = GeoPoint(40.7128, -74.0060),
            startTimestampEpochMs = 1723800000000L,       // 08:00
            endTimestampEpochMs = 1723836000000L,         // 18:00 (10 hours)
            confidence = 0.9f
        )

        // 3 distinct clusters: 09:00 breakfast, 13:00 lunch, 17:00 evening
        val p1 = createTestPhoto("p_morning", 1723803600000L, matchedVisitId = "v_long")
        val p2 = createTestPhoto("p_noon", 1723818000000L, matchedVisitId = "v_long")
        val p3 = createTestPhoto("p_evening", 1723832400000L, matchedVisitId = "v_long")

        val renderModel = TravelMapRenderModel(
            visits = listOf(longVisit),
            segments = emptyList(),
            photos = listOf(p1, p2, p3)
        )

        val timeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)

        assertEquals(3, timeline.photoMoments.size)
        // Verify moments are scheduled in strictly chronological story time order with no overlaps
        for (i in 0 until timeline.photoMoments.size - 1) {
            val m1 = timeline.photoMoments[i]
            val m2 = timeline.photoMoments[i + 1]
            assertTrue(m1.scheduledStoryTimeSeconds < m2.scheduledStoryTimeSeconds)
            assertTrue(m1.displayEndStorySeconds <= m2.displayStartStorySeconds + 0.05f)
        }

        // Test evaluations at distinct story times
        val state1 = timeline.evaluateAtStoryTime(timeline.photoMoments[0].scheduledStoryTimeSeconds)
        assertEquals("p_morning", state1.activePhoto?.id)

        val state2 = timeline.evaluateAtStoryTime(timeline.photoMoments[1].scheduledStoryTimeSeconds)
        assertEquals("p_noon", state2.activePhoto?.id)

        val state3 = timeline.evaluateAtStoryTime(timeline.photoMoments[2].scheduledStoryTimeSeconds)
        assertEquals("p_evening", state3.activePhoto?.id)
    }

    // 3. Route marker coordinate is NEVER mutated by photo GPS (P0-10)
    @Test
    fun testRouteMarkerNeverMutatedByPhotoGps() {
        val visitAnchor = GeoPoint(40.7128, -74.0060)
        val visit = Visit(
            id = "v1",
            location = visitAnchor,
            startTimestampEpochMs = 1723800000000L,
            endTimestampEpochMs = 1723807200000L,
            confidence = 0.9f
        )
        // Photo has slightly different GPS
        val photoFar = createTestPhoto("p_far", 1723803600000L, matchedVisitId = "v1", location = GeoPoint(40.8000, -73.9000))

        val renderModel = TravelMapRenderModel(
            visits = listOf(visit),
            segments = emptyList(),
            photos = listOf(photoFar)
        )

        val timeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)
        val state = timeline.evaluateAtStoryTime(timeline.photoMoments[0].scheduledStoryTimeSeconds)

        assertEquals("p_far", state.activePhoto?.id)
        // Marker position MUST remain visit anchor
        assertEquals(visitAnchor.latitude, state.currentPosition.latitude, 0.0001)
        assertEquals(visitAnchor.longitude, state.currentPosition.longitude, 0.0001)
    }

    // 4. Large Photo Visit (190 raw photos) bounded during playback (P0-03, P0-14)
    @Test
    fun testLargePhotoVisitPlaybackBudgetBounded() {
        val visit = Visit(
            id = "v_massive",
            location = GeoPoint(40.7128, -74.0060),
            startTimestampEpochMs = 1723800000000L,
            endTimestampEpochMs = 1723836000000L,
            confidence = 0.9f
        )

        val all190Photos = (1..190).map { i ->
            createTestPhoto("photo_$i", 1723800000000L + (i * 180_000L), matchedVisitId = "v_massive")
        }

        val renderModel = TravelMapRenderModel(
            visits = listOf(visit),
            segments = emptyList(),
            photos = all190Photos
        )

        // Diary retains all 190 photos
        assertEquals(190, renderModel.photos.size)

        val timeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)

        // Playback budget must be bounded to <= 3 representative moments for a single visit
        assertTrue(timeline.photoMoments.size <= 3)

        // Sample 200 points across playback and count distinct active photos observed
        val observedPhotos = mutableSetOf<String>()
        for (step in 0..200) {
            val progress = step.toFloat() / 200.0f
            val state = timeline.evaluate(progress)
            state.activePhoto?.let { observedPhotos.add(it.id) }
        }

        assertTrue("Observed photos: ${observedPhotos.size}", observedPhotos.size <= 3)
    }
}
