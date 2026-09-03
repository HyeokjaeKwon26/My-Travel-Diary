package com.traveler.core.timeline

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DateRangeFilterIntervalTest {

    @Test
    fun testMultiDayHotelStay_EnclosingSelectedDate() {
        // User selects July 2 only: [July 2 00:00 UTC .. July 2 23:59 UTC]
        val filter = DateRangeFilter(
            startEpochMs = 1782864000000L, // 2026-07-02 00:00:00 UTC
            endEpochMs = 1782950399999L   // 2026-07-02 23:59:59 UTC
        )

        // Hotel Stay: July 1 22:00 UTC to July 3 08:00 UTC
        val stayStart = 1782856800000L // 2026-07-01 22:00:00 UTC
        val stayEnd = 1782979200000L   // 2026-07-03 08:00:00 UTC

        // Neither endpoint is July 2, but the interval encloses July 2 completely
        assertFalse("contains() on start must be false", filter.contains(stayStart))
        assertFalse("contains() on end must be false", filter.contains(stayEnd))
        assertTrue("overlaps() MUST be true for enclosing multi-day stay", filter.overlaps(stayStart, stayEnd))
    }

    @Test
    fun testOvernightFlight_StartsBeforeAndEndsDuring() {
        val filter = DateRangeFilter(10000L, 20000L)

        val flightStart = 5000L
        val flightEnd = 15000L

        assertTrue("Overnight flight overlapping boundary must return true", filter.overlaps(flightStart, flightEnd))
    }

    @Test
    fun testMovement_StartsDuringAndEndsAfter() {
        val filter = DateRangeFilter(10000L, 20000L)

        val start = 15000L
        val end = 25000L

        assertTrue("Movement extending beyond filter must return true", filter.overlaps(start, end))
    }

    @Test
    fun testExactBoundaryContact() {
        val filter = DateRangeFilter(10000L, 20000L)

        // Exact start boundary
        assertTrue(filter.overlaps(5000L, 10000L))
        // Exact end boundary
        assertTrue(filter.overlaps(20000L, 25000L))
    }

    @Test
    fun testNoOverlap_BeforeAndAfter() {
        val filter = DateRangeFilter(10000L, 20000L)

        // Strictly before
        assertFalse(filter.overlaps(1000L, 9999L))
        // Strictly after
        assertFalse(filter.overlaps(20001L, 30000L))
    }
}
