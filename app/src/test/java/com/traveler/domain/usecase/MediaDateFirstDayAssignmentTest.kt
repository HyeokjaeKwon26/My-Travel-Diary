package com.traveler.domain.usecase

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.DayAssignmentConfidence
import com.traveler.core.model.GeometryProvenance
import com.traveler.core.model.LocationConfidenceLevel
import com.traveler.core.model.MediaItem
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.TimestampConfidence
import com.traveler.core.model.TransportMode
import com.traveler.core.model.TransportPrediction
import com.traveler.core.model.TripDayItem
import com.traveler.core.model.Visit
import com.traveler.data.repository.TripRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class MediaDateFirstDayAssignmentTest {

    private val nyZone = ZoneId.of("America/New_York")
    private val seoulZone = ZoneId.of("Asia/Seoul")

    @Test
    fun crossMidnightVisit_photoAppearsOnAssignedDayNotStartDay() {
        // Visit from July 1 22:00 to July 2 08:00 (America/New_York)
        val visitStart = ZonedDateTime.of(2026, 7, 1, 22, 0, 0, 0, nyZone).toInstant().toEpochMilli()
        val visitEnd = ZonedDateTime.of(2026, 7, 2, 8, 0, 0, 0, nyZone).toInstant().toEpochMilli()

        val visit = Visit(
            id = "hotel_overnight",
            placeName = "Hotel Manhattan",
            placeAddress = "New York, NY",
            placeId = "place_hotel",
            location = GeoPoint(40.7580, -73.9855),
            startTimestampEpochMs = visitStart,
            endTimestampEpochMs = visitEnd,
            confidence = 0.95f,
            timezoneId = "America/New_York"
        )

        // Photo captured on July 2 at 07:00 AM
        val photoTime = ZonedDateTime.of(2026, 7, 2, 7, 0, 0, 0, nyZone).toInstant().toEpochMilli()
        val photoJuly2 = MediaItem(
            id = "photo_july2_morning",
            contentUriString = "content://media/101",
            fileName = "breakfast.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = photoTime,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = "America/New_York",
            location = GeoPoint(40.7580, -73.9855),
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            matchedVisitId = visit.id,
            assignedDayIso = "2026-07-02",
            dayAssignmentConfidence = DayAssignmentConfidence.EXACT,
            dayAssignmentProvenance = "Explicit capture timezone (America/New_York)"
        )

        val reconstructedDays = TripRepositoryImpl.reconstructDays(
            visits = listOf(visit),
            segments = emptyList(),
            mediaItems = listOf(photoJuly2),
            tripStartDateIso = "2026-07-01",
            tripEndDateIso = "2026-07-02"
        )

        assertEquals("Should have 2 days", 2, reconstructedDays.size)

        val day1 = reconstructedDays.first { it.dateIso == "2026-07-01" }
        val day2 = reconstructedDays.first { it.dateIso == "2026-07-02" }

        // Day 1 contains the Visit anchor, but 0 photos
        assertEquals("Day 1 photo count must be 0", 0, day1.photoCount)
        val day1Visit = day1.items.filterIsInstance<TripDayItem.VisitItem>().firstOrNull()
        assertNotNull("Day 1 should have VisitItem", day1Visit)
        assertEquals("Day 1 visit must have 0 photos", 0, day1Visit!!.photos.size)

        // Day 2 must contain the photo with context
        assertEquals("Day 2 photo count must be 1", 1, day2.photoCount)
        val day2Contextual = day2.items.filterIsInstance<TripDayItem.ContextualPhotosItem>().firstOrNull()
        assertNotNull("Day 2 should have ContextualPhotosItem referencing hotel", day2Contextual)
        assertEquals("Day 2 contextual item has 1 photo", 1, day2Contextual!!.photos.size)
        assertEquals("Photo key match", "photo_july2_morning", day2Contextual.photos.first().id)
        assertTrue("Context label contains place name", day2Contextual.contextLabel.contains("Hotel Manhattan"))
    }

    @Test
    fun crossTimezoneMovement_arrivalPhotoAppearsOnArrivalDateWithoutDistanceDuplication() {
        // Flight from JFK to ICN departing July 1 12:00 EDT, arriving July 2 16:00 KST
        val flightStart = ZonedDateTime.of(2026, 7, 1, 12, 0, 0, 0, nyZone).toInstant().toEpochMilli()
        val flightEnd = ZonedDateTime.of(2026, 7, 2, 16, 0, 0, 0, seoulZone).toInstant().toEpochMilli()

        val flightSeg = MovementSegment(
            id = "flight_jfk_icn",
            startTimestampEpochMs = flightStart,
            endTimestampEpochMs = flightEnd,
            startPoint = GeoPoint(40.6413, -73.7781),
            endPoint = GeoPoint(37.4602, 126.4407),
            simplifiedPoints = listOf(GeoPoint(40.6413, -73.7781), GeoPoint(37.4602, 126.4407)),
            distanceMeters = 11000000.0,
            durationMillis = flightEnd - flightStart,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Flight JFK to ICN"),
            startTimezoneId = "America/New_York",
            endTimezoneId = "Asia/Seoul",
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC
        )

        // Departure photo on July 1
        val departurePhoto = MediaItem(
            id = "photo_departure",
            contentUriString = "content://media/201",
            fileName = "departure.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = flightStart + 30 * 60 * 1000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = "America/New_York",
            location = GeoPoint(40.6413, -73.7781),
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            matchedSegmentId = flightSeg.id,
            assignedDayIso = "2026-07-01",
            dayAssignmentConfidence = DayAssignmentConfidence.EXACT
        )

        // Arrival photo on July 2
        val arrivalPhoto = MediaItem(
            id = "photo_arrival",
            contentUriString = "content://media/202",
            fileName = "arrival.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = flightEnd - 30 * 60 * 1000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = "Asia/Seoul",
            location = GeoPoint(37.4602, 126.4407),
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            matchedSegmentId = flightSeg.id,
            assignedDayIso = "2026-07-02",
            dayAssignmentConfidence = DayAssignmentConfidence.EXACT
        )

        val reconstructedDays = TripRepositoryImpl.reconstructDays(
            visits = emptyList(),
            segments = listOf(flightSeg),
            mediaItems = listOf(departurePhoto, arrivalPhoto),
            tripStartDateIso = "2026-07-01",
            tripEndDateIso = "2026-07-02"
        )

        assertEquals(2, reconstructedDays.size)

        val day1 = reconstructedDays.first { it.dateIso == "2026-07-01" }
        val day2 = reconstructedDays.first { it.dateIso == "2026-07-02" }

        // Day 1 has departure photo and flight distance
        assertEquals("Day 1 photo count", 1, day1.photoCount)
        assertEquals("Day 1 distance", 11000000.0, day1.totalDistanceMeters, 0.001)

        // Day 2 has arrival photo and 0.0 distance (no duplicate flight distance!)
        assertEquals("Day 2 photo count", 1, day2.photoCount)
        assertEquals("Day 2 distance must be 0", 0.0, day2.totalDistanceMeters, 0.001)
        val day2Contextual = day2.items.filterIsInstance<TripDayItem.ContextualPhotosItem>().firstOrNull()
        assertNotNull("Day 2 should have ContextualPhotosItem for flight", day2Contextual)
        assertEquals(1, day2Contextual!!.photos.size)
        assertEquals("photo_arrival", day2Contextual.photos.first().id)
    }

    @Test
    fun emptySelectedDay_persistsAcrossReconstruction() {
        val visit1 = Visit(
            id = "v1",
            placeName = "Day 1 Place",
            placeAddress = null,
            placeId = null,
            location = GeoPoint(40.7128, -74.0060),
            startTimestampEpochMs = ZonedDateTime.of(2026, 7, 1, 10, 0, 0, 0, nyZone).toInstant().toEpochMilli(),
            endTimestampEpochMs = ZonedDateTime.of(2026, 7, 1, 12, 0, 0, 0, nyZone).toInstant().toEpochMilli(),
            confidence = 0.9f,
            timezoneId = "America/New_York"
        )

        val visit3 = Visit(
            id = "v3",
            placeName = "Day 3 Place",
            placeAddress = null,
            placeId = null,
            location = GeoPoint(40.7128, -74.0060),
            startTimestampEpochMs = ZonedDateTime.of(2026, 7, 3, 10, 0, 0, 0, nyZone).toInstant().toEpochMilli(),
            endTimestampEpochMs = ZonedDateTime.of(2026, 7, 3, 12, 0, 0, 0, nyZone).toInstant().toEpochMilli(),
            confidence = 0.9f,
            timezoneId = "America/New_York"
        )

        val reconstructedDays = TripRepositoryImpl.reconstructDays(
            visits = listOf(visit1, visit3),
            segments = emptyList(),
            mediaItems = emptyList(),
            tripStartDateIso = "2026-07-01",
            tripEndDateIso = "2026-07-03"
        )

        // Must have 3 days: Jul 1, Jul 2, Jul 3
        assertEquals("Must reconstruct all 3 inclusive days", 3, reconstructedDays.size)
        assertEquals("Day 1 date", "2026-07-01", reconstructedDays[0].dateIso)
        assertEquals("Day 2 date", "2026-07-02", reconstructedDays[1].dateIso)
        assertEquals("Day 3 date", "2026-07-03", reconstructedDays[2].dateIso)

        assertEquals("Day 2 is empty but preserved", 0, reconstructedDays[1].items.size)
        assertEquals("Day 2 photo count is 0", 0, reconstructedDays[1].photoCount)
    }

    @Test
    fun photoCountInvariant_isStrictlyPreserved() {
        val photos = (1..5).map { i ->
            MediaItem(
                id = "photo_$i",
                contentUriString = "content://media/$i",
                fileName = "photo_$i.jpg",
                mimeType = "image/jpeg",
                timestampEpochMs = 1782871200000L + i * 3600_000L,
                timestampConfidence = TimestampConfidence.EXIF_EXACT,
                assignedDayIso = if (i <= 3) "2026-07-01" else "2026-07-02",
                dayAssignmentConfidence = DayAssignmentConfidence.EXACT
            )
        }

        val uncertainPhotos = listOf(
            MediaItem(
                id = "uncertain_1",
                contentUriString = "content://media/99",
                fileName = "uncertain.jpg",
                mimeType = "image/jpeg",
                timestampEpochMs = null,
                timestampConfidence = TimestampConfidence.UNKNOWN,
                assignedDayIso = null,
                dayAssignmentConfidence = DayAssignmentConfidence.UNKNOWN
            )
        )

        val days = TripRepositoryImpl.reconstructDays(
            visits = emptyList(),
            segments = emptyList(),
            mediaItems = photos + uncertainPhotos,
            tripStartDateIso = "2026-07-01",
            tripEndDateIso = "2026-07-02"
        )

        val sumDayPhotos = days.sumOf { it.photoCount }
        assertEquals("Sum of day photos equals confident photos count", 5, sumDayPhotos)
        assertEquals("Sum of day photos + uncertain photos equals total photos", 6, sumDayPhotos + uncertainPhotos.size)
    }
}
