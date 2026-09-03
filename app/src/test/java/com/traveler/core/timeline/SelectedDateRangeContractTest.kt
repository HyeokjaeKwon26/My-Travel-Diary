package com.traveler.core.timeline

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import com.traveler.data.repository.TripRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SelectedDateRangeContractTest {

    @Test
    fun testReconstructDays_ExcludesMediaOutsideRequestedDateRange() {
        val epochJuly1 = Instant.parse("2026-07-01T03:00:00Z").toEpochMilli() // 2026-07-01 12:00 JST
        val epochJuly2 = Instant.parse("2026-07-02T03:00:00Z").toEpochMilli() // 2026-07-02 12:00 JST

        val visitJuly1 = Visit(
            id = "v1",
            placeName = "Tokyo Tower",
            placeAddress = "Minato, Tokyo",
            placeId = "tokyo_tower",
            location = GeoPoint(35.6586, 139.7454),
            startTimestampEpochMs = epochJuly1,
            endTimestampEpochMs = epochJuly1 + 3600000L,
            confidence = 0.95f,
            timezoneId = "Asia/Tokyo"
        )

        val photoJuly1 = MediaItem(
            id = "p_july1",
            contentUriString = "content://media/1",
            fileName = "tokyo_july1.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = epochJuly1 + 1800000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = "Asia/Tokyo",
            matchedVisitId = "v1",
            assignedDayIso = "2026-07-01"
        )

        // Photo captured on July 2 local time
        val photoJuly2 = MediaItem(
            id = "p_july2",
            contentUriString = "content://media/2",
            fileName = "tokyo_july2.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = epochJuly2 + 1800000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = "Asia/Tokyo",
            matchedVisitId = "v1",
            assignedDayIso = "2026-07-02"
        )

        // Trip requested strictly for 2026-07-01 to 2026-07-01
        val days = TripRepositoryImpl.reconstructDays(
            visits = listOf(visitJuly1),
            segments = emptyList(),
            mediaItems = listOf(photoJuly1, photoJuly2),
            tripStartDateIso = "2026-07-01",
            tripEndDateIso = "2026-07-01"
        )

        assertEquals("Should only create 1 day for 2026-07-01", 1, days.size)
        assertEquals("2026-07-01", days[0].dateIso)
        assertEquals("Day 1 should only contain the photo assigned to July 1", 1, days[0].photoCount)
        assertEquals("p_july1", days[0].items.filterIsInstance<TripDayItem.VisitItem>().first().photos.first().id)
    }

    @Test
    fun testReconstructDays_HandlesCrossMidnightFlightAcrossTimezones() {
        val epochDeparture = 1782820000000L // 2026-07-01 20:00 JST
        val epochArrival = 1782850000000L   // 2026-07-02 04:20 JST (2026-07-01 12:20 PDT)

        val flight = MovementSegment(
            id = "flight_hnd_sfo",
            startTimestampEpochMs = epochDeparture,
            endTimestampEpochMs = epochArrival,
            startPoint = GeoPoint(35.5494, 139.7798),
            endPoint = GeoPoint(37.6213, -122.3790),
            simplifiedPoints = listOf(GeoPoint(35.5494, 139.7798), GeoPoint(37.6213, -122.3790)),
            distanceMeters = 8300000.0,
            durationMillis = 30000000L,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Flight"),
            startTimezoneId = "Asia/Tokyo",
            endTimezoneId = "America/Los_Angeles",
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC
        )

        val days = TripRepositoryImpl.reconstructDays(
            visits = emptyList(),
            segments = listOf(flight),
            mediaItems = emptyList(),
            tripStartDateIso = "2026-07-01",
            tripEndDateIso = "2026-07-02"
        )

        assertEquals(2, days.size)
        assertEquals("2026-07-01", days[0].dateIso)
        assertEquals("2026-07-02", days[1].dateIso)
    }

    @Test
    fun testReconstructDays_Utc14AndUtcMinus12Boundaries() {
        // Line Islands / Kiritimati (UTC+14)
        val kiritimatiZone = ZoneId.of("Pacific/Kiritimati")
        val kiritimatiInstant = Instant.parse("2026-07-01T12:00:00Z")
        val kiritimatiEpochMs = kiritimatiInstant.toEpochMilli()

        val visitKiritimati = Visit(
            id = "v_kiritimati",
            placeName = "Kiritimati",
            placeAddress = "Kiribati",
            placeId = "kiritimati",
            location = GeoPoint(1.8709, -157.3630),
            startTimestampEpochMs = kiritimatiEpochMs,
            endTimestampEpochMs = kiritimatiEpochMs + 3600000L,
            confidence = 0.95f,
            timezoneId = "Pacific/Kiritimati"
        )

        // Baker Island (UTC-12 / Etc/GMT+12)
        val bakerZone = ZoneId.of("Etc/GMT+12")
        val bakerInstant = Instant.parse("2026-07-01T12:00:00Z")
        val bakerEpochMs = bakerInstant.toEpochMilli()

        val visitBaker = Visit(
            id = "v_baker",
            placeName = "Baker Island",
            placeAddress = "Baker Island",
            placeId = "baker",
            location = GeoPoint(0.1936, -176.4769),
            startTimestampEpochMs = bakerEpochMs,
            endTimestampEpochMs = bakerEpochMs + 3600000L,
            confidence = 0.95f,
            timezoneId = "Etc/GMT+12"
        )

        val days = TripRepositoryImpl.reconstructDays(
            visits = listOf(visitKiritimati, visitBaker),
            segments = emptyList(),
            mediaItems = emptyList(),
            tripStartDateIso = "2026-07-01",
            tripEndDateIso = "2026-07-02"
        )

        assertEquals(2, days.size)
        assertTrue(days.any { it.dateIso == "2026-07-01" })
        assertTrue(days.any { it.dateIso == "2026-07-02" })
    }
}
