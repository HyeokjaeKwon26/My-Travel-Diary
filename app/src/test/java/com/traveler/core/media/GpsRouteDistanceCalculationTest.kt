package com.traveler.core.media

import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import org.junit.Assert.*
import org.junit.Test

class GpsRouteDistanceCalculationTest {

    @Test
    fun testTestA_100KmStraightRoute_PhotoAtQuarterPoint_MatchesMovement() {
        // Start: (40.0, -74.0), End: (40.0, -72.8) (~102 km straight east-west)
        val start = GeoPoint(40.0, -74.0)
        val end = GeoPoint(40.0, -72.8)

        val totalDist = GeodesicUtils.distanceMeters(start, end)
        assertTrue("Total segment distance should be ~102 km", totalDist > 100000.0)

        // Photo is exactly 25% along the segment
        val photoPoint = GeodesicUtils.interpolate(start, end, 0.25)

        val distanceToSegment = GeodesicUtils.distancePointToSegmentMeters(photoPoint, start, end)
        assertTrue("Distance to segment must be effectively 0 (< 5 meters), was: $distanceToSegment", distanceToSegment < 5.0)

        val segment = MovementSegment(
            id = "seg_100km",
            startTimestampEpochMs = 10000L,
            endTimestampEpochMs = 20000L,
            startPoint = start,
            endPoint = end,
            simplifiedPoints = listOf(start, end),
            distanceMeters = totalDist,
            durationMillis = 10000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "Car"),
            startTimezoneId = "America/New_York",
            endTimezoneId = "America/New_York"
        )

        val photo = MediaItem(
            id = "photo_quarter_point",
            contentUriString = "content://media/quarter",
            fileName = "quarter.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = 12500L, // 25% in time
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = photoPoint,
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.95f
        )

        val matched = PhotoLocationMatcher.matchPhotos(
            photos = listOf(photo),
            visits = emptyList(),
            segments = listOf(segment),
            rawPoints = emptyList()
        )

        assertEquals(1, matched.size)
        val matchedPhoto = matched[0]
        assertEquals("Photo should match segment", "seg_100km", matchedPhoto.matchedSegmentId)
        assertEquals(LocationConfidenceLevel.GPS_EXACT, matchedPhoto.locationConfidence)
    }

    @Test
    fun testTestB_BentRawRoute_CoarseSimplified_PhotoNearRawRoute_MatchesMovement() {
        // Raw route has an elbow curve: Start (0,0) -> Elbow (0.1, 0.1) -> End (0.2, 0.0)
        // Coarse simplified geometry only has endpoints: (0,0) -> (0.2, 0.0)
        // Straight line chord distance from (0.1, 0.1) to chord (0.0 to 0.2 at lat 0) is ~11 km (> 3 km threshold)
        val start = GeoPoint(0.0, 0.0)
        val elbow = GeoPoint(0.1, 0.1)
        val end = GeoPoint(0.2, 0.0)

        val rawPoints = listOf(
            LocationPoint(id = "p1", timestampEpochMs = 10000L, coordinate = start),
            LocationPoint(id = "p2", timestampEpochMs = 15000L, coordinate = elbow),
            LocationPoint(id = "p3", timestampEpochMs = 20000L, coordinate = end)
        )

        // Photo captured near elbow (0.1001, 0.1001), distance to raw elbow < 20 meters, but distance to straight chord > 10 km
        val photoLocation = GeoPoint(0.1001, 0.1001)

        val segment = MovementSegment(
            id = "seg_bent",
            startTimestampEpochMs = 10000L,
            endTimestampEpochMs = 20000L,
            startPoint = start,
            endPoint = end,
            simplifiedPoints = listOf(start, end), // Coarse simplified chord
            rawPoints = rawPoints,
            distanceMeters = 30000.0,
            durationMillis = 10000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "Car"),
            startTimezoneId = "UTC",
            endTimezoneId = "UTC"
        )

        val photo = MediaItem(
            id = "photo_near_raw_elbow",
            contentUriString = "content://media/elbow",
            fileName = "elbow.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = 15000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = photoLocation,
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.95f
        )

        val matched = PhotoLocationMatcher.matchPhotos(
            photos = listOf(photo),
            visits = emptyList(),
            segments = listOf(segment),
            rawPoints = emptyList()
        )

        assertEquals(1, matched.size)
        val matchedPhoto = matched[0]
        assertEquals("Photo should match segment using raw geometry", "seg_bent", matchedPhoto.matchedSegmentId)
        assertEquals(LocationConfidenceLevel.GPS_EXACT, matchedPhoto.locationConfidence)
        assertEquals(photoLocation.latitude, matchedPhoto.location!!.latitude, 0.00001)
    }
}
