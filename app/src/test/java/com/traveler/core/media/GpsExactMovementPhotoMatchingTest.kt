package com.traveler.core.media

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GpsExactMovementPhotoMatchingTest {

    @Test
    fun gpsExactPhoto_duringMovementSegment_bindsToSegmentId_whilePreservingExactCoordinates() {
        val startTs = Instant.parse("2026-07-02T14:00:00Z").toEpochMilli()
        val endTs = Instant.parse("2026-07-02T15:00:00Z").toEpochMilli()

        val pStart = GeoPoint(42.355, -71.065) // Boston
        val pEnd = GeoPoint(42.100, -71.000)

        val segment = MovementSegment(
            id = "drive_segment_123",
            startTimestampEpochMs = startTs,
            endTimestampEpochMs = endTs,
            startPoint = pStart,
            endPoint = pEnd,
            simplifiedPoints = listOf(pStart, pEnd),
            distanceMeters = 30000.0,
            durationMillis = 3600_000L,
            transport = TransportPrediction(TransportMode.CAR, 0.95f, "Highway drive"),
            geometryProvenance = GeometryProvenance.SIMPLIFIED_OBSERVED
        )

        // Exact GPS photo taken halfway along the highway at 14:30
        val exactGps = GeoPoint(42.2275, -71.0325)
        val photoTime = startTs + 1800_000L

        val photo = MediaItem(
            id = "highway_photo",
            contentUriString = "content://media/101",
            fileName = "highway.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = photoTime,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = exactGps,
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.98f
        )

        val matched = PhotoLocationMatcher.matchPhotos(
            photos = listOf(photo),
            visits = emptyList(),
            segments = listOf(segment)
        )

        assertEquals(1, matched.size)
        val resultPhoto = matched[0]

        // P0-05: Must bind to segment while preserving GPS_EXACT and exact coordinate values
        assertEquals("drive_segment_123", resultPhoto.matchedSegmentId)
        assertNull(resultPhoto.matchedVisitId)
        assertEquals(LocationConfidenceLevel.GPS_EXACT, resultPhoto.locationConfidence)
        assertEquals(exactGps.latitude, resultPhoto.location?.latitude ?: 0.0, 0.00001)
        assertEquals(exactGps.longitude, resultPhoto.location?.longitude ?: 0.0, 0.00001)
        assertEquals(0.98f, resultPhoto.confidenceScore, 0.01f)
    }
}
