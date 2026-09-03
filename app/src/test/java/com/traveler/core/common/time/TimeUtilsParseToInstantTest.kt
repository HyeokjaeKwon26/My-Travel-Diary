package com.traveler.core.common.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TimeUtilsParseToInstantTest {

    @Test
    fun parseToInstant_validExplicitTimestamps_succeed() {
        // ISO-8601 with UTC Z
        val instZ = TimeUtils.parseToInstant("2026-07-01T14:00:00Z")
        assertNotNull(instZ)
        assertEquals(Instant.parse("2026-07-01T14:00:00Z"), instZ)

        // ISO-8601 with Offset
        val instOffset = TimeUtils.parseToInstant("2026-07-01T10:00:00-04:00")
        assertNotNull(instOffset)
        assertEquals(Instant.parse("2026-07-01T14:00:00Z"), instOffset)

        // Numeric epoch millis
        val epochMs = TimeUtils.parseToInstant("1782871200000")
        assertNotNull(epochMs)
        assertEquals(Instant.ofEpochMilli(1782871200000L), epochMs)
    }

    @Test
    fun parseToInstant_timezoneLessWallClockStrings_returnNull() {
        // Must reject timezone-less wall clock strings to prevent inventing fake UTC (P2-11)
        assertNull(TimeUtils.parseToInstant("2026:07:01 10:00:00"))
        assertNull(TimeUtils.parseToInstant("2026-07-01 10:00:00"))
        assertNull(TimeUtils.parseToInstant("2026-07-01T10:00:00"))
        assertNull(TimeUtils.parseToInstant("invalid string"))
        assertNull(TimeUtils.parseToInstant(null))
        assertNull(TimeUtils.parseToInstant(""))
    }
}
