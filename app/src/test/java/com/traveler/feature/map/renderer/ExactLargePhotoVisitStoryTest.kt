package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ExactLargePhotoVisitStoryTest {

    @Test
    fun test100PhotoVisits_CompressesToBoundedNodesAndTargetDuration() {
        val visits = mutableListOf<Visit>()
        val photos = mutableListOf<MediaItem>()
        val segments = mutableListOf<MovementSegment>()

        var t = 1782800000000L
        for (i in 1..100) {
            val vId = "visit_$i"
            val lat = 40.0 + (i * 0.01)
            val lng = -74.0 + (i * 0.01)
            val v = Visit(
                id = vId,
                placeName = "Stop $i",
                placeAddress = null,
                placeId = null,
                location = GeoPoint(lat, lng),
                startTimestampEpochMs = t,
                endTimestampEpochMs = t + 3600000L,
                confidence = 0.9f,
                timezoneId = "America/New_York",
                isUserOverride = (i == 1 || i == 100 || i == 50)
            )
            visits.add(v)

            // Each visit has a photo
            val photo = MediaItem(
                id = "photo_$i",
                contentUriString = "content://media/$i",
                fileName = "IMG_$i.jpg",
                mimeType = "image/jpeg",
                timestampEpochMs = t + 1000L,
                timestampConfidence = TimestampConfidence.EXIF_EXACT,
                captureTimezoneId = "America/New_York",
                location = GeoPoint(lat, lng),
                locationConfidence = LocationConfidenceLevel.GPS_EXACT,
                confidenceScore = 0.95f,
                matchedVisitId = vId,
                isRepresentative = (i % 10 == 0)
            )
            photos.add(photo)

            if (i < 100) {
                val nextLat = 40.0 + ((i + 1) * 0.01)
                val nextLng = -74.0 + ((i + 1) * 0.01)
                val seg = MovementSegment(
                    id = "seg_$i",
                    startTimestampEpochMs = t + 3600000L,
                    endTimestampEpochMs = t + 5400000L,
                    startPoint = GeoPoint(lat, lng),
                    endPoint = GeoPoint(nextLat, nextLng),
                    simplifiedPoints = listOf(GeoPoint(lat, lng), GeoPoint(nextLat, nextLng)),
                    distanceMeters = 1500.0,
                    durationMillis = 1800000L,
                    transport = TransportPrediction(if (i == 50) TransportMode.AIRPLANE else TransportMode.CAR, 0.9f, "Move"),
                    startTimezoneId = "America/New_York",
                    endTimezoneId = "America/New_York",
                    geometryProvenance = if (i == 50) GeometryProvenance.ESTIMATED_GEODESIC else GeometryProvenance.OBSERVED
                )
                segments.add(seg)
            }
            t += 5400000L
        }

        val renderModel = TravelMapRenderModel(
            visits = visits,
            segments = segments,
            photos = photos
        )

        val compressor = TimelineStoryCompressor(renderModel, targetStoryDurationSeconds = 30.0f)

        // 1. Verify bounded node count <= 24
        assertTrue("Node count (${compressor.nodes.size}) must be <= 24", compressor.nodes.size <= 24)

        // 2. Verify target duration within [27.0, 33.0] seconds
        assertTrue(
            "Total duration (${compressor.totalStoryDurationSeconds}s) must be close to target 30.0s",
            compressor.totalStoryDurationSeconds in 27.0f..33.0f
        )

        // 3. Verify hero photo moments meet minimum visibility (>= 1.0s)
        val visitNodes = compressor.nodes.filterIsInstance<StoryNode.VisitNode>()
        for (vn in visitNodes) {
            if (vn.heroPhoto != null) {
                assertTrue("Hero photo visit node must be >= 1.0s, was: ${vn.durationStorySeconds}", vn.durationStorySeconds >= 1.0f)
            }
        }
    }
}
