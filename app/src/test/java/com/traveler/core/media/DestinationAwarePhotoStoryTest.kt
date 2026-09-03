package com.traveler.core.media

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DestinationAwarePhotoStoryTest {

    private fun createPhoto(
        id: String,
        timestampEpochMs: Long,
        matchedVisitId: String? = null,
        matchedSegmentId: String? = null,
        location: GeoPoint = GeoPoint(43.0962, -79.0377),
        isRepresentative: Boolean = false
    ): MediaItem {
        return MediaItem(
            id = id,
            contentUriString = "content://media/$id",
            fileName = "photo_$id.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = timestampEpochMs,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = location,
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.95f,
            matchedVisitId = matchedVisitId,
            matchedSegmentId = matchedSegmentId,
            isRepresentative = isRepresentative
        )
    }

    // 1. Niagara Falls receives representative coverage despite earlier photo-heavy stops (P1, P1-04, P1-11)
    @Test
    fun testNiagaraFallsReceivesRepresentativeCoverage() {
        val baseTime = 1723800000000L // 2026-08-16 08:00

        // 10 earlier intermediate stops with 40 total high-score photos
        val earlyVisits = (1..10).map { i ->
            Visit(
                id = "v_early_$i",
                placeName = "Stop $i",
                location = GeoPoint(40.7128 + i * 0.05, -74.0060),
                startTimestampEpochMs = baseTime + i * 3600_000L,
                endTimestampEpochMs = baseTime + i * 3600_000L + 1800_000L,
                confidence = 0.9f
            )
        }
        val earlyPhotos = earlyVisits.flatMap { v ->
            (1..4).map { j ->
                createPhoto("p_${v.id}_$j", v.startTimestampEpochMs + j * 60_000L, matchedVisitId = v.id)
            }
        }

        // Major destination: Niagara Falls with 12 photos across 3 distinct clusters
        val niagaraVisit = Visit(
            id = "v_niagara",
            placeName = "Niagara Falls",
            location = GeoPoint(43.0962, -79.0377),
            startTimestampEpochMs = baseTime + 50 * 3600_000L,
            endTimestampEpochMs = baseTime + 55 * 3600_000L, // 5 hour visit
            confidence = 0.95f
        )
        val niagaraPhotos = listOf(
            // Cluster 1: Arrival & Horseshoe Falls
            createPhoto("p_niagara_1", niagaraVisit.startTimestampEpochMs + 600_000L, matchedVisitId = "v_niagara"),
            createPhoto("p_niagara_2", niagaraVisit.startTimestampEpochMs + 700_000L, matchedVisitId = "v_niagara"),
            // Cluster 2: Maid of the Mist (1.5h later)
            createPhoto("p_niagara_3", niagaraVisit.startTimestampEpochMs + 5400_000L, matchedVisitId = "v_niagara"),
            createPhoto("p_niagara_4", niagaraVisit.startTimestampEpochMs + 5500_000L, matchedVisitId = "v_niagara"),
            // Cluster 3: Evening Illumination (4h later)
            createPhoto("p_niagara_5", niagaraVisit.startTimestampEpochMs + 14400_000L, matchedVisitId = "v_niagara")
        )

        val allVisits = earlyVisits + niagaraVisit
        val allPhotos = earlyPhotos + niagaraPhotos

        val moments = PhotoStoryEngine.buildGlobalPhotoStoryMoments(
            visits = allVisits,
            segments = emptyList(),
            allPhotos = allPhotos,
            profile = StoryDurationProfile.STANDARD
        )

        // Verify Niagara Falls is covered
        val niagaraMoments = moments.filter { it.parentId == "v_niagara" }
        assertTrue("Niagara Falls must receive at least 1 representative moment (found ${niagaraMoments.size})", niagaraMoments.isNotEmpty())
        assertTrue("Niagara Falls with 3 clusters should receive up to 3 moments in Standard", niagaraMoments.size in 1..3)

        // Verify total budget is bounded
        assertTrue("Total moments must not exceed Standard budget 35 (found ${moments.size})", moments.size <= 35)
    }

    // 2. Diversity before extra photos: Pass 1 gives 1 photo to each distinct destination (P1-07)
    @Test
    fun testDiversityBeforeExtraPhotos() {
        val baseTime = 1723800000000L
        val visitA = Visit(id = "v_a", placeName = "Erie", location = GeoPoint(42.1292, -80.0851), startTimestampEpochMs = baseTime, endTimestampEpochMs = baseTime + 3600_000L, confidence = 0.9f)
        val visitB = Visit(id = "v_b", placeName = "Niagara", location = GeoPoint(43.0962, -79.0377), startTimestampEpochMs = baseTime + 7200_000L, endTimestampEpochMs = baseTime + 10800_000L, confidence = 0.9f)

        // Visit A has 10 photos in 5 clusters, Visit B has 2 photos in 1 cluster
        val photosA = (1..5).map { createPhoto("p_a_$it", baseTime + it * 300_000L, matchedVisitId = "v_a") }
        val photosB = listOf(createPhoto("p_b_1", baseTime + 7500_000L, matchedVisitId = "v_b"))

        val moments = PhotoStoryEngine.buildGlobalPhotoStoryMoments(
            visits = listOf(visitA, visitB),
            segments = emptyList(),
            allPhotos = photosA + photosB,
            profile = StoryDurationProfile.SHORT // Budget = 15
        )

        val momentIds = moments.map { it.parentId }.toSet()
        assertTrue("Both Destination A and Destination B must be covered in Short profile", momentIds.contains("v_a") && momentIds.contains("v_b"))
    }

    // 3. 190 photos in single massive visit strictly bounded to <= 3 moments (P1-12)
    @Test
    fun testMassiveVisitPhotoBudgetBounded() {
        val baseTime = 1723800000000L
        val massiveVisit = Visit(
            id = "v_huge",
            placeName = "Metropolitan Museum",
            location = GeoPoint(40.7794, -73.9632),
            startTimestampEpochMs = baseTime,
            endTimestampEpochMs = baseTime + 36000_000L, // 10 hours
            confidence = 0.95f
        )
        val raw190 = (1..190).map { i ->
            createPhoto("p_met_$i", baseTime + i * 180_000L, matchedVisitId = "v_huge")
        }

        val moments = PhotoStoryEngine.buildGlobalPhotoStoryMoments(
            visits = listOf(massiveVisit),
            segments = emptyList(),
            allPhotos = raw190,
            profile = StoryDurationProfile.STANDARD
        )

        assertTrue("Massive 190-photo visit must be bounded to <= 3 moments (got ${moments.size})", moments.size <= 3)
    }

    // 4. Anchor exclusion reasons generated properly for unselected photo-bearing anchors (P1-08)
    @Test
    fun testAnchorExclusionReasons() {
        val baseTime = 1723800000000L
        val visit = Visit(
            id = "v_conflict",
            placeName = "Excluded Stop",
            location = GeoPoint(40.7128, -74.0060),
            startTimestampEpochMs = baseTime,
            endTimestampEpochMs = baseTime + 3600_000L,
            confidence = 0.9f
        )
        // Photo taken 2 hours before visit (timestamp conflict > 5 min)
        val conflictPhoto = createPhoto("p_conflict", baseTime - 7200_000L, matchedVisitId = "v_conflict")

        val anchors = PhotoStoryEngine.buildStoryAnchors(
            visits = listOf(visit),
            segments = emptyList(),
            allPhotos = listOf(conflictPhoto)
        )

        assertEquals(1, anchors.size)
        assertEquals(AnchorExclusionReason.TIMESTAMP_CONFLICT, anchors[0].exclusionReason)
    }

    // 5. Per-Day coverage report generates entries for all days (P1-14)
    @Test
    fun testPerDayCoverageReport() {
        val baseTime = 1723800000000L
        val dayDuration = 24 * 3600_000L

        val visits = (0 until 3).map { day ->
            Visit(
                id = "v_day$day",
                placeName = "Day${day}City",
                location = GeoPoint(40.0 + day * 1.0, -74.0 + day * 1.0),
                startTimestampEpochMs = baseTime + day * dayDuration,
                endTimestampEpochMs = baseTime + day * dayDuration + 7200_000L,
                confidence = 0.9f
            )
        }

        val photos = visits.flatMap { v ->
            (1..2).map { j ->
                createPhoto("p_${v.id}_$j", v.startTimestampEpochMs + j * 300_000L, matchedVisitId = v.id)
            }
        }

        val report = PhotoStoryEngine.buildCoverageReport(
            visits = visits,
            segments = emptyList(),
            allPhotos = photos,
            profile = StoryDurationProfile.STANDARD
        )

        assertEquals("Coverage report must have 3 days", 3, report.totalDays)
        assertTrue("Total moments must be > 0", report.totalMoments > 0)
        for (day in report.days) {
            assertTrue("Each day must have at least 1 anchor", day.anchorCount >= 1)
            assertTrue("Each day with photos must have selected representatives",
                day.selectedRepresentatives >= 1)
        }
    }

    // 6. Transient stop (short dwell, no name) gets lower importance than named destination (P1)
    @Test
    fun testTransientStopPenaltyInAnchorScoring() {
        val baseTime = 1723800000000L

        // Short anonymous stop (10 min)
        val transient = Visit(
            id = "v_transient",
            placeName = null,
            location = GeoPoint(41.5, -76.0),
            startTimestampEpochMs = baseTime,
            endTimestampEpochMs = baseTime + 600_000L,
            confidence = 0.8f
        )
        // Named destination (2 hours)
        val destination = Visit(
            id = "v_dest",
            placeName = "Major Landmark",
            location = GeoPoint(43.0, -79.0),
            startTimestampEpochMs = baseTime + 3600_000L,
            endTimestampEpochMs = baseTime + 10800_000L,
            confidence = 0.9f
        )

        val tPhoto = createPhoto("p_t", baseTime + 300_000L, matchedVisitId = "v_transient")
        val dPhoto = createPhoto("p_d", baseTime + 5400_000L, matchedVisitId = "v_dest")

        val anchors = PhotoStoryEngine.buildStoryAnchors(
            visits = listOf(transient, destination),
            segments = emptyList(),
            allPhotos = listOf(tPhoto, dPhoto)
        )

        val tAnchor = anchors.find { it.parentId == "v_transient" }!!
        val dAnchor = anchors.find { it.parentId == "v_dest" }!!

        assertTrue("Named destination (${dAnchor.importanceScore}) must score higher than transient stop (${tAnchor.importanceScore})",
            dAnchor.importanceScore > tAnchor.importanceScore)
    }
}
