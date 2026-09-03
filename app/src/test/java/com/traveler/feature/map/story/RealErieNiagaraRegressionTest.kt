package com.traveler.feature.map.story

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.media.*
import com.traveler.core.model.*
import com.traveler.feature.map.renderer.TravelMapRenderModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Exact regression fixture derived from the REAL "east" Trip physical-device failure.
 *
 * Source Timeline proves:
 *   Erie Visit:       2026-08-19 14:00:28 → 14:12:30 (12 min)
 *   Erie→Niagara Mvt: 2026-08-19 14:12:30 → 15:40:55 (~88 min)
 *   Niagara Visit:    2026-08-19 15:40:55 → 16:06:18 (~25 min)
 *
 * Observed physical failure:
 *   Erie → arrive Niagara → jump back to Erie Love's → show Love's photo → jump back to Niagara.
 *
 * Required:
 *   Erie photo visible ONLY inside compressed Erie Visit episode.
 *   After Niagara episode begins: Erie photo visibility count = 0.
 */
class RealErieNiagaraRegressionTest {

    // Real timestamps (2026-08-19 EDT = UTC-4)
    private val erieStart   = 1787162428000L  // 2026-08-19T14:00:28-04:00
    private val erieEnd     = 1787163150000L  // 2026-08-19T14:12:30-04:00
    private val mvtStart    = erieEnd          // 14:12:30
    private val mvtEnd      = 1787168455000L  // 2026-08-19T15:40:55-04:00
    private val niagaraStart = mvtEnd          // 15:40:55
    private val niagaraEnd  = 1787169978000L  // 2026-08-19T16:06:18-04:00

    // Real locations
    private val erieLoc    = GeoPoint(42.2587, -79.7437)
    private val erieEndPt  = GeoPoint(42.2591, -79.7435)
    private val niagaraLoc = GeoPoint(43.0858, -79.0627)
    private val niagaraPt  = GeoPoint(43.0863, -79.0606)

    private fun buildTimeline(): TravelStoryTimeline {
        val erieVisit = Visit(
            id = "v_erie_real",
            placeName = "Love's Travel Stop",
            location = erieLoc,
            startTimestampEpochMs = erieStart,
            endTimestampEpochMs = erieEnd,
            confidence = 0.9f
        )
        val movement = MovementSegment(
            id = "s_erie_niagara_real",
            startTimestampEpochMs = mvtStart,
            endTimestampEpochMs = mvtEnd,
            startPoint = erieEndPt,
            endPoint = niagaraPt,
            distanceMeters = 160_000.0,
            durationMillis = mvtEnd - mvtStart,
            transport = TransportPrediction(TransportMode.CAR, 0.95f, "I-90")
        )
        val niagaraVisit = Visit(
            id = "v_niagara_real",
            placeName = "Niagara Falls",
            location = niagaraLoc,
            startTimestampEpochMs = niagaraStart,
            endTimestampEpochMs = niagaraEnd,
            confidence = 0.95f
        )

        // Erie photo: captured at 14:08:00, matched to Erie Visit
        val eriePhoto = MediaItem(
            id = "p_erie_loves",
            contentUriString = "content://media/p_erie_loves",
            fileName = "IMG_20260819_140800.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = erieStart + 8 * 60_000L, // 14:08:00
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = erieLoc,
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.95f,
            matchedVisitId = "v_erie_real",
            isRepresentative = false
        )

        // Niagara photo: captured at 15:50:00, matched to Niagara Visit
        val niagaraPhoto = MediaItem(
            id = "p_niagara_falls",
            contentUriString = "content://media/p_niagara_falls",
            fileName = "IMG_20260819_155000.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = niagaraStart + 9 * 60_000L, // 15:49:55
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = niagaraLoc,
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.95f,
            matchedVisitId = "v_niagara_real",
            isRepresentative = false
        )

        val renderModel = TravelMapRenderModel(
            visits = listOf(erieVisit, niagaraVisit),
            segments = listOf(movement),
            photos = listOf(eriePhoto, niagaraPhoto)
        )

        return TravelStoryTimeline.build(renderModel, StoryDurationProfile.STANDARD)
    }

    // =====================================================================
    // 1. Erie photo visible ONLY inside Erie episode
    // =====================================================================
    @Test
    fun testEriePhotoOnlyVisibleDuringErieEpisode() {
        val timeline = buildTimeline()

        // Find episode indices
        val erieEpIdx = timeline.episodes.indexOfFirst {
            it is StoryEpisode.VisitEpisode && it.visit.id == "v_erie_real"
        }
        val niagaraEpIdx = timeline.episodes.indexOfFirst {
            it is StoryEpisode.VisitEpisode && it.visit.id == "v_niagara_real"
        }
        assertTrue("Erie episode must exist", erieEpIdx >= 0)
        assertTrue("Niagara episode must exist", niagaraEpIdx >= 0)

        // Sample every 0.05s across entire story
        val dt = 0.05f
        var t = 0f
        var eriePhotoDuringNiagara = 0
        var eriePhotoCount = 0

        while (t <= timeline.totalStoryDurationSeconds) {
            val state = timeline.evaluateAtStoryTime(t)
            if (state.activePhoto?.id == "p_erie_loves") {
                eriePhotoCount++
                // Must be during Erie visit
                if (state.currentVisit?.id == "v_niagara_real") {
                    eriePhotoDuringNiagara++
                }
            }
            t += dt
        }

        assertTrue("Erie photo must be visible at least once", eriePhotoCount > 0)
        assertEquals(
            "Erie photo must NEVER appear during Niagara episode",
            0, eriePhotoDuringNiagara
        )
    }

    // =====================================================================
    // 2. Every photo's parentEpisodeIndex is valid and matches
    // =====================================================================
    @Test
    fun testPhotoParentEpisodeIndexValid() {
        val timeline = buildTimeline()

        for (moment in timeline.photoMoments) {
            assertTrue(
                "parentEpisodeIndex must be valid (got ${moment.parentEpisodeIndex})",
                moment.parentEpisodeIndex in timeline.episodes.indices
            )

            val ep = timeline.episodes[moment.parentEpisodeIndex]
            val epParentId = when (ep) {
                is StoryEpisode.VisitEpisode -> ep.visit.id
                is StoryEpisode.MovementEpisode -> ep.segment.id
            }
            assertEquals(
                "parentEpisodeIndex must match parentId for ${moment.mediaId}",
                moment.parentId, epParentId
            )
        }
    }

    // =====================================================================
    // 3. Photo display window strictly inside parent episode
    // =====================================================================
    @Test
    fun testPhotoWindowInsideParentEpisode() {
        val timeline = buildTimeline()

        for (moment in timeline.photoMoments) {
            assertTrue(
                "${moment.mediaId}: displayStart (${moment.displayStartStorySeconds}) >= " +
                "parentStart (${moment.parentEpisodeStoryStart})",
                moment.displayStartStorySeconds >= moment.parentEpisodeStoryStart - 0.01f
            )
            assertTrue(
                "${moment.mediaId}: displayEnd (${moment.displayEndStorySeconds}) <= " +
                "parentEnd (${moment.parentEpisodeStoryEnd})",
                moment.displayEndStorySeconds <= moment.parentEpisodeStoryEnd + 0.01f
            )
        }
    }

    // =====================================================================
    // 4. validateInvariants is clean
    // =====================================================================
    @Test
    fun testValidateInvariantsClean() {
        val timeline = buildTimeline()
        val report = timeline.validateInvariants()

        assertEquals("completedEpisodeReactivationCount", 0, report.completedEpisodeReactivationCount)
        assertEquals("outOfBoundsPhotoWindowCount", 0, report.outOfBoundsPhotoWindowCount)
        assertEquals("photoParentMismatchCount", 0, report.photoParentMismatchCount)
        assertTrue("Report must be clean", report.isClean)
    }

    // =====================================================================
    // 5. Photo parents are non-droppable (parent episode must exist)
    // =====================================================================
    @Test
    fun testSelectedPhotosHaveParentEpisodes() {
        val timeline = buildTimeline()
        val episodeParentIds = timeline.episodes.map { ep ->
            when (ep) {
                is StoryEpisode.VisitEpisode -> ep.visit.id
                is StoryEpisode.MovementEpisode -> ep.segment.id
            }
        }.toSet()

        for (moment in timeline.photoMoments) {
            assertTrue(
                "Photo ${moment.mediaId} parent ${moment.parentId} must have a Story episode",
                moment.parentId in episodeParentIds
            )
        }
    }

    // =====================================================================
    // 6. Parent Episode Stable ID and Midpoint Evaluation Match (P0-02, P0-04)
    // =====================================================================
    @Test
    fun testParentEpisodeStableIdBoundAndMidpointMatches() {
        val timeline = buildTimeline()

        for (moment in timeline.photoMoments) {
            assertTrue("parentEpisodeStableId must not be empty", moment.parentEpisodeStableId.isNotBlank())
            val ep = timeline.episodes[moment.parentEpisodeIndex]
            assertEquals(
                "parentEpisodeStableId must match episode.stableId",
                ep.stableId, moment.parentEpisodeStableId
            )

            // P0-04: At (displayStart + displayEnd)/2, evaluateAtStoryTime must return the parent episode
            val midTime = (moment.displayStartStorySeconds + moment.displayEndStorySeconds) / 2.0f
            val state = timeline.evaluateAtStoryTime(midTime)
            assertEquals(
                "Midpoint evaluate must match parentEpisodeIndex",
                moment.parentEpisodeIndex, state.episodeIndex
            )
            assertEquals(
                "Midpoint evaluate must return the photo",
                moment.mediaId, state.activePhoto?.id
            )
        }
    }

    // =====================================================================
    // 7. Continuity Diagnostic: Zero Violations during Continuous Playback (P0-07)
    // =====================================================================
    @Test
    fun testPlaybackContinuityDiagnosticNoViolations() {
        val timeline = buildTimeline()
        val diagnostic = PlaybackContinuityDiagnostic()

        val steps = 200
        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            val state = timeline.evaluate(progress)
            diagnostic.recordFrame(state, isUserScrubbing = false)
        }

        assertEquals("playbackProgressBackwardCount must be 0", 0, diagnostic.playbackProgressBackwardCount)
        assertEquals("episodeIndexBackwardCount must be 0", 0, diagnostic.episodeIndexBackwardCount)
        assertEquals("completedEpisodeReactivationCount must be 0", 0, diagnostic.completedEpisodeReactivationCount)
        assertEquals("spatialJumpCount must be 0", 0, diagnostic.spatialJumpCount)
        assertNull("No anomalies recorded", diagnostic.lastAnomalyDescription)
    }

    // =====================================================================
    // 8. Niagara Destination Selection (P1-07)
    // =====================================================================
    @Test
    fun testNiagaraDestinationReceivesRepresentativePhoto() {
        val timeline = buildTimeline()
        val niagaraMoment = timeline.photoMoments.firstOrNull { it.parentId == "v_niagara_real" }
        assertNotNull("Niagara must have a selected PhotoStoryMoment", niagaraMoment)
        assertEquals("p_niagara_falls", niagaraMoment?.mediaId)
    }
}
