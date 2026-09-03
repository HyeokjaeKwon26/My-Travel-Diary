package com.traveler.core.media

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.LocationConfidenceLevel
import com.traveler.core.model.MediaItem
import com.traveler.core.model.TimestampConfidence
import org.junit.Assert.*
import org.junit.Test

class ExactGpsUnknownTimestampTest {

    @Test
    fun exactGpsSurvivesUnknownTimestamp() {
        val seoulCoord = GeoPoint(37.5665, 126.9780)

        val photoWithGpsOnly = MediaItem(
            id = "photo_gps_only",
            contentUriString = "content://media/seoul",
            fileName = "SEOUL_EXIF_GPS.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = null,
            timestampConfidence = TimestampConfidence.UNKNOWN,
            captureTimezoneId = null,
            location = seoulCoord,
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.98f
        )

        val matched = PhotoLocationMatcher.matchPhotos(
            photos = listOf(photoWithGpsOnly),
            visits = emptyList(),
            segments = emptyList(),
            rawPoints = emptyList()
        )

        assertEquals(1, matched.size)
        val result = matched.first()

        assertNotNull("Exact GPS location must not be erased when timestamp is unknown", result.location)
        assertEquals(seoulCoord.latitude, result.location!!.latitude, 0.00001)
        assertEquals(seoulCoord.longitude, result.location!!.longitude, 0.00001)
        assertEquals("locationConfidence must remain GPS_EXACT", LocationConfidenceLevel.GPS_EXACT, result.locationConfidence)
        assertEquals(0.98f, result.confidenceScore, 0.01f)
        assertNull(result.matchedVisitId)
        assertNull(result.matchedSegmentId)
    }
}
