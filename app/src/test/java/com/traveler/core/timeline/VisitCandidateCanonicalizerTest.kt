package com.traveler.core.timeline

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.Visit
import org.junit.Assert.*
import org.junit.Test

class VisitCandidateCanonicalizerTest {

    @Test
    fun testCase1_nestedDwell_mergesToOneCanonicalVisit() {
        // CASE 1: Visit A (12:16:52 -> 13:10:06) and Visit B (12:21:47 -> 13:09:29), within ~100m.
        val baseMs = 1782800000000L
        val tAStart = baseMs + (12 * 3600 + 16 * 60 + 52) * 1000L
        val tAEnd = baseMs + (13 * 3600 + 10 * 60 + 6) * 1000L

        val tBStart = baseMs + (12 * 3600 + 21 * 60 + 47) * 1000L
        val tBEnd = baseMs + (13 * 3600 + 9 * 60 + 29) * 1000L

        val locA = GeoPoint(40.7580, -73.9855) // Times Square
        val locB = GeoPoint(40.7585, -73.9850) // ~70m away

        val visitA = Visit(
            id = "v_times_sq_1",
            placeName = "Times Square Main",
            location = locA,
            startTimestampEpochMs = tAStart,
            endTimestampEpochMs = tAEnd,
            confidence = 0.85f
        )

        val visitB = Visit(
            id = "v_times_sq_2",
            placeName = "Times Square Store",
            location = locB,
            startTimestampEpochMs = tBStart,
            endTimestampEpochMs = tBEnd,
            confidence = 0.95f
        )

        val result = VisitCandidateCanonicalizer.deduplicate(listOf(visitA, visitB))

        // Must produce exactly ONE canonical visit
        assertEquals("Nested dwell must merge to exactly 1 canonical visit", 1, result.size)
        val canonical = result.first()

        // Preserves outer union dwell
        assertEquals("Canonical visit start must be min start", tAStart, canonical.startTimestampEpochMs)
        assertEquals("Canonical visit end must be max end", tAEnd, canonical.endTimestampEpochMs)

        // Metadata adopted from best candidate (higher confidence)
        assertEquals("Times Square Store", canonical.placeName)
    }

    @Test
    fun testCase2_overnightStayWithShortCandidate_mergesToOneOvernightVisit() {
        // CASE 2: Visit A (22:26:12 -> next day 08:22:31) and Visit B (22:48:13 -> 23:55:48), co-located.
        val baseMs = 1782800000000L
        val tAStart = baseMs + (22 * 3600 + 26 * 60 + 12) * 1000L
        val tAEnd = baseMs + (32 * 3600 + 22 * 60 + 31) * 1000L // next morning

        val tBStart = baseMs + (22 * 3600 + 48 * 60 + 13) * 1000L
        val tBEnd = baseMs + (23 * 3600 + 55 * 60 + 48) * 1000L

        val locHotel = GeoPoint(43.0962, -79.0377) // Niagara Hotel

        val visitOvernight = Visit(
            id = "v_hotel_overnight",
            placeName = "Niagara Falls Hotel",
            location = locHotel,
            startTimestampEpochMs = tAStart,
            endTimestampEpochMs = tAEnd,
            confidence = 0.80f
        )

        val visitEvening = Visit(
            id = "v_hotel_restaurant",
            placeName = "Hotel Restaurant",
            location = locHotel,
            startTimestampEpochMs = tBStart,
            endTimestampEpochMs = tBEnd,
            confidence = 0.92f
        )

        val result = VisitCandidateCanonicalizer.deduplicate(listOf(visitOvernight, visitEvening))

        assertEquals("Overnight stay with evening candidate must merge to 1 canonical visit", 1, result.size)
        val canonical = result.first()

        assertEquals("Must preserve full overnight start", tAStart, canonical.startTimestampEpochMs)
        assertEquals("Must preserve full overnight morning end", tAEnd, canonical.endTimestampEpochMs)
    }

    @Test
    fun testCase3_overlappingVisits20kmApart_resolvedWithoutDoubleDwell() {
        // CASE 3: Two temporally overlapping visits 20km apart.
        val baseMs = 1782800000000L
        val t1Start = baseMs
        val t1End = baseMs + 3600_000L // 1 hour

        val t2Start = baseMs + 1800_000L // 30 min overlap
        val t2End = baseMs + 5400_000L

        val locNYC = GeoPoint(40.7128, -74.0060)
        val locWhitePlains = GeoPoint(41.0339, -73.7629) // ~40km away

        val v1 = Visit(id = "v_nyc", placeName = "NYC", location = locNYC, startTimestampEpochMs = t1Start, endTimestampEpochMs = t1End, confidence = 0.95f)
        val v2 = Visit(id = "v_wp", placeName = "White Plains", location = locWhitePlains, startTimestampEpochMs = t2Start, endTimestampEpochMs = t2End, confidence = 0.90f)

        val result = VisitCandidateCanonicalizer.deduplicate(listOf(v1, v2))

        // Must strictly have zero overlap violations
        assertEquals("Must have zero overlap violations", 0, CanonicalVisitTimelineValidator.countOverlapViolations(result))
    }

    @Test
    fun testCase4_sequentialNonOverlappingVisits_preservesBoth() {
        // CASE 4: Two sequential visits with no overlap.
        val baseMs = 1782800000000L
        val v1 = Visit(id = "v1", placeName = "Stop 1", location = GeoPoint(40.0, -74.0), startTimestampEpochMs = baseMs, endTimestampEpochMs = baseMs + 3600_000L, confidence = 0.9f)
        val v2 = Visit(id = "v2", placeName = "Stop 2", location = GeoPoint(40.1, -74.1), startTimestampEpochMs = baseMs + 5400_000L, endTimestampEpochMs = baseMs + 9000_000L, confidence = 0.9f)

        val result = VisitCandidateCanonicalizer.deduplicate(listOf(v1, v2))
        assertEquals(2, result.size)
        assertEquals("v1", result[0].id)
        assertEquals("v2", result[1].id)
    }
}
