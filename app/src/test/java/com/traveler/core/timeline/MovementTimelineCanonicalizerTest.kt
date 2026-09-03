package com.traveler.core.timeline

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.model.GeometryProvenance
import com.traveler.core.model.LocationPoint
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.TransportMode
import com.traveler.core.model.TransportPrediction
import org.junit.Assert.*
import org.junit.Test

class MovementTimelineCanonicalizerTest {

    @Test
    fun testCaseA_sameModeNestedIncompatibleRoute_preventsDiscontinuousSwitching() {
        // Physical device failure reproduction:
        // Broad CAR A: 09:00 - 11:00 (NYC to Philadelphia via corridor)
        // Nested CAR B: 10:29 - 10:46 (partial corridor with ~25km lateral offset / incompatible geometry)
        val baseTs = 1783153800000L // 09:00

        val pNYC = GeoPoint(40.7128, -74.0060)
        val pPhilly = GeoPoint(39.9526, -75.1652)
        val pPrincetonCorridor = GeoPoint(40.3573, -74.6672)
        val pFarAwayStart = GeoPoint(40.6000, -74.2000)
        val pFarAwayEnd = GeoPoint(40.5000, -74.3000)

        val segCarA = MovementSegment(
            id = "car_broad_A",
            startTimestampEpochMs = baseTs,
            endTimestampEpochMs = baseTs + 7200000L, // 11:00 (2 hours)
            startPoint = pNYC,
            endPoint = pPhilly,
            simplifiedPoints = listOf(pNYC, pPrincetonCorridor, pPhilly),
            distanceMeters = 150000.0,
            durationMillis = 7200000L,
            transport = TransportPrediction(TransportMode.CAR, 0.85f, "Driving"),
            geometryProvenance = GeometryProvenance.SIMPLIFIED_OBSERVED
        )

        // Nested CAR B has high score locally but starts > 20km away from where A is at 10:29
        val segCarB = MovementSegment(
            id = "car_nested_B_incompatible",
            startTimestampEpochMs = baseTs + 5340000L, // 10:29
            endTimestampEpochMs = baseTs + 6360000L, // 10:46 (17 min)
            startPoint = pFarAwayStart,
            endPoint = pFarAwayEnd,
            simplifiedPoints = listOf(pFarAwayStart, pFarAwayEnd),
            distanceMeters = 15000.0,
            durationMillis = 1020000L,
            transport = TransportPrediction(TransportMode.CAR, 0.90f, "Driving"),
            geometryProvenance = GeometryProvenance.OBSERVED
        )

        val result = MovementTimelineCanonicalizer.canonicalize(listOf(segCarA, segCarB))

        // 1. Assert temporal non-overlap invariant
        CanonicalTimelineValidator.requireNonOverlapping(result.canonicalSegments)
        assertEquals(0, result.diagnostics.finalOverlapViolationsCount)

        // 2. Assert spatial continuity (no manufactured artificial teleport jumps)
        val spatialDiscontinuities = CanonicalTimelineValidator.findSpatialDiscontinuities(result.canonicalSegments, 500.0)
        assertEquals("Must not manufacture spatial discontinuities at split boundaries", 0, spatialDiscontinuities.size)
        assertEquals(0, result.diagnostics.spatialDiscontinuitiesCount)

        // 3. Global Viterbi optimizer preserves coherent continuous broad route A instead of jumping A1 -> B -> A2
        assertEquals(1, result.canonicalSegments.size)
        assertEquals("car_broad_A", result.canonicalSegments[0].id)
    }

    @Test
    fun testCaseB_nestedHighQualitySegment_spatiallyAligned_safelyReplacesOrSplits() {
        val baseTs = 1783153800000L

        val p1 = GeoPoint(40.7128, -74.0060)
        val pMid1 = GeoPoint(40.5000, -74.4000)
        val pMid2 = GeoPoint(40.3500, -74.6500)
        val p2 = GeoPoint(39.9526, -75.1652)

        // Broad low-confidence CAR 09:00 - 11:00
        val segBroad = MovementSegment(
            id = "car_broad",
            startTimestampEpochMs = baseTs,
            endTimestampEpochMs = baseTs + 7200000L,
            startPoint = p1,
            endPoint = p2,
            simplifiedPoints = listOf(p1, pMid1, pMid2, p2),
            distanceMeters = 150000.0,
            durationMillis = 7200000L,
            transport = TransportPrediction(TransportMode.CAR, 0.40f, "Inferred"),
            geometryProvenance = GeometryProvenance.ENDPOINT_INTERPOLATED
        )

        // High quality raw CAR 10:00 - 10:30 (located directly on the corridor at that time)
        val tNestStart = baseTs + 3600000L
        val tNestEnd = baseTs + 5400000L
        val posAtStart = MovementTimelineCanonicalizer.pointAtTimestamp(segBroad, tNestStart)
        val posAtEnd = MovementTimelineCanonicalizer.pointAtTimestamp(segBroad, tNestEnd)

        val rawPts = listOf(
            LocationPoint("pt1", tNestStart, posAtStart),
            LocationPoint("pt2", baseTs + 4500000L, GeodesicUtils.interpolate(posAtStart, posAtEnd, 0.5)),
            LocationPoint("pt3", tNestEnd, posAtEnd)
        )
        val segPrecise = MovementSegment(
            id = "car_precise",
            startTimestampEpochMs = tNestStart,
            endTimestampEpochMs = tNestEnd,
            startPoint = posAtStart,
            endPoint = posAtEnd,
            rawPoints = rawPts,
            simplifiedPoints = rawPts.map { it.coordinate },
            distanceMeters = 35000.0,
            durationMillis = 1800000L,
            transport = TransportPrediction(TransportMode.CAR, 0.98f, "Observed Driving"),
            geometryProvenance = GeometryProvenance.OBSERVED
        )

        val result = MovementTimelineCanonicalizer.canonicalize(listOf(segBroad, segPrecise))

        CanonicalTimelineValidator.requireNonOverlapping(result.canonicalSegments)
        assertEquals(0, result.diagnostics.finalOverlapViolationsCount)
        assertEquals(0, result.diagnostics.spatialDiscontinuitiesCount)
        assertTrue("High quality precise segment must be utilized in canonical output",
            result.canonicalSegments.any { it.id.contains("car_precise") || it.rawPoints.isNotEmpty() || it.geometryProvenance == GeometryProvenance.OBSERVED })
    }

    @Test
    fun testCaseC_pointAtTimestamp_accurateInterpolation() {
        val baseTs = 1783153800000L
        val p1 = GeoPoint(40.0, -74.0)
        val p2 = GeoPoint(40.0, -75.0)

        val seg = MovementSegment(
            id = "seg_test",
            startTimestampEpochMs = baseTs,
            endTimestampEpochMs = baseTs + 3600000L,
            startPoint = p1,
            endPoint = p2,
            simplifiedPoints = listOf(p1, p2),
            distanceMeters = 85000.0,
            durationMillis = 3600000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "Driving")
        )

        val posStart = MovementTimelineCanonicalizer.pointAtTimestamp(seg, baseTs)
        val posMid = MovementTimelineCanonicalizer.pointAtTimestamp(seg, baseTs + 1800000L)
        val posEnd = MovementTimelineCanonicalizer.pointAtTimestamp(seg, baseTs + 3600000L)

        assertEquals(p1.latitude, posStart.latitude, 0.001)
        assertEquals(p1.longitude, posStart.longitude, 0.001)
        assertEquals(40.0, posMid.latitude, 0.05)
        assertEquals(-74.5, posMid.longitude, 0.05)
        assertEquals(p2.latitude, posEnd.latitude, 0.001)
        assertEquals(p2.longitude, posEnd.longitude, 0.001)
    }

    @Test
    fun testCaseD_greedyTrap_globalViterbiAvoidsOscillation() {
        val baseTs = 1783153800000L

        // Broad continuous segment A (09:00 - 11:00)
        val pA1 = GeoPoint(40.0, -74.0)
        val pA2 = GeoPoint(41.0, -74.0)
        val segA = MovementSegment(
            id = "seg_A",
            startTimestampEpochMs = baseTs,
            endTimestampEpochMs = baseTs + 7200000L,
            startPoint = pA1,
            endPoint = pA2,
            simplifiedPoints = listOf(pA1, pA2),
            distanceMeters = 111000.0,
            durationMillis = 7200000L,
            transport = TransportPrediction(TransportMode.CAR, 0.80f, "Driving")
        )

        // Trapping segment B: locally higher confidence (0.95 vs 0.80) but 30km away laterally
        val pB1 = GeoPoint(40.5, -74.4)
        val pB2 = GeoPoint(40.6, -74.4)
        val segB = MovementSegment(
            id = "seg_B_trap",
            startTimestampEpochMs = baseTs + 3000000L,
            endTimestampEpochMs = baseTs + 4200000L,
            startPoint = pB1,
            endPoint = pB2,
            simplifiedPoints = listOf(pB1, pB2),
            distanceMeters = 12000.0,
            durationMillis = 1200000L,
            transport = TransportPrediction(TransportMode.CAR, 0.95f, "Driving")
        )

        val result = MovementTimelineCanonicalizer.canonicalize(listOf(segA, segB))

        // Viterbi should not take the bait and cause 20-30km jumps A -> B -> A
        assertEquals(1, result.canonicalSegments.size)
        assertEquals("seg_A", result.canonicalSegments[0].id)
        assertEquals(0, result.diagnostics.spatialDiscontinuitiesCount)
    }

    @Test
    fun testCaseE_threeCandidateOverlap_resolvesGloballyCoherent() {
        val baseTs = 1783153800000L
        val p0 = GeoPoint(40.0, -74.0)
        val p1 = GeoPoint(40.4, -74.4)
        val p2 = GeoPoint(40.8, -74.8)
        val p3 = GeoPoint(41.2, -75.2)

        // Seg A: 09:00 - 11:00 (p0 -> p2, at 10:00 reaches p1)
        val segA = MovementSegment(
            id = "seg_A",
            startTimestampEpochMs = baseTs,
            endTimestampEpochMs = baseTs + 7200000L,
            startPoint = p0,
            endPoint = p2,
            simplifiedPoints = listOf(p0, p1, p2),
            distanceMeters = 110000.0,
            durationMillis = 7200000L,
            transport = TransportPrediction(TransportMode.CAR, 0.75f, "Driving")
        )

        // Seg B: 10:00 - 12:00 (p1 -> p3, at 11:00 reaches p2)
        val segB = MovementSegment(
            id = "seg_B",
            startTimestampEpochMs = baseTs + 3600000L,
            endTimestampEpochMs = baseTs + 10800000L,
            startPoint = p1,
            endPoint = p3,
            simplifiedPoints = listOf(p1, p2, p3),
            distanceMeters = 110000.0,
            durationMillis = 7200000L,
            transport = TransportPrediction(TransportMode.CAR, 0.95f, "Driving"),
            geometryProvenance = GeometryProvenance.OBSERVED
        )

        // Seg C: 11:00 - 13:00 (p2 -> p3)
        val segC = MovementSegment(
            id = "seg_C",
            startTimestampEpochMs = baseTs + 7200000L,
            endTimestampEpochMs = baseTs + 14400000L,
            startPoint = p2,
            endPoint = p3,
            simplifiedPoints = listOf(p2, p3),
            distanceMeters = 55000.0,
            durationMillis = 7200000L,
            transport = TransportPrediction(TransportMode.CAR, 0.75f, "Driving")
        )

        val result = MovementTimelineCanonicalizer.canonicalize(listOf(segA, segB, segC))

        CanonicalTimelineValidator.requireNonOverlapping(result.canonicalSegments)
        assertEquals(0, result.diagnostics.finalOverlapViolationsCount)
        assertEquals(0, result.diagnostics.spatialDiscontinuitiesCount)
    }

    @Test
    fun testCaseF_slicePolylineByFraction_preservesIntermediateVertices() {
        val pA = GeoPoint(40.0, -74.0)
        val pB = GeoPoint(40.2, -74.1)
        val pC = GeoPoint(40.4, -74.2)
        val pD = GeoPoint(40.6, -74.3)
        val pE = GeoPoint(40.8, -74.4)

        val polyline = listOf(pA, pB, pC, pD, pE)

        // Slicing from 25% to 75% should include intermediate vertex pC
        val sliced = MovementTimelineCanonicalizer.slicePolylineByFraction(polyline, 0.25, 0.75)

        assertTrue("Sliced polyline must retain intermediate vertices", sliced.size >= 3)
        assertTrue("Intermediate point pC must be preserved in slice", sliced.any { it.latitude == pC.latitude && it.longitude == pC.longitude })
    }

    @Test
    fun testCaseG_canonicalizationIdempotence() {
        val baseTs = 1783153800000L
        val p1 = GeoPoint(40.7128, -74.0060)
        val p2 = GeoPoint(40.7589, -73.9851)

        val seg = MovementSegment(
            id = "seg_idempotent",
            startTimestampEpochMs = baseTs,
            endTimestampEpochMs = baseTs + 1800000L,
            startPoint = p1,
            endPoint = p2,
            distanceMeters = 8000.0,
            durationMillis = 1800000L,
            transport = TransportPrediction(TransportMode.SUBWAY, 0.95f, "Subway")
        )

        val pass1 = MovementTimelineCanonicalizer.canonicalize(listOf(seg))
        val pass2 = MovementTimelineCanonicalizer.canonicalize(pass1.canonicalSegments)

        assertEquals(pass1.canonicalSegments.size, pass2.canonicalSegments.size)
        assertEquals(pass1.canonicalSegments[0].id, pass2.canonicalSegments[0].id)
        assertEquals(pass1.canonicalSegments[0].startTimestampEpochMs, pass2.canonicalSegments[0].startTimestampEpochMs)
        assertEquals(pass1.canonicalSegments[0].endTimestampEpochMs, pass2.canonicalSegments[0].endTimestampEpochMs)
    }

    @Test
    fun testCaseH_halfOpenInterval_neverMoreThanOneActiveSegmentAtBoundary() {
        val baseTs = 1783153800000L
        val boundaryTs = baseTs + 1800000L // 10:00

        val seg1 = MovementSegment(
            id = "seg_1",
            startTimestampEpochMs = baseTs,
            endTimestampEpochMs = boundaryTs,
            startPoint = GeoPoint(40.0, -74.0),
            endPoint = GeoPoint(40.1, -74.0),
            distanceMeters = 10000.0,
            durationMillis = 1800000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "Driving")
        )

        val seg2 = MovementSegment(
            id = "seg_2",
            startTimestampEpochMs = boundaryTs,
            endTimestampEpochMs = boundaryTs + 1800000L,
            startPoint = GeoPoint(40.1, -74.0),
            endPoint = GeoPoint(40.2, -74.0),
            distanceMeters = 10000.0,
            durationMillis = 1800000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "Driving")
        )

        val segments = listOf(seg1, seg2)

        // At exact boundary 10:00, countActiveSegmentsAt must be exactly 1 (seg2 is active, seg1 has ended)
        val activeCountAtBoundary = CanonicalTimelineValidator.countActiveSegmentsAt(segments, boundaryTs)
        assertEquals("At exact boundary instant, active segment count must be <= 1", 1, activeCountAtBoundary)
    }

    @Test
    fun testCaseI_rawPointSliceBoundaries_synthesizesExactBoundaryPoints() {
        // P0-02: Broad observed segment with raw samples at:
        // 09:58, 10:03, 10:08, 10:13, 10:18, 10:23, 10:27, 10:32
        // Canonical slice: 10:00 -> 10:30
        val baseTs = 1783153800000L // 09:00:00
        val t0958 = baseTs + 58 * 60 * 1000L
        val t1000 = baseTs + 60 * 60 * 1000L
        val t1003 = baseTs + 63 * 60 * 1000L
        val t1008 = baseTs + 68 * 60 * 1000L
        val t1013 = baseTs + 73 * 60 * 1000L
        val t1018 = baseTs + 78 * 60 * 1000L
        val t1023 = baseTs + 83 * 60 * 1000L
        val t1027 = baseTs + 87 * 60 * 1000L
        val t1030 = baseTs + 90 * 60 * 1000L
        val t1032 = baseTs + 92 * 60 * 1000L

        val rawPoints = listOf(
            LocationPoint("p1", t0958, GeoPoint(40.00, -74.00)),
            LocationPoint("p2", t1003, GeoPoint(40.05, -74.05)),
            LocationPoint("p3", t1008, GeoPoint(40.10, -74.10)),
            LocationPoint("p4", t1013, GeoPoint(40.15, -74.15)),
            LocationPoint("p5", t1018, GeoPoint(40.20, -74.20)),
            LocationPoint("p6", t1023, GeoPoint(40.25, -74.25)),
            LocationPoint("p7", t1027, GeoPoint(40.30, -74.30)),
            LocationPoint("p8", t1032, GeoPoint(40.35, -74.35))
        )

        val broadSegment = MovementSegment(
            id = "broad_observed",
            startTimestampEpochMs = t0958,
            endTimestampEpochMs = t1032,
            startPoint = GeoPoint(40.00, -74.00),
            endPoint = GeoPoint(40.35, -74.35),
            rawPoints = rawPoints,
            simplifiedPoints = rawPoints.map { it.coordinate },
            distanceMeters = 50000.0,
            durationMillis = t1032 - t0958,
            transport = TransportPrediction(TransportMode.CAR, 0.95f, "Driving"),
            geometryProvenance = GeometryProvenance.OBSERVED
        )

        val sliced = MovementTimelineCanonicalizer.sliceSegmentWithIntermediateVertices(broadSegment, t1000, t1030)
        assertNotNull(sliced)
        assertEquals(t1000, sliced!!.startTimestampEpochMs)
        assertEquals(t1030, sliced.endTimestampEpochMs)

        // Synthesized boundary points must equal exact pointAtTimestamp results
        val expectedStartPt = MovementTimelineCanonicalizer.pointAtTimestamp(broadSegment, t1000)
        val expectedEndPt = MovementTimelineCanonicalizer.pointAtTimestamp(broadSegment, t1030)

        assertEquals("Slice start point must equal exact pointAtTimestamp(10:00)", expectedStartPt.latitude, sliced.startPoint.latitude, 0.0001)
        assertEquals("Slice start point must equal exact pointAtTimestamp(10:00)", expectedStartPt.longitude, sliced.startPoint.longitude, 0.0001)
        assertEquals("Slice end point must equal exact pointAtTimestamp(10:30)", expectedEndPt.latitude, sliced.endPoint.latitude, 0.0001)
        assertEquals("Slice end point must equal exact pointAtTimestamp(10:30)", expectedEndPt.longitude, sliced.endPoint.longitude, 0.0001)

        // Raw points in slice must start at 10:00 and end at 10:30 with all intermediate raw points preserved
        assertEquals(8, sliced.rawPoints.size) // start (10:00) + 6 intermediate (10:03..10:27) + end (10:30)
        assertEquals(t1000, sliced.rawPoints.first().timestampEpochMs)
        assertEquals(t1030, sliced.rawPoints.last().timestampEpochMs)
        assertEquals(t1003, sliced.rawPoints[1].timestampEpochMs)
        assertEquals(t1027, sliced.rawPoints[6].timestampEpochMs)
    }

    @Test
    fun testCaseJ_infeasibleTransition_isStrictlyRejected() {
        // P0-01: An impossible jump (> 1500m at boundary timestamp) receives NEGATIVE_INFINITY
        // and cannot be selected even if it's the only alternative candidate
        val baseTs = 1783153800000L

        val segA = MovementSegment(
            id = "seg_A",
            startTimestampEpochMs = baseTs,
            endTimestampEpochMs = baseTs + 3600000L,
            startPoint = GeoPoint(40.0, -74.0),
            endPoint = GeoPoint(40.1, -74.0),
            simplifiedPoints = listOf(GeoPoint(40.0, -74.0), GeoPoint(40.1, -74.0)),
            distanceMeters = 10000.0,
            durationMillis = 3600000L,
            transport = TransportPrediction(TransportMode.CAR, 0.8f, "Driving")
        )

        // Seg B is 20km away at boundary timestamp (baseTs + 1800000L)
        val segB = MovementSegment(
            id = "seg_B_teleport",
            startTimestampEpochMs = baseTs + 1800000L,
            endTimestampEpochMs = baseTs + 3600000L,
            startPoint = GeoPoint(40.3, -74.0), // ~22km away
            endPoint = GeoPoint(40.4, -74.0),
            simplifiedPoints = listOf(GeoPoint(40.3, -74.0), GeoPoint(40.4, -74.0)),
            distanceMeters = 10000.0,
            durationMillis = 1800000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "Driving")
        )

        val result = MovementTimelineCanonicalizer.canonicalize(listOf(segA, segB))

        // Infeasible teleport must NOT be selected; segA remains continuous
        assertEquals(0, result.diagnostics.spatialDiscontinuitiesCount)
        assertEquals(1, result.canonicalSegments.size)
        assertEquals("seg_A", result.canonicalSegments[0].id)
    }

    @Test
    fun testPass202_caseA_sourceGap_classifiedAsSourceTimelineGap_succeeds() {
        // CASE A - SOURCE GAP:
        // Semantic Activity A ends at coordinate X at 10:00:00
        // Semantic Activity B starts at coordinate Y at 10:00:00
        // (Temporal boundary is 0ms, but there is a pre-existing 2.5km source gap in original Activities)
        val baseTs = 1783153800000L

        val pX = GeoPoint(40.0, -74.0)
        val pY = GeoPoint(40.0225, -74.0) // ~2.5km north

        val segA = MovementSegment(
            id = "source_act_A",
            startTimestampEpochMs = baseTs,
            endTimestampEpochMs = baseTs + 3600000L,
            startPoint = GeoPoint(39.9, -74.0),
            endPoint = pX,
            simplifiedPoints = listOf(GeoPoint(39.9, -74.0), pX),
            distanceMeters = 11000.0,
            durationMillis = 3600000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "Driving")
        )

        val segB = MovementSegment(
            id = "source_act_B",
            startTimestampEpochMs = baseTs + 3600000L, // exact 0ms gap
            endTimestampEpochMs = baseTs + 7200000L,
            startPoint = pY,
            endPoint = GeoPoint(40.1, -74.0),
            simplifiedPoints = listOf(pY, GeoPoint(40.1, -74.0)),
            distanceMeters = 8600.0,
            durationMillis = 3600000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "Driving")
        )

        val result = MovementTimelineCanonicalizer.canonicalize(listOf(segA, segB))

        // Temporal non-overlap must hold
        CanonicalTimelineValidator.requireNonOverlapping(result.canonicalSegments)
        assertEquals(0, result.diagnostics.finalOverlapViolationsCount)

        // Must produce 2 distinct segments (no fake teleport road drawn)
        assertEquals(2, result.canonicalSegments.size)
        assertEquals("source_act_A", result.canonicalSegments[0].id)
        assertEquals("source_act_B", result.canonicalSegments[1].id)

        // Discontinuity must be detected and classified as SOURCE_TIMELINE_GAP
        assertEquals(1, result.diagnostics.spatialDiscontinuitiesCount)
        assertEquals(1, result.diagnostics.sourceTimelineGapsCount)
        assertEquals(0, result.diagnostics.canonicalizerIntroducedJumpsCount)

        val disc = result.diagnostics.spatialDiscontinuities.first()
        assertEquals(SpatialDiscontinuityType.SOURCE_TIMELINE_GAP, disc.type)
        assertEquals("source_act_A", disc.previousSegmentId)
        assertEquals("source_act_B", disc.nextSegmentId)
        assertTrue(disc.spatialGapMeters > 2000.0)
    }

    @Test
    fun testPass202_caseB_canonicalizerIntroducedJump_repairedDeterministically() {
        // CASE B - CANONICALIZER CREATED JUMP:
        // Broad route A: 09:00 - 11:00 (continuous route)
        // Nested route B: 10:00 - 10:30 (starts far away from corridor)
        val baseTs = 1783153800000L

        val pNYC = GeoPoint(40.7128, -74.0060)
        val pPhilly = GeoPoint(39.9526, -75.1652)

        val segA = MovementSegment(
            id = "broad_A",
            startTimestampEpochMs = baseTs,
            endTimestampEpochMs = baseTs + 7200000L,
            startPoint = pNYC,
            endPoint = pPhilly,
            simplifiedPoints = listOf(pNYC, pPhilly),
            distanceMeters = 150000.0,
            durationMillis = 7200000L,
            transport = TransportPrediction(TransportMode.CAR, 0.85f, "Driving")
        )

        val segB = MovementSegment(
            id = "nested_B_far",
            startTimestampEpochMs = baseTs + 3600000L,
            endTimestampEpochMs = baseTs + 5400000L,
            startPoint = GeoPoint(41.5, -73.5), // far away
            endPoint = GeoPoint(41.6, -73.4),
            simplifiedPoints = listOf(GeoPoint(41.5, -73.5), GeoPoint(41.6, -73.4)),
            distanceMeters = 15000.0,
            durationMillis = 1800000L,
            transport = TransportPrediction(TransportMode.CAR, 0.90f, "Driving")
        )

        val result = MovementTimelineCanonicalizer.canonicalize(listOf(segA, segB))

        // Must repair and yield 0 canonicalizer introduced jumps
        assertEquals(0, result.diagnostics.canonicalizerIntroducedJumpsCount)
        assertEquals(1, result.canonicalSegments.size)
        assertEquals("broad_A", result.canonicalSegments[0].id)
    }
}
