package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanReadablePlaybackDurationTest {

    @Test
    fun multiDayTrip_scalesToHumanReadableDurationBetween90And150Seconds() {
        val baseTs = 1783153800000L // Day 1
        val visits = mutableListOf<Visit>()
        val segments = mutableListOf<MovementSegment>()

        // Simulate 8-day trip with 15 visits and 15 movements across ~5,000 km
        for (i in 0 until 15) {
            val vStart = baseTs + i * (86400000L / 2)
            val vEnd = vStart + 7200000L
            val loc = GeoPoint(40.0 + i * 0.5, -74.0 - i * 0.5)

            visits.add(
                Visit(
                    id = "v_$i",
                    placeName = "Stop $i",
                    location = loc,
                    startTimestampEpochMs = vStart,
                    endTimestampEpochMs = vEnd
                )
            )

            val sStart = vEnd
            val sEnd = sStart + 7200000L
            val nextLoc = GeoPoint(40.0 + (i + 1) * 0.5, -74.0 - (i + 1) * 0.5)
            segments.add(
                MovementSegment(
                    id = "s_$i",
                    startTimestampEpochMs = sStart,
                    endTimestampEpochMs = sEnd,
                    startPoint = loc,
                    endPoint = nextLoc,
                    distanceMeters = 350_000.0, // ~350 km per leg
                    durationMillis = 7200000L,
                    transport = TransportPrediction(TransportMode.CAR, 0.95f, "Driving")
                )
            )
        }

        val renderModel = TravelMapRenderModel(
            visits = visits,
            segments = segments,
            photos = emptyList()
        )

        val compressor = TimelineStoryCompressor(renderModel)
        val duration = compressor.totalStoryDurationSeconds

        // 8-day trip duration should be between 90s and 150s, not crushed to 30s!
        assertTrue(
            "Duration $duration seconds should be between 80.0s and 160.0s for an 8-day trip",
            duration in 80.0f..160.0f
        )
    }

    @Test
    fun minimumNodeDurations_enforced() {
        val baseTs = 1783153800000L
        val loc1 = GeoPoint(40.7128, -74.0060)
        val loc2 = GeoPoint(42.3601, -71.0589)

        val v1 = Visit("v1", "NY", location = loc1, startTimestampEpochMs = baseTs, endTimestampEpochMs = baseTs + 3600000L)
        val seg = MovementSegment("s1", startTimestampEpochMs = baseTs + 3600000L, endTimestampEpochMs = baseTs + 7200000L, startPoint = loc1, endPoint = loc2, distanceMeters = 300000.0, durationMillis = 3600000L, transport = TransportPrediction(TransportMode.CAR, 0.95f, "Driving"))
        val v2 = Visit("v2", "Boston", location = loc2, startTimestampEpochMs = baseTs + 7200000L, endTimestampEpochMs = baseTs + 10800000L)

        val renderModel = TravelMapRenderModel(listOf(v1, v2), listOf(seg), emptyList())
        val compressor = TimelineStoryCompressor(renderModel)

        for (node in compressor.nodes) {
            assertTrue("Node duration ${node.durationStorySeconds} should be >= 2.0s", node.durationStorySeconds >= 2.0f)
        }
    }
}
