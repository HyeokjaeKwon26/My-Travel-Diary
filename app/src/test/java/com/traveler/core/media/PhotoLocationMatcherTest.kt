package com.traveler.core.media

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLocationMatcherTest {

    @Test
    fun testPhotoMatching_WithExactGps_PreservesLocation() {
        val photoGps = GeoPoint(43.0896, -79.0849) // Niagara Falls
        val photo = MediaItem(
            id = "IMG_1",
            contentUriString = "content://media/1",
            fileName = "IMG_001.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = 1783170000000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = photoGps,
            locationConfidence = LocationConfidenceLevel.GPS_EXACT
        )

        val result = PhotoLocationMatcher.matchPhotos(
            photos = listOf(photo),
            visits = emptyList(),
            segments = emptyList()
        )

        assertEquals(1, result.size)
        assertEquals(LocationConfidenceLevel.GPS_EXACT, result[0].locationConfidence)
        assertEquals(photoGps.latitude, result[0].location?.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun testPhotoMatching_NoGps_InfersFromActiveVisit() {
        val visitLocation = GeoPoint(43.0896, -79.0849) // Niagara Falls
        val visitStart = 1783160000000L
        val visitEnd = 1783180000000L

        val visit = Visit(
            id = "visit-niagara",
            placeName = "Niagara Falls State Park",
            location = visitLocation,
            startTimestampEpochMs = visitStart,
            endTimestampEpochMs = visitEnd
        )

        val photoWithoutGps = MediaItem(
            id = "IMG_2",
            contentUriString = "content://media/2",
            fileName = "IMG_002.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = 1783170000000L, // In the middle of visit
            timestampConfidence = TimestampConfidence.MEDIASTORE,
            location = null,
            locationConfidence = LocationConfidenceLevel.UNKNOWN
        )

        val result = PhotoLocationMatcher.matchPhotos(
            photos = listOf(photoWithoutGps),
            visits = listOf(visit),
            segments = emptyList()
        )

        assertEquals(1, result.size)
        assertEquals(LocationConfidenceLevel.VISIT_INFERRED, result[0].locationConfidence)
        assertEquals("visit-niagara", result[0].matchedVisitId)
        assertNotNull(result[0].location)
        assertEquals(visitLocation.latitude, result[0].location?.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun testPhotoMatching_NoGps_InterpolatesAlongMovementSegment() {
        val startLoc = GeoPoint(42.3601, -71.0589) // Boston
        val endLoc = GeoPoint(43.0896, -79.0849) // Niagara Falls
        val segStart = 1000000L
        val segEnd = 2000000L

        val segment = MovementSegment(
            id = "seg-1",
            startTimestampEpochMs = segStart,
            endTimestampEpochMs = segEnd,
            startPoint = startLoc,
            endPoint = endLoc,
            distanceMeters = 600000.0,
            durationMillis = 1000000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "")
        )

        val midPhoto = MediaItem(
            id = "IMG_3",
            contentUriString = "content://media/3",
            fileName = "IMG_003.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = 1500000L, // Exactly midway (fraction 0.5)
            timestampConfidence = TimestampConfidence.MEDIASTORE,
            location = null,
            locationConfidence = LocationConfidenceLevel.UNKNOWN
        )

        val result = PhotoLocationMatcher.matchPhotos(
            photos = listOf(midPhoto),
            visits = emptyList(),
            segments = listOf(segment)
        )

        assertEquals(1, result.size)
        assertEquals(LocationConfidenceLevel.TIMELINE_INTERPOLATED, result[0].locationConfidence)
        assertEquals("seg-1", result[0].matchedSegmentId)
        assertNotNull(result[0].location)
        assertTrue(result[0].location!!.latitude > startLoc.latitude)
        assertTrue(result[0].location!!.latitude < endLoc.latitude)
    }
}
