package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import org.junit.Assert.*
import org.junit.Test

class WatchableLargeTripStoryCompressorTest {

    @Test
    fun massiveTrip_respectsStoryNodeBudget_andMinimumVisualDurations() {
        val visits = (1..50).map { i ->
            Visit(
                id = "visit_$i",
                placeName = "Place $i",
                placeAddress = null,
                placeId = "place_id_$i",
                location = GeoPoint(40.0 + i * 0.01, -74.0 + i * 0.01),
                startTimestampEpochMs = i * 3600_000L,
                endTimestampEpochMs = i * 3600_000L + 1800_000L,
                confidence = 0.95f
            )
        }

        val segments = (1..49).map { i ->
            MovementSegment(
                id = "seg_$i",
                startTimestampEpochMs = i * 3600_000L + 1800_000L,
                endTimestampEpochMs = (i + 1) * 3600_000L,
                startPoint = GeoPoint(40.0 + i * 0.01, -74.0 + i * 0.01),
                endPoint = GeoPoint(40.0 + (i + 1) * 0.01, -74.0 + (i + 1) * 0.01),
                simplifiedPoints = listOf(
                    GeoPoint(40.0 + i * 0.01, -74.0 + i * 0.01),
                    GeoPoint(40.0 + (i + 1) * 0.01, -74.0 + (i + 1) * 0.01)
                ),
                distanceMeters = 2000.0,
                durationMillis = 1800_000L,
                transport = TransportPrediction(TransportMode.CAR, 0.9f, "Car drive"),
                geometryProvenance = GeometryProvenance.SIMPLIFIED_OBSERVED
            )
        }

        val photos = listOf(
            MediaItem(
                id = "photo_start",
                contentUriString = "content://media/1",
                fileName = "start.jpg",
                mimeType = "image/jpeg",
                timestampEpochMs = 1000L,
                timestampConfidence = TimestampConfidence.EXIF_EXACT,
                matchedVisitId = "visit_1",
                isRepresentative = true
            ),
            MediaItem(
                id = "photo_mid",
                contentUriString = "content://media/25",
                fileName = "mid.jpg",
                mimeType = "image/jpeg",
                timestampEpochMs = 25000L,
                timestampConfidence = TimestampConfidence.EXIF_EXACT,
                matchedVisitId = "visit_25",
                isRepresentative = true
            ),
            MediaItem(
                id = "photo_end",
                contentUriString = "content://media/50",
                fileName = "end.jpg",
                mimeType = "image/jpeg",
                timestampEpochMs = 50000L,
                timestampConfidence = TimestampConfidence.EXIF_EXACT,
                matchedVisitId = "visit_50",
                isRepresentative = true
            )
        )

        val renderModel = TravelMapRenderModel(
            visits = visits,
            segments = segments,
            photos = photos
        )

        val compressor = TimelineStoryCompressor(renderModel, targetStoryDurationSeconds = 30.0f)

        // P1-09: Node count must be bounded for watchable recap (<= 24 nodes)
        assertTrue("Node count (${compressor.nodes.size}) must be <= 24", compressor.nodes.size <= 24)
        assertTrue("Node count (${compressor.nodes.size}) must be >= 4", compressor.nodes.size >= 4)

        // Minimum visual duration enforcement:
        for (node in compressor.nodes) {
            when (node) {
                is StoryNode.VisitNode -> {
                    if (node.heroPhoto != null) {
                        assertTrue("Photo visit duration (${node.durationStorySeconds}s) must be >= 1.0s", node.durationStorySeconds >= 1.0f)
                    } else {
                        assertTrue("Visit duration (${node.durationStorySeconds}s) must be >= 0.7s", node.durationStorySeconds >= 0.7f)
                    }
                }
                is StoryNode.MovementNode -> {
                    assertTrue("Movement duration (${node.durationStorySeconds}s) must be >= 0.5s", node.durationStorySeconds >= 0.5f)
                }
            }
        }

        // Total duration should be close to 30s target (within +-15%)
        assertTrue(
            "Total duration (${compressor.totalStoryDurationSeconds}s) should be between 25s and 35s",
            compressor.totalStoryDurationSeconds in 25.0f..35.0f
        )
    }
}
