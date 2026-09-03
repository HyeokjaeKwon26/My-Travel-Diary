package com.traveler.feature.map.story

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.media.StoryDurationProfile
import com.traveler.core.model.*
import com.traveler.feature.map.renderer.TravelMapRenderModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying:
 * 1. Gap-Bridging ("timeline에 비어있는 곳은 부드럽게 그라데이션 하듯이 이동"):
 *    Guarantees 100% spatial continuity between all episodes with zero teleports.
 * 2. Elevation / Altitude ("고도추가"):
 *    Verifies live altitude tracking, flight climb/cruise curves, and terrain elevation.
 */
class GapBridgingAndAltitudeTest {

    private fun createTripWithGaps(): TravelMapRenderModel {
        // Visit 1: New York
        val visitNY = Visit(
            id = "v_ny",
            location = GeoPoint(40.7128, -74.0060, altitudeMeters = 10.0),
            startTimestampEpochMs = 1787100000000L,
            endTimestampEpochMs = 1787103600000L,
            confidence = 0.9f
        )
        // GAP: No movement between NY and Philadelphia (~130 km gap)

        // Visit 2: Philadelphia
        val visitPhilly = Visit(
            id = "v_philly",
            location = GeoPoint(39.9526, -75.1652, altitudeMeters = 12.0),
            startTimestampEpochMs = 1787110000000L,
            endTimestampEpochMs = 1787115000000L,
            confidence = 0.9f
        )

        // Movement: Philly to DC airport
        val driveToDC = MovementSegment(
            id = "s_dc",
            startTimestampEpochMs = 1787115000000L,
            endTimestampEpochMs = 1787125000000L,
            startPoint = GeoPoint(39.9526, -75.1652, altitudeMeters = 12.0),
            endPoint = GeoPoint(38.8512, -77.0402, altitudeMeters = 5.0), // DCA airport
            distanceMeters = 200_000.0,
            durationMillis = 10000000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "TestDrive")
        )

        // GAP: Flight from DCA to Chicago O'Hare (~950 km flight gap without segment)

        // Visit 3: Chicago
        val visitChicago = Visit(
            id = "v_chicago",
            location = GeoPoint(41.8781, -87.6298, altitudeMeters = 180.0),
            startTimestampEpochMs = 1787140000000L,
            endTimestampEpochMs = 1787150000000L,
            confidence = 0.9f
        )

        return TravelMapRenderModel(
            visits = listOf(visitNY, visitPhilly, visitChicago),
            segments = listOf(driveToDC),
            photos = emptyList()
        )
    }

    @Test
    fun testGapBridgingEliminatesAllSpatialJumps() {
        val trip = createTripWithGaps()
        val timeline = TravelStoryTimeline.build(trip, StoryDurationProfile.STANDARD)

        assertTrue("Timeline must have generated episodes", timeline.episodes.isNotEmpty())

        // 1. Verify that every episode's end matches the next episode's start (ZERO SPATIAL GAP)
        for (i in 0 until timeline.episodes.size - 1) {
            val curr = timeline.episodes[i]
            val next = timeline.episodes[i + 1]

            val currEnd = when (curr) {
                is StoryEpisode.VisitEpisode -> curr.visit.location
                is StoryEpisode.MovementEpisode -> curr.pathPoints.last()
            }
            val nextStart = when (next) {
                is StoryEpisode.VisitEpisode -> next.visit.location
                is StoryEpisode.MovementEpisode -> next.pathPoints.first()
            }

            val gap = GeodesicUtils.distanceMeters(currEnd, nextStart)
            assertEquals(
                "Distance between episode $i (${curr.stableId}) and episode ${i + 1} (${next.stableId}) must be <= 1 meter",
                0.0,
                gap,
                1.0
            )
        }

        // 2. Continuous playback diagnostic verification
        val diagnostic = PlaybackContinuityDiagnostic()
        val steps = 500
        for (step in 0..steps) {
            val p = step.toFloat() / steps.toFloat()
            val state = timeline.evaluate(p)
            diagnostic.recordFrame(state, isUserScrubbing = false)
        }

        assertEquals("PlaybackProgress backward count must be 0", 0, diagnostic.playbackProgressBackwardCount)
        assertEquals("EpisodeIndex backward count must be 0", 0, diagnostic.episodeIndexBackwardCount)
        assertEquals("Completed episode reactivation count must be 0", 0, diagnostic.completedEpisodeReactivationCount)
        assertEquals("Spatial jump count must be 0 across entire trip", 0, diagnostic.spatialJumpCount)
    }

    @Test
    fun testAltitudeTrackingAcrossTripAndFlights() {
        val trip = createTripWithGaps()
        val timeline = TravelStoryTimeline.build(trip, StoryDurationProfile.STANDARD)

        val steps = 300
        var maxAltitude = 0.0
        var hasValidAltitudeAtAllSteps = true

        for (step in 0..steps) {
            val p = step.toFloat() / steps.toFloat()
            val state = timeline.evaluate(p)
            val alt = state.currentAltitudeMeters

            if (alt == null || alt <= 0.0) {
                hasValidAltitudeAtAllSteps = false
            } else {
                if (alt > maxAltitude) maxAltitude = alt
            }
        }

        assertTrue("Altitude must be present and positive at all playback steps", hasValidAltitudeAtAllSteps)
        assertTrue(
            "Flight / high altitude bridge must reach cruising altitude >= 5,000 meters (got $maxAltitude m)",
            maxAltitude >= 5_000.0
        )
    }

    @Test
    fun testRealRoadConnectingSegmentsPreferredOverSyntheticBridges() {
        val visitA = Visit(
            id = "v_vegas",
            location = GeoPoint(36.1699, -115.1398),
            startTimestampEpochMs = 1787100000000L,
            endTimestampEpochMs = 1787103600000L,
            confidence = 0.9f
        )
        val visitB = Visit(
            id = "v_north",
            location = GeoPoint(36.5000, -115.2000),
            startTimestampEpochMs = 1787110000000L,
            endTimestampEpochMs = 1787120000000L,
            confidence = 0.9f
        )

        // Real curved road segment between visitA and visitB
        val realRoad = MovementSegment(
            id = "s_curved_road",
            startTimestampEpochMs = 1787103600000L,
            endTimestampEpochMs = 1787110000000L,
            startPoint = GeoPoint(36.1699, -115.1398),
            endPoint = GeoPoint(36.5000, -115.2000),
            simplifiedPoints = listOf(
                GeoPoint(36.1699, -115.1398),
                GeoPoint(36.2500, -115.1500),
                GeoPoint(36.3500, -115.1800),
                GeoPoint(36.5000, -115.2000)
            ),
            distanceMeters = 40_000.0,
            durationMillis = 6400000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "Highway")
        )

        // Add 12 dummy segments elsewhere so canonicalSegments.size > 10
        val dummySegments = (1..12).map { idx ->
            MovementSegment(
                id = "s_dummy_$idx",
                startTimestampEpochMs = 1787200000000L + idx * 100000L,
                endTimestampEpochMs = 1787200000000L + idx * 100000L + 50000L,
                startPoint = GeoPoint(40.0 + idx * 0.01, -100.0),
                endPoint = GeoPoint(40.0 + idx * 0.02, -100.0),
                distanceMeters = 100_000.0, // higher distance to crowd out keySegmentIds
                durationMillis = 50000L,
                transport = TransportPrediction(TransportMode.CAR, 0.9f, "Dummy")
            )
        }

        val allSegments = listOf(realRoad) + dummySegments
        val trip = TravelMapRenderModel(
            visits = listOf(visitA, visitB),
            segments = allSegments,
            photos = emptyList()
        )

        val timeline = TravelStoryTimeline.build(trip, StoryDurationProfile.STANDARD)

        // Find movement episode between visitA and visitB
        val movementEp = timeline.episodes.filterIsInstance<StoryEpisode.MovementEpisode>()
            .find { it.segment.id == "s_curved_road" }

        assertNotNull("Real road segment s_curved_road MUST be preserved in the timeline!", movementEp)
        assertTrue(
            "Episode must use real curved road points instead of a synthetic 2-point chord",
            (movementEp?.pathPoints?.size ?: 0) >= 4
        )

        // Verify NO synthetic straight bridge was created between visitA and visitB
        val bridgeEp = timeline.episodes.filterIsInstance<StoryEpisode.MovementEpisode>()
            .find { it.segment.id.startsWith("bridge_") && it.segment.id.contains("v_vegas") }
        assertNull("No synthetic straight bridge should be created when real road segment exists", bridgeEp)
    }
}

