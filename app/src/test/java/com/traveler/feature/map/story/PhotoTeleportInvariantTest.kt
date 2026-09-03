package com.traveler.feature.map.story

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.media.*
import com.traveler.core.model.*
import com.traveler.feature.map.renderer.TravelMapRenderModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive photo teleport invariant tests (P0-01 through P0-08).
 *
 * These tests directly validate that the Erie → Niagara → Erie teleport regression
 * cannot occur and that all photo display windows are strictly confined to their
 * parent episodes.
 */
class PhotoTeleportInvariantTest {

    private fun createVisit(
        id: String,
        placeName: String,
        location: GeoPoint,
        startMs: Long,
        endMs: Long,
        isUserOverride: Boolean = false
    ): Visit = Visit(
        id = id,
        placeName = placeName,
        location = location,
        startTimestampEpochMs = startMs,
        endTimestampEpochMs = endMs,
        confidence = 0.9f,
        isUserOverride = isUserOverride
    )

    private fun createSegment(
        id: String,
        startMs: Long,
        endMs: Long,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        distance: Double = 100_000.0,
        mode: TransportMode = TransportMode.CAR
    ): MovementSegment = MovementSegment(
        id = id,
        startTimestampEpochMs = startMs,
        endTimestampEpochMs = endMs,
        startPoint = startPoint,
        endPoint = endPoint,
        distanceMeters = distance,
        durationMillis = endMs - startMs,
        transport = TransportPrediction(mode, 0.95f, "Highway")
    )

    private fun createPhoto(
        id: String,
        timestampEpochMs: Long,
        matchedVisitId: String? = null,
        matchedSegmentId: String? = null,
        location: GeoPoint = GeoPoint(43.0, -79.0),
        isRepresentative: Boolean = false
    ): MediaItem = MediaItem(
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

    // ===================================================================
    // 1. Erie → Niagara teleport regression (P0-07)
    // ===================================================================
    @Test
    fun testEriePhotoNeverAppearsDuringNiagaraEpisode() {
        val baseTime = 1723800000000L // 2026-08-16 08:00

        // Erie Visit: 08:00 → 09:00
        val erieVisit = createVisit("v_erie", "Erie", GeoPoint(42.1292, -80.0851),
            baseTime, baseTime + 3600_000L)

        // Movement Erie→Niagara: 09:00 → 12:00
        val movement = createSegment("s_erie_niagara",
            baseTime + 3600_000L, baseTime + 10800_000L,
            GeoPoint(42.1292, -80.0851), GeoPoint(43.0962, -79.0377), 200_000.0)

        // Niagara Visit: 12:00 → 17:00
        val niagaraVisit = createVisit("v_niagara", "Niagara Falls", GeoPoint(43.0962, -79.0377),
            baseTime + 10800_000L, baseTime + 28800_000L)

        // Erie photo — taken during Erie visit
        val eriePhoto = createPhoto("p_erie_rep", baseTime + 1800_000L, matchedVisitId = "v_erie",
            location = GeoPoint(42.1292, -80.0851), isRepresentative = true)

        // Niagara photo — taken during Niagara visit
        val niagaraPhoto = createPhoto("p_niagara_falls", baseTime + 14400_000L, matchedVisitId = "v_niagara",
            location = GeoPoint(43.0962, -79.0377), isRepresentative = true)

        val renderModel = TravelMapRenderModel(
            visits = listOf(erieVisit, niagaraVisit),
            segments = listOf(movement),
            photos = listOf(eriePhoto, niagaraPhoto)
        )

        val timeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)

        // Find the Niagara episode
        val niagaraEpIdx = timeline.episodes.indexOfFirst { ep ->
            ep is StoryEpisode.VisitEpisode && ep.visit.id == "v_niagara"
        }
        assertTrue("Niagara episode must exist", niagaraEpIdx >= 0)

        // Sample every 0.1s across the entire story
        val dt = 0.1f
        var t = 0f
        var eriePhotoDuringNiagara = 0
        while (t <= timeline.totalStoryDurationSeconds) {
            val state = timeline.evaluateAtStoryTime(t)
            if (state.currentVisit?.id == "v_niagara" && state.activePhoto != null) {
                // If we're in Niagara's episode, the active photo must NOT be Erie's
                if (state.activePhoto!!.id == "p_erie_rep") {
                    eriePhotoDuringNiagara++
                }
            }
            t += dt
        }

        assertEquals(
            "Erie photo must NEVER appear during Niagara episode (lateErieAfterNiagara = 0)",
            0, eriePhotoDuringNiagara
        )
    }

    // ===================================================================
    // 2. P0-01: All photo display windows lie inside parent episode (Hard invariant)
    // ===================================================================
    @Test
    fun testAllPhotoWindowsInsideParentEpisode() {
        val baseTime = 1723800000000L
        val visits = listOf(
            createVisit("v1", "Start", GeoPoint(40.7, -74.0), baseTime, baseTime + 7200_000L),
            createVisit("v2", "Middle", GeoPoint(42.1, -80.1), baseTime + 14400_000L, baseTime + 21600_000L),
            createVisit("v3", "End", GeoPoint(43.1, -79.0), baseTime + 28800_000L, baseTime + 36000_000L)
        )
        val segments = listOf(
            createSegment("s1", baseTime + 7200_000L, baseTime + 14400_000L, GeoPoint(40.7, -74.0), GeoPoint(42.1, -80.1)),
            createSegment("s2", baseTime + 21600_000L, baseTime + 28800_000L, GeoPoint(42.1, -80.1), GeoPoint(43.1, -79.0))
        )
        val photos = visits.flatMap { v ->
            (1..3).map { i ->
                createPhoto("p_${v.id}_$i", v.startTimestampEpochMs + i * 600_000L,
                    matchedVisitId = v.id, isRepresentative = i == 1)
            }
        }

        val renderModel = TravelMapRenderModel(visits = visits, segments = segments, photos = photos)
        val timeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)
        val report = timeline.validateInvariants()

        assertEquals("outOfBoundsPhotoWindowCount must be 0", 0, report.outOfBoundsPhotoWindowCount)
    }

    // ===================================================================
    // 3. P0-04: At photo midpoint, evaluateAtStoryTime returns matching parent
    // ===================================================================
    @Test
    fun testPhotoParentEpisodeMatchAtMidpoint() {
        val baseTime = 1723800000000L
        val visits = listOf(
            createVisit("v1", "A", GeoPoint(40.7, -74.0), baseTime, baseTime + 3600_000L),
            createVisit("v2", "B", GeoPoint(42.1, -80.1), baseTime + 7200_000L, baseTime + 10800_000L)
        )
        val segments = listOf(
            createSegment("s1", baseTime + 3600_000L, baseTime + 7200_000L, GeoPoint(40.7, -74.0), GeoPoint(42.1, -80.1))
        )
        val photos = listOf(
            createPhoto("p_a", baseTime + 1800_000L, matchedVisitId = "v1", isRepresentative = true),
            createPhoto("p_b", baseTime + 9000_000L, matchedVisitId = "v2", isRepresentative = true)
        )

        val renderModel = TravelMapRenderModel(visits = visits, segments = segments, photos = photos)
        val timeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)

        for (moment in timeline.photoMoments) {
            val midpoint = (moment.displayStartStorySeconds + moment.displayEndStorySeconds) / 2f
            val state = timeline.evaluateAtStoryTime(midpoint)

            if (state.activePhoto != null) {
                // The active photo must match the moment's photo
                assertEquals("Active photo at midpoint must be this moment's photo",
                    moment.mediaId, state.activePhoto!!.id)
            }
        }
    }

    // ===================================================================
    // 4. P0-06: Completed episode never reactivated
    // ===================================================================
    @Test
    fun testCompletedEpisodeNeverReactivated() {
        val baseTime = 1723800000000L
        val visits = listOf(
            createVisit("v1", "First", GeoPoint(40.7, -74.0), baseTime, baseTime + 3600_000L),
            createVisit("v2", "Second", GeoPoint(41.5, -76.0), baseTime + 7200_000L, baseTime + 10800_000L),
            createVisit("v3", "Third", GeoPoint(43.1, -79.0), baseTime + 14400_000L, baseTime + 18000_000L)
        )
        val segments = listOf(
            createSegment("s1", baseTime + 3600_000L, baseTime + 7200_000L, GeoPoint(40.7, -74.0), GeoPoint(41.5, -76.0)),
            createSegment("s2", baseTime + 10800_000L, baseTime + 14400_000L, GeoPoint(41.5, -76.0), GeoPoint(43.1, -79.0))
        )
        val photos = (1..3).flatMap { vIdx ->
            (1..2).map { pIdx ->
                val v = visits[vIdx - 1]
                createPhoto("p_v${vIdx}_$pIdx", v.startTimestampEpochMs + pIdx * 600_000L,
                    matchedVisitId = v.id, isRepresentative = pIdx == 1)
            }
        }

        val renderModel = TravelMapRenderModel(visits = visits, segments = segments, photos = photos)
        val timeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)
        val report = timeline.validateInvariants()

        assertEquals("completedEpisodeReactivationCount must be 0",
            0, report.completedEpisodeReactivationCount)
    }

    // ===================================================================
    // 5. P0-08: Full Story sweep — all invariants clean
    // ===================================================================
    @Test
    fun testFullStorySweepAllInvariantsClean() {
        val baseTime = 1723800000000L
        // Build a realistic multi-stop trip
        val visits = (0 until 8).map { i ->
            createVisit("v_$i", "Stop$i",
                GeoPoint(40.7 + i * 0.3, -74.0 + i * 0.5),
                baseTime + i * 14400_000L,
                baseTime + i * 14400_000L + 7200_000L)
        }
        val segments = (0 until 7).map { i ->
            createSegment("s_$i",
                baseTime + i * 14400_000L + 7200_000L,
                baseTime + (i + 1) * 14400_000L,
                GeoPoint(40.7 + i * 0.3, -74.0 + i * 0.5),
                GeoPoint(40.7 + (i + 1) * 0.3, -74.0 + (i + 1) * 0.5))
        }
        val photos = visits.flatMap { v ->
            (1..2).map { j ->
                createPhoto("p_${v.id}_$j", v.startTimestampEpochMs + j * 300_000L,
                    matchedVisitId = v.id, isRepresentative = j == 1)
            }
        }

        val renderModel = TravelMapRenderModel(visits = visits, segments = segments, photos = photos)
        val timeline = TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)
        val report = timeline.validateInvariants()

        assertTrue("Report must be clean: ${report}", report.isClean)
        assertEquals(0, report.completedEpisodeReactivationCount)
        assertEquals(0, report.outOfBoundsPhotoWindowCount)
        assertEquals(0, report.photoParentMismatchCount)
    }

    // ===================================================================
    // 6. 6-Day trip coverage — Days 4-6 must not be starved (P1-01)
    // ===================================================================
    @Test
    fun testSixDayTripLaterDaysNotStarved() {
        val baseTime = 1723800000000L
        val dayDuration = 24 * 3600_000L

        // Build 6-day trip with 3 visits per day, each with photos
        val visits = mutableListOf<Visit>()
        val segments = mutableListOf<MovementSegment>()
        val photos = mutableListOf<MediaItem>()

        for (day in 0 until 6) {
            val dayStart = baseTime + day * dayDuration
            for (stop in 0 until 3) {
                val vStart = dayStart + stop * 8 * 3600_000L
                val vEnd = vStart + 3 * 3600_000L
                val vId = "v_d${day}_s$stop"
                val lat = 40.0 + day * 0.5 + stop * 0.1
                val lng = -74.0 + day * 0.5 + stop * 0.1
                val location = GeoPoint(lat, lng)

                visits.add(createVisit(vId, "Day${day}Stop$stop", location, vStart, vEnd))

                // 2 photos per visit
                for (p in 1..2) {
                    photos.add(createPhoto("p_${vId}_$p", vStart + p * 300_000L,
                        matchedVisitId = vId, location = location))
                }

                // Movement between stops
                if (stop < 2) {
                    val sId = "s_d${day}_${stop}_${stop + 1}"
                    segments.add(createSegment(sId, vEnd, vEnd + 3600_000L,
                        location, GeoPoint(lat + 0.1, lng + 0.1), 50_000.0))
                }
            }

            // Movement between days
            if (day < 5) {
                val lastVisit = visits.last()
                val nextDayLat = 40.0 + (day + 1) * 0.5
                val nextDayLng = -74.0 + (day + 1) * 0.5
                segments.add(createSegment("s_d${day}_next",
                    lastVisit.endTimestampEpochMs, baseTime + (day + 1) * dayDuration,
                    lastVisit.location, GeoPoint(nextDayLat, nextDayLng), 300_000.0))
            }
        }

        val moments = PhotoStoryEngine.buildGlobalPhotoStoryMoments(
            visits = visits,
            segments = segments,
            allPhotos = photos,
            profile = StoryDurationProfile.STANDARD
        )

        // Group moments by day
        val momentsByDay = moments.groupBy { m ->
            val parent = visits.find { it.id == m.parentId }
            if (parent != null) (parent.startTimestampEpochMs - baseTime) / dayDuration else -1L
        }

        // Days 4, 5 (0-indexed: 3, 4, 5) must each have at least 1 representative
        for (dayIdx in 3L..5L) {
            val dayMoments = momentsByDay[dayIdx] ?: emptyList()
            assertTrue("Day $dayIdx must have at least 1 representative (found ${dayMoments.size})",
                dayMoments.isNotEmpty())
        }

        assertTrue("Total budget must be <= 35 (found ${moments.size})", moments.size <= 35)
    }

    // ===================================================================
    // 7. Transient stop penalty — short-dwell roadside deprioritized (P1)
    // ===================================================================
    @Test
    fun testTransientStopDeprioritizedVsDestination() {
        val baseTime = 1723800000000L

        // Transient stop: 15 min, no place name, 1 cluster
        val transientVisit = createVisit("v_gas", null.toString(), GeoPoint(41.5, -76.0),
            baseTime, baseTime + 900_000L) // 15 min

        // Destination: 3 hours, named, multiple clusters
        val destinationVisit = createVisit("v_dest", "Niagara Falls", GeoPoint(43.0962, -79.0377),
            baseTime + 3600_000L, baseTime + 14400_000L) // 3 hours

        val transientPhoto = createPhoto("p_gas", baseTime + 300_000L,
            matchedVisitId = "v_gas", location = GeoPoint(41.5, -76.0))
        val destPhoto1 = createPhoto("p_dest_1", baseTime + 5400_000L,
            matchedVisitId = "v_dest", location = GeoPoint(43.0962, -79.0377))
        val destPhoto2 = createPhoto("p_dest_2", baseTime + 9000_000L,
            matchedVisitId = "v_dest", location = GeoPoint(43.0962, -79.0377))

        val anchors = PhotoStoryEngine.buildStoryAnchors(
            visits = listOf(transientVisit, destinationVisit),
            segments = emptyList(),
            allPhotos = listOf(transientPhoto, destPhoto1, destPhoto2)
        )

        val transientAnchor = anchors.find { it.parentId == "v_gas" }
        val destAnchor = anchors.find { it.parentId == "v_dest" }

        assertNotNull("Transient anchor must exist", transientAnchor)
        assertNotNull("Destination anchor must exist", destAnchor)

        assertTrue("Destination must have higher importance than transient stop " +
                "(dest=${destAnchor!!.importanceScore}, transient=${transientAnchor!!.importanceScore})",
            destAnchor.importanceScore > transientAnchor.importanceScore)
    }
}
