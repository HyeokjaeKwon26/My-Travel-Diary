package com.traveler.core.media

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.TimestampConfidence
import com.traveler.core.model.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class DstGapAndOverlapTest {

    private val nyZone = ZoneId.of("America/New_York")

    @Test
    fun springDstGap_nonexistentTime_resolvesToUnknown() {
        // 2026-03-08 02:30:00 does not exist in America/New_York (spring forward jump 02:00 -> 03:00)
        val result = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = "2026:03:08 02:30:00",
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileName = "gap.jpg",
            fileDateModifiedMs = null,
            referenceLocation = null,
            referenceLocationZoneId = "America/New_York",
            candidateTripTimezones = listOf(nyZone)
        )

        assertEquals("DST gap must resolve to UNKNOWN confidence", TimestampConfidence.UNKNOWN, result.confidence)
        assertNull("DST gap must have null epoch timestamp", result.timestampEpochMs)
    }

    @Test
    fun fallDstOverlap_withoutContext_resolvesToUnknown() {
        // 2026-11-01 01:30:00 occurs twice in America/New_York (EDT and EST)
        val result = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = "2026:11:01 01:30:00",
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileName = "overlap.jpg",
            fileDateModifiedMs = null,
            referenceLocation = null,
            referenceLocationZoneId = null,
            candidateTripTimezones = listOf(nyZone),
            visits = emptyList(),
            segments = emptyList()
        )

        assertEquals("Ambiguous DST overlap without context must resolve to UNKNOWN", TimestampConfidence.UNKNOWN, result.confidence)
        assertNull("Ambiguous DST overlap without context must have null epoch timestamp", result.timestampEpochMs)
    }

    @Test
    fun fallDstOverlap_withMatchingVisitContext_resolvesToCorrectInstant() {
        // 01:30:00 in EDT (UTC-4) = 05:30:00 UTC (1793511000000L)
        // 01:30:00 in EST (UTC-5) = 06:30:00 UTC (1793514600000L)
        val edtInstantMs = LocalDateTime.of(2026, 11, 1, 1, 30, 0).toInstant(ZoneOffset.ofHours(-4)).toEpochMilli()

        // Create a Visit matching the EDT instant (05:30 UTC)
        val matchingVisit = Visit(
            id = "late_night_diner",
            placeName = "Late Night Diner",
            placeAddress = "New York, NY",
            placeId = "diner_1",
            location = GeoPoint(40.7580, -73.9855),
            startTimestampEpochMs = edtInstantMs - 20 * 60 * 1000L,
            endTimestampEpochMs = edtInstantMs + 20 * 60 * 1000L,
            confidence = 0.95f,
            timezoneId = "America/New_York"
        )

        val result = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = "2026:11:01 01:30:00",
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileName = "diner.jpg",
            fileDateModifiedMs = null,
            referenceLocation = null,
            referenceLocationZoneId = "America/New_York",
            candidateTripTimezones = listOf(nyZone),
            visits = listOf(matchingVisit),
            segments = emptyList(),
            tripIntervalStartMs = edtInstantMs - 12 * 3600_000L,
            tripIntervalEndMs = edtInstantMs + 12 * 3600_000L
        )

        assertEquals("DST overlap with unique visit match resolves successfully", TimestampConfidence.EXIF_LOCAL, result.confidence)
        assertEquals("Resolved timestamp matches EDT instant", edtInstantMs, result.timestampEpochMs)
        assertEquals("America/New_York", result.resolvedZoneId)
    }
}
