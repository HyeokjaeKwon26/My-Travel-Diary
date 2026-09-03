package com.traveler.core.media

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TimelinePathPhotoInterpolationTest {

    @Test
    fun bentRoute_interpolatesAlongCurvedPath_notStraightEndpoints() {
        val startTs = Instant.parse("2026-07-02T10:00:00Z").toEpochMilli()
        val endTs = Instant.parse("2026-07-02T10:30:00Z").toEpochMilli() // 30 mins

        // Route: A (42.0, -71.0) -> B (42.1, -71.0) -> C (42.1, -70.9) -> D (42.0, -70.9) (L-shaped loop)
        val pA = GeoPoint(42.0, -71.0)
        val pB = GeoPoint(42.1, -71.0)
        val pC = GeoPoint(42.1, -70.9)
        val pD = GeoPoint(42.0, -70.9)

        val rawPoints = listOf(
            LocationPoint("pt1", startTs, pA),
            LocationPoint("pt2", startTs + 10 * 60_000L, pB),
            LocationPoint("pt3", startTs + 20 * 60_000L, pC),
            LocationPoint("pt4", endTs, pD)
        )

        val segment = MovementSegment(
            id = "seg_bent",
            startTimestampEpochMs = startTs,
            endTimestampEpochMs = endTs,
            startPoint = pA,
            endPoint = pD,
            rawPoints = rawPoints,
            simplifiedPoints = listOf(pA, pB, pC, pD),
            distanceMeters = 30000.0,
            durationMillis = 30 * 60_000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "Road path"),
            geometryProvenance = GeometryProvenance.OBSERVED
        )

        // Photo taken at 10:20 (at point C) without GPS
        val photoTime = startTs + 20 * 60_000L
        val photo = MediaItem(
            id = "photo_at_curve",
            contentUriString = "content://media/99",
            fileName = "curve.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = photoTime,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = null,
            locationConfidence = LocationConfidenceLevel.UNKNOWN
        )

        val matched = PhotoLocationMatcher.matchPhotos(
            photos = listOf(photo),
            visits = emptyList(),
            segments = listOf(segment)
        )

        assertEquals(1, matched.size)
        val matchedPhoto = matched[0]
        assertNotNull(matchedPhoto.location)
        assertEquals("seg_bent", matchedPhoto.matchedSegmentId)

        // Must be close to pC (42.1, -70.9), NOT mid-point between A and D (42.0, -70.95)
        val distToC = GeodesicUtils.distanceMeters(matchedPhoto.location!!, pC)
        val distToEndpointMid = GeodesicUtils.distanceMeters(matchedPhoto.location!!, GeoPoint(42.0, -70.95))

        assertTrue("Photo at curve must interpolate near point C (dist=$distToC)", distToC < 50.0)
        assertTrue("Photo must NOT interpolate along direct straight line between endpoints", distToEndpointMid > 5000.0)
    }
}
