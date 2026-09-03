package com.traveler.core.media

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.TimestampConfidence
import com.traveler.core.model.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TimezoneContradictionScoreTest {

    private val nyZone = ZoneId.of("America/New_York")
    private val seoulZone = ZoneId.of("Asia/Seoul")

    @Test
    fun timezoneContradiction_withKnownVisitTimezone_mustNotReceivePositiveScore() {
        val exifLocal = LocalDateTime.of(2026, 7, 1, 10, 0, 0)

        // NY interpretation: 10:00 EDT = 14:00 UTC
        val nyInstantMs = ZonedDateTime.of(exifLocal, nyZone).toInstant().toEpochMilli()

        // Construct a Visit around 14:00 UTC with timezone Asia/Seoul (e.g. night market in Seoul)
        val visitInSeoul = Visit(
            id = "seoul_night_market",
            placeName = "Seoul Night Market",
            placeAddress = "Seoul, South Korea",
            placeId = "seoul_place",
            location = GeoPoint(37.5665, 126.9780),
            startTimestampEpochMs = nyInstantMs - 30 * 60 * 1000L,
            endTimestampEpochMs = nyInstantMs + 30 * 60 * 1000L,
            confidence = 0.9f,
            timezoneId = "Asia/Seoul" // Known conflicting timezone
        )

        // Trip interval encompassing the day
        val tripStartMs = nyInstantMs - 12 * 3600_000L
        val tripEndMs = nyInstantMs + 12 * 3600_000L

        // Score candidates with [America/New_York, Asia/Seoul]
        val resultNyFirst = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = "2026:07:01 10:00:00",
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileName = "photo.jpg",
            fileDateModifiedMs = null,
            referenceLocation = null,
            referenceLocationZoneId = null,
            candidateTripTimezones = listOf(nyZone, seoulZone),
            visits = listOf(visitInSeoul),
            segments = emptyList(),
            tripIntervalStartMs = tripStartMs,
            tripIntervalEndMs = tripEndMs
        )

        // America/New_York must NOT be declared exact winner based on conflicting Seoul visit
        assertNotEquals(
            "America/New_York must NOT win due to a conflicting Asia/Seoul visit",
            "America/New_York",
            resultNyFirst.resolvedZoneId
        )

        // Reverse candidate order [Asia/Seoul, America/New_York]
        val resultSeoulFirst = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = "2026:07:01 10:00:00",
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileName = "photo.jpg",
            fileDateModifiedMs = null,
            referenceLocation = null,
            referenceLocationZoneId = null,
            candidateTripTimezones = listOf(seoulZone, nyZone),
            visits = listOf(visitInSeoul),
            segments = emptyList(),
            tripIntervalStartMs = tripStartMs,
            tripIntervalEndMs = tripEndMs
        )

        assertEquals("Resolution must be candidate order-invariant", resultNyFirst.confidence, resultSeoulFirst.confidence)
        assertEquals("Resolution must be candidate order-invariant", resultNyFirst.resolvedZoneId, resultSeoulFirst.resolvedZoneId)
        assertEquals("Resolution must be candidate order-invariant", resultNyFirst.timestampEpochMs, resultSeoulFirst.timestampEpochMs)
    }

    @Test
    fun p0_04_authoritativeDirectGpsTimezone_onDstGap_mustReturnUnknownAndNotFallbackToTripZones() {
        val nyGps = GeoPoint(40.7128, -74.0060)
        val laZone = ZoneId.of("America/Los_Angeles")
        val seoulZone = ZoneId.of("Asia/Seoul")

        // 2026-03-08 02:30:00 does not exist in America/New_York due to spring-forward DST gap
        val result = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = "2026:03:08 02:30:00",
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileName = "times_square_dst_gap.jpg",
            fileDateModifiedMs = null,
            referenceLocation = nyGps,
            referenceLocationZoneId = "America/New_York", // Authoritative direct GPS timezone
            candidateTripTimezones = listOf(nyZone, laZone, seoulZone),
            visits = emptyList(),
            segments = emptyList(),
            tripIntervalStartMs = 1772928000000L,
            tripIntervalEndMs = 1773014400000L
        )

        assertEquals("Authoritative direct GPS timezone with DST gap must return UNKNOWN", TimestampConfidence.UNKNOWN, result.confidence)
        assertNotEquals("Must NEVER fall back to America/Los_Angeles", "America/Los_Angeles", result.resolvedZoneId)
        assertNotEquals("Must NEVER fall back to Asia/Seoul", "Asia/Seoul", result.resolvedZoneId)
    }

    @Test
    fun p1_05_singleCandidateZone_withKnownTimezoneContradiction_mustInvalidateContextAndReturnUnknown() {
        val exifLocal = LocalDateTime.of(2026, 7, 1, 10, 0, 0)
        val nyInstantMs = ZonedDateTime.of(exifLocal, nyZone).toInstant().toEpochMilli()

        // Visit at that instant is explicitly verified as Asia/Seoul
        val visitInSeoul = Visit(
            id = "v_seoul",
            placeName = "Seoul Palace",
            placeAddress = "Seoul",
            placeId = "seoul_1",
            location = GeoPoint(37.5665, 126.9780),
            startTimestampEpochMs = nyInstantMs - 3600000L,
            endTimestampEpochMs = nyInstantMs + 3600000L,
            confidence = 0.95f,
            timezoneId = "Asia/Seoul"
        )

        // Only candidate is America/New_York
        val result = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = "2026:07:01 10:00:00",
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileName = "photo_in_seoul.jpg",
            fileDateModifiedMs = null,
            referenceLocation = null,
            referenceLocationZoneId = null,
            candidateTripTimezones = listOf(nyZone),
            visits = listOf(visitInSeoul),
            segments = emptyList(),
            tripIntervalStartMs = nyInstantMs - 86400000L,
            tripIntervalEndMs = nyInstantMs + 86400000L
        )

        assertEquals("Known timezone contradiction must reject candidate and return UNKNOWN", TimestampConfidence.UNKNOWN, result.confidence)
        assertNotEquals("Contradictory America/New_York must NOT be resolved", "America/New_York", result.resolvedZoneId)
    }
}
