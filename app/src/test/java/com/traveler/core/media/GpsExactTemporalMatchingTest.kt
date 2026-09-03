package com.traveler.core.media

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GpsExactTemporalMatchingTest {

    private val hotelCoord = GeoPoint(40.7580, -73.9855) // Times Square Hotel
    private val day1Start = Instant.parse("2026-07-01T14:00:00Z").toEpochMilli() // 10:00 EDT
    private val day1End = Instant.parse("2026-07-01T16:00:00Z").toEpochMilli()   // 12:00 EDT

    private val day5Start = Instant.parse("2026-07-05T18:00:00Z").toEpochMilli() // 14:00 EDT
    private val day5End = Instant.parse("2026-07-05T19:00:00Z").toEpochMilli()   // 15:00 EDT

    @Test
    fun testP0_03_Day5DrivingPastDay1Hotel_MatchesDay5Movement_NotDay1Visit() {
        // Day 1: Visit Hotel A
        val day1Visit = Visit(
            id = "day1_hotel_a",
            placeName = "Hotel A",
            placeAddress = null,
            placeId = null,
            location = hotelCoord,
            startTimestampEpochMs = day1Start,
            endTimestampEpochMs = day1End,
            confidence = 0.95f
        )

        // Day 5: Driving past Hotel A (road passes 50m from hotel)
        val roadStart = GeoPoint(40.7550, -73.9855)
        val roadEnd = GeoPoint(40.7610, -73.9855)
        val day5Segment = MovementSegment(
            id = "day5_car_segment",
            startTimestampEpochMs = day5Start,
            endTimestampEpochMs = day5End,
            startPoint = roadStart,
            endPoint = roadEnd,
            simplifiedPoints = listOf(roadStart, hotelCoord, roadEnd),
            distanceMeters = 700.0,
            durationMillis = day5End - day5Start,
            transport = TransportPrediction(TransportMode.CAR, 0.95f, "Car")
        )

        // Photo captured during Day 5 drive near Hotel A with GPS_EXACT
        val photoTime = day5Start + 1800_000L // 14:30 Day 5
        val photo = MediaItem(
            id = "photo_day5_drive",
            contentUriString = "content://media/drive1",
            fileName = "IMG_drive.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = photoTime,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = "America/New_York",
            location = GeoPoint(40.7581, -73.9855),
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.98f
        )

        val matched = PhotoLocationMatcher.matchPhotos(
            photos = listOf(photo),
            visits = listOf(day1Visit),
            segments = listOf(day5Segment)
        ).first()

        // P0-03 REQUIREMENT: Must match Day 5 MovementSegment, NOT Day 1 Hotel A!
        assertNull("Photo on Day 5 must not match Day 1 visit", matched.matchedVisitId)
        assertEquals("Photo on Day 5 must match Day 5 active movement segment", "day5_car_segment", matched.matchedSegmentId)
        assertEquals(LocationConfidenceLevel.GPS_EXACT, matched.locationConfidence)
    }

    @Test
    fun testP0_03A_RepeatedVisit_MatchesDay5Visit_NotDay1Visit() {
        // Hotel visited on both Day 1 and Day 5
        val day1Visit = Visit(
            id = "day1_hotel_a",
            placeName = "Hotel A",
            location = hotelCoord,
            startTimestampEpochMs = day1Start,
            endTimestampEpochMs = day1End,
            confidence = 0.95f
        )
        val day5Visit = Visit(
            id = "day5_hotel_a",
            placeName = "Hotel A",
            location = hotelCoord,
            startTimestampEpochMs = day5Start,
            endTimestampEpochMs = day5End,
            confidence = 0.95f
        )

        val photoTime = day5Start + 1800_000L // during Day 5 visit
        val photo = MediaItem(
            id = "photo_day5_hotel",
            contentUriString = "content://media/hotel5",
            fileName = "IMG_hotel5.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = photoTime,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = "America/New_York",
            location = hotelCoord,
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.98f
        )

        val matched = PhotoLocationMatcher.matchPhotos(
            photos = listOf(photo),
            visits = listOf(day1Visit, day5Visit),
            segments = emptyList()
        ).first()

        assertEquals("Photo taken during Day 5 visit must match Day 5 visit", "day5_hotel_a", matched.matchedVisitId)
        assertNull(matched.matchedSegmentId)
    }

    @Test
    fun testP0_03_TimestampedGpsPhoto_FarFromAnyActiveItem_DoesNotMatchHistoricalVisit() {
        val day1Visit = Visit(
            id = "day1_hotel_a",
            placeName = "Hotel A",
            location = hotelCoord,
            startTimestampEpochMs = day1Start,
            endTimestampEpochMs = day1End,
            confidence = 0.95f
        )

        // Photo taken on Day 5 at Hotel A coordinates when NO active visit/movement exists on timeline
        val photoTime = day5Start + 1800_000L
        val photo = MediaItem(
            id = "photo_day5_isolated",
            contentUriString = "content://media/isolated",
            fileName = "IMG_isolated.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = photoTime,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = "America/New_York",
            location = hotelCoord,
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.98f
        )

        val matched = PhotoLocationMatcher.matchPhotos(
            photos = listOf(photo),
            visits = listOf(day1Visit),
            segments = emptyList()
        ).first()

        assertNull("Photo on Day 5 must not match Day 1 visit despite identical coordinates", matched.matchedVisitId)
        assertNull(matched.matchedSegmentId)
        assertEquals(LocationConfidenceLevel.GPS_EXACT, matched.locationConfidence)
        assertEquals(hotelCoord, matched.location)
    }
}
