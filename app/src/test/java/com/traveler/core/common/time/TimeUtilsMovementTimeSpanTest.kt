package com.traveler.core.common.time

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimeUtilsMovementTimeSpanTest {

    @Test
    fun testSameZoneId_UsesCompactFormat() {
        val zone = ZoneId.of("America/New_York")
        val start = Instant.parse("2026-07-01T14:00:00Z")
        val end = Instant.parse("2026-07-01T16:30:00Z")

        val result = TimeUtils.formatMovementTimeSpan(start, zone, end, zone)
        assertEquals("10:00 - 12:30", result)
    }

    @Test
    fun testDistinctZoneIdsWithEqualUtcOffset_ShowsBothGeographicTimezones() {
        // America/New_York (EDT UTC-4) and America/Toronto (EDT UTC-4) have the exact same offset on July 1
        val startZone = ZoneId.of("America/New_York")
        val endZone = ZoneId.of("America/Toronto")
        val start = Instant.parse("2026-07-01T14:00:00Z")
        val end = Instant.parse("2026-07-01T16:30:00Z")

        val result = TimeUtils.formatMovementTimeSpan(start, startZone, end, endZone)
        // Must show both local times with timezone identifiers rather than collapsing into single-zone format
        assertTrue("Must include arrow between zones", result.contains("→"))
        assertTrue("Must include start time and end time", result.contains("10:00") && result.contains("12:30"))
    }

    @Test
    fun testCrossTimezoneWithDayDifference_IncludesDayDifferenceIndicator() {
        // JFK (America/New_York, EDT UTC-4) -> ICN (Asia/Seoul, KST UTC+9)
        val startZone = ZoneId.of("America/New_York")
        val endZone = ZoneId.of("Asia/Seoul")
        val start = Instant.parse("2026-07-01T13:00:00Z") // 09:00 EDT July 1
        val end = Instant.parse("2026-07-02T03:00:00Z")   // 12:00 KST July 2

        val result = TimeUtils.formatMovementTimeSpan(start, startZone, end, endZone)
        assertTrue("Must include +1 day indicator", result.contains("(+1 day)"))
        assertTrue("Must include arrow", result.contains("→"))
    }
}
