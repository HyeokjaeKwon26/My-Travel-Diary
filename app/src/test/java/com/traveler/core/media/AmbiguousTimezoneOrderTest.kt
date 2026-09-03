package com.traveler.core.media

import com.traveler.core.model.TimestampConfidence
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class AmbiguousTimezoneOrderTest {

    @Test
    fun ambiguousTimezoneCandidateOrder_producesIdenticalResult() {
        val nyZone = ZoneId.of("America/New_York")
        val seoulZone = ZoneId.of("Asia/Seoul")

        val rawCandidate = RawMediaCandidate(
            id = "photo_ambiguous",
            contentUriString = "content://media/1",
            fileName = "IMG_20260704_120000.jpg",
            mimeType = "image/jpeg",
            exifDateTimeOriginal = "2026:07:04 12:00:00",
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileDateModifiedMs = null,
            directGps = null,
            directGpsZoneId = null
        )

        // Trip interval spanning both timezones (July 1 to July 10, 2026 UTC)
        val tripStartMs = 1782777600000L
        val tripEndMs = 1783555200000L

        // Run with [New_York, Seoul]
        val resultNyFirst = PhotoTimestampResolver.resolve(
            candidate = rawCandidate,
            candidateTripTimezones = listOf(nyZone, seoulZone),
            visits = emptyList(),
            segments = emptyList(),
            tripIntervalStartMs = tripStartMs,
            tripIntervalEndMs = tripEndMs
        )

        // Run with [Seoul, New_York]
        val resultSeoulFirst = PhotoTimestampResolver.resolve(
            candidate = rawCandidate,
            candidateTripTimezones = listOf(seoulZone, nyZone),
            visits = emptyList(),
            segments = emptyList(),
            tripIntervalStartMs = tripStartMs,
            tripIntervalEndMs = tripEndMs
        )

        // Expected: Identical result regardless of candidate order
        assertEquals("Both orders must produce identical confidence", resultNyFirst.confidence, resultSeoulFirst.confidence)
        assertEquals("Both orders must produce identical epoch", resultNyFirst.timestampEpochMs, resultSeoulFirst.timestampEpochMs)
        assertEquals("Both orders must produce identical zone", resultNyFirst.resolvedZoneId, resultSeoulFirst.resolvedZoneId)

        // Expected: UNKNOWN confidence and null epoch because no timeline context distinguished them
        assertEquals(TimestampConfidence.UNKNOWN, resultNyFirst.confidence)
        assertNull("Ambiguous wall clock must not guess an epoch", resultNyFirst.timestampEpochMs)
        assertNull("Ambiguous wall clock must not guess a zone", resultNyFirst.resolvedZoneId)
    }
}
