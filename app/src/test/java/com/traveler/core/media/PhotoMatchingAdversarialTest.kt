package com.traveler.core.media

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMatchingAdversarialTest {

    @Test
    fun testGpsDisagreement_DoesNotAttachPhotoToDistantVisit() {
        // Timeline has a visit in Boston at 15:00
        val bostonVisit = Visit(
            id = "boston-visit",
            placeName = "Boston Back Bay",
            location = GeoPoint(42.3503, -71.0810),
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 2000000L
        )

        // Photo has genuine EXIF GPS in Paris at 15:00 (5,500 km away!)
        val parisPhoto = MediaItem(
            id = "IMG_999",
            contentUriString = "content://media/999",
            fileName = "Eiffel.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = 1500000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = GeoPoint(48.8584, 2.2945),
            locationConfidence = LocationConfidenceLevel.GPS_EXACT
        )

        val result = PhotoLocationMatcher.matchPhotos(
            photos = listOf(parisPhoto),
            visits = listOf(bostonVisit),
            segments = emptyList()
        )

        assertEquals(1, result.size)
        // Must NOT match with Boston visit
        assertNull(result[0].matchedVisitId)
        // Must preserve genuine Paris coordinates
        assertEquals(48.8584, result[0].location?.latitude ?: 0.0, 0.001)
        assertEquals(LocationConfidenceLevel.GPS_EXACT, result[0].locationConfidence)
    }

    @Test
    fun testInFlightPhoto_InterpolatedWithFlightProvenance() {
        val jfk = GeoPoint(40.6413, -73.7781)
        val incheon = GeoPoint(37.4602, 126.4407)
        val segStart = 1000000L
        val segEnd = 1000000L + (14 * 3600 * 1000L) // 14 hours

        val flightSegment = MovementSegment(
            id = "flight-segment",
            startTimestampEpochMs = segStart,
            endTimestampEpochMs = segEnd,
            startPoint = jfk,
            endPoint = incheon,
            distanceMeters = 11000_000.0,
            durationMillis = 14 * 3600 * 1000L,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.95f, "Flight")
        )

        val inFlightPhoto = MediaItem(
            id = "IMG_888",
            contentUriString = "content://media/888",
            fileName = "Clouds.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = segStart + (7 * 3600 * 1000L), // Midway in flight
            timestampConfidence = TimestampConfidence.MEDIASTORE,
            location = null,
            locationConfidence = LocationConfidenceLevel.UNKNOWN
        )

        val result = PhotoLocationMatcher.matchPhotos(
            photos = listOf(inFlightPhoto),
            visits = emptyList(),
            segments = listOf(flightSegment)
        )

        assertEquals(1, result.size)
        assertEquals(LocationConfidenceLevel.TIMELINE_INTERPOLATED, result[0].locationConfidence)
        assertEquals("flight-segment", result[0].matchedSegmentId)
        assertTrue(result[0].confidenceScore in 0.65f..0.75f)
    }
}
