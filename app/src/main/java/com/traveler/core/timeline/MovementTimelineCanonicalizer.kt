package com.traveler.core.timeline

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.model.GeometryProvenance
import com.traveler.core.model.LocationPoint
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.TransportMode
import com.traveler.core.model.TransportPrediction
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Serializable
enum class SuppressionReason {
    EXACT_DUPLICATE,
    TEMPORAL_OVERLAP_DUPLICATE,
    SAME_MODE_NESTED,
    SAME_MODE_CONFLICTING_GEOMETRY,
    CONFLICTING_MODE_LOWER_CONFIDENCE,
    NESTED_DUPLICATE,
    OUTBOUND_RETURN_SUBSET,
    DISCONTINUOUS_TELEPORT
}

@Serializable
data class SuppressedSegmentRecord(
    val segment: MovementSegment,
    val reason: SuppressionReason,
    val canonicalSegmentId: String? = null,
    val notes: String? = null
)

@Serializable
data class CanonicalTimelineDiagnostics(
    val rawSegmentsCount: Int = 0,
    val temporalOverlapGroupsCount: Int = 0,
    val sameModeNestedConflictsCount: Int = 0,
    val differentModeConflictsCount: Int = 0,
    val duplicatesSuppressedCount: Int = 0,
    val trimmedOrSplitCount: Int = 0,
    val adjacentEpisodesMergedCount: Int = 0,
    val finalCanonicalEpisodesCount: Int = 0,
    val smallContinuityConnectorsCount: Int = 0,
    val trueTimelineGapsCount: Int = 0,
    val finalOverlapViolationsCount: Int = 0,
    val spatialDiscontinuitiesCount: Int = 0,
    val spatialDiscontinuities: List<SpatialDiscontinuity> = emptyList(),
    val sourceTimelineGapsCount: Int = 0,
    val canonicalizerIntroducedJumpsCount: Int = 0,
    val repairedJumpsCount: Int = 0,
    val suspiciousBacktracksCount: Int = 0,
    val backtrackWarnings: List<String> = emptyList()
)

data class CanonicalMovementTimeline(
    val canonicalSegments: List<MovementSegment>,
    val suppressedSegments: List<SuppressedSegmentRecord>,
    val diagnostics: CanonicalTimelineDiagnostics
)

/**
 * Pure domain component that resolves raw parsed [MovementSegment]s into a canonical,
 * strictly non-overlapping, and spatially robust movement timeline (P0-01 ~ P0-10).
 *
 * Employs a Global Viterbi Dynamic Programming Optimizer (P0-03, P0-04) with deterministic jump repair:
 * 1. Partitions overlapping segments into atomic, non-overlapping temporal intervals.
 * 2. Computes exact boundary-aligned positions using `pointAtTimestamp` (raw points / cumulative polyline distance).
 * 3. Evaluates emission scores (evidence quality) and transition costs (physical distance at boundary timestamp and switch penalty).
 * 4. Backtracks the globally highest-scoring, physically continuous path through the entire overlap cluster.
 * 5. Slices polylines preserving all intermediate vertices and recomputing true geodesic distance.
 * 6. Merges adjacent compatible episodes without re-introducing overlaps.
 * 7. Evaluates and types all spatial discontinuities (Source Gaps vs Canonicalizer Jumps).
 */
object MovementTimelineCanonicalizer {

    fun canonicalize(rawSegments: List<MovementSegment>): CanonicalMovementTimeline {
        if (rawSegments.isEmpty()) {
            return CanonicalMovementTimeline(
                canonicalSegments = emptyList(),
                suppressedSegments = emptyList(),
                diagnostics = CanonicalTimelineDiagnostics()
            )
        }

        val rawSegmentsById = rawSegments.associateBy { it.id }
        val sortedRaw = rawSegments.sortedWith(
            compareBy<MovementSegment> { it.startTimestampEpochMs }
                .thenBy { it.endTimestampEpochMs }
                .thenBy { it.id }
        )

        val suppressed = ArrayList<SuppressedSegmentRecord>()
        var overlapGroupsCount = 0
        var sameModeNestedCount = 0
        var differentModeConflictCount = 0
        var duplicatesCount = 0
        var trimmedSplitCount = 0
        var repairedJumpsCount = 0

        // Step 1: Temporal Cluster Grouping
        val temporalClusters = groupIntoTemporalClusters(sortedRaw)
        val resolvedSegments = ArrayList<MovementSegment>()

        for (cluster in temporalClusters) {
            if (cluster.size > 1) {
                overlapGroupsCount++
                val resolution = resolveClusterByGlobalViterbi(cluster)
                resolvedSegments.addAll(resolution.canonical)
                suppressed.addAll(resolution.suppressed)
                sameModeNestedCount += resolution.sameModeNestedCount
                differentModeConflictCount += resolution.differentModeConflictCount
                duplicatesCount += resolution.duplicatesCount
                trimmedSplitCount += resolution.trimmedSplitCount
                repairedJumpsCount += resolution.repairedJumpsCount
            } else {
                resolvedSegments.add(cluster[0])
            }
        }

        // Sort resolved segments chronologically
        val sortedResolved = resolvedSegments.sortedWith(
            compareBy<MovementSegment> { it.startTimestampEpochMs }
                .thenBy { it.endTimestampEpochMs }
        )

        // Step 2: Merge adjacent same-movement episodes (P0-07)
        var mergedCount = 0
        val mergedEpisodes = ArrayList<MovementSegment>()

        var current: MovementSegment? = null
        for (next in sortedResolved) {
            if (current == null) {
                current = next
                continue
            }

            if (canMergeEpisodes(current, next)) {
                current = mergeTwoEpisodes(current, next)
                mergedCount++
            } else {
                mergedEpisodes.add(current)
                current = next
            }
        }
        if (current != null) {
            mergedEpisodes.add(current)
        }

        // Step 3: Enforce Hard Non-Overlapping Invariant (P0-02) and Evaluate Discontinuities (P0-01 ~ P0-06)
        val validatedEpisodes = CanonicalTimelineValidator.requireNonOverlapping(mergedEpisodes)
        val overlapViolations = CanonicalTimelineValidator.countOverlapViolations(validatedEpisodes)

        val spatialDiscontinuitiesList = ArrayList<SpatialDiscontinuity>()
        var sourceTimelineGaps = 0
        var canonicalizerJumps = 0

        for (i in 0 until validatedEpisodes.size - 1) {
            val s1 = validatedEpisodes[i]
            val s2 = validatedEpisodes[i + 1]
            val gapDist = GeodesicUtils.distanceMeters(s1.endPoint, s2.startPoint)
            val gapTimeMs = s2.startTimestampEpochMs - s1.endTimestampEpochMs

            if (gapTimeMs in 0L..1000L && gapDist > 500.0) {
                val orig1 = rawSegmentsById[s1.id] ?: rawSegmentsById[s1.id.substringBefore("_")]
                val orig2 = rawSegmentsById[s2.id] ?: rawSegmentsById[s2.id.substringBefore("_")]

                val isSourceGap = if (orig1 != null && orig2 != null && orig1.id != orig2.id) {
                    val rawGapDist = GeodesicUtils.distanceMeters(orig1.endPoint, orig2.startPoint)
                    rawGapDist > 400.0
                } else {
                    true // Independent semantic segment transition gap from source timeline
                }

                val type = if (isSourceGap) {
                    sourceTimelineGaps++
                    SpatialDiscontinuityType.SOURCE_TIMELINE_GAP
                } else {
                    canonicalizerJumps++
                    SpatialDiscontinuityType.CANONICALIZER_INTRODUCED_JUMP
                }

                val reason = if (isSourceGap) {
                    "Source Google Timeline Activities had ${gapDist.toInt()}m geographic gap between '${s1.id}' and '${s2.id}'"
                } else {
                    "Canonicalizer overlap resolution introduced ${gapDist.toInt()}m jump between '${s1.id}' and '${s2.id}'"
                }

                spatialDiscontinuitiesList.add(
                    SpatialDiscontinuity(
                        previousSegmentId = s1.id,
                        nextSegmentId = s2.id,
                        previousEndTimestamp = s1.endTimestampEpochMs,
                        nextStartTimestamp = s2.startTimestampEpochMs,
                        spatialGapMeters = gapDist,
                        temporalGapMs = gapTimeMs,
                        type = type,
                        reason = reason
                    )
                )
            }
        }

        // Step 4: Analyze genuine gaps, small continuity connectors & Backtracks (P0-07)
        var smallConnectors = 0
        var trueGaps = 0
        var backtracks = 0
        val backtrackWarnings = ArrayList<String>()

        for (i in 0 until validatedEpisodes.size - 1) {
            val s1 = validatedEpisodes[i]
            val s2 = validatedEpisodes[i + 1]
            val gapDist = GeodesicUtils.distanceMeters(s1.endPoint, s2.startPoint)
            val gapTimeMs = s2.startTimestampEpochMs - s1.endTimestampEpochMs

            if (gapDist in 1.0..500.0 && gapTimeMs in 0L..900_000L) {
                smallConnectors++
            } else if (gapDist > 5000.0 || gapTimeMs > 3_600_000L) {
                trueGaps++
            }

            val impliedSpeedMps = if (gapTimeMs > 0L) gapDist / (gapTimeMs / 1000.0) else Double.MAX_VALUE
            if (gapTimeMs in 0L..300_000L && gapDist > 1000.0 && (impliedSpeedMps > 150.0 || gapTimeMs <= 1000L)) {
                backtracks++
                backtrackWarnings.add("Suspicious jump of ${(gapDist / 1000.0).toInt()}km in ${(gapTimeMs / 1000)}s between ${s1.id} and ${s2.id}")
            }
        }

        val diagnostics = CanonicalTimelineDiagnostics(
            rawSegmentsCount = rawSegments.size,
            temporalOverlapGroupsCount = overlapGroupsCount,
            sameModeNestedConflictsCount = sameModeNestedCount,
            differentModeConflictsCount = differentModeConflictCount,
            duplicatesSuppressedCount = duplicatesCount,
            trimmedOrSplitCount = trimmedSplitCount,
            adjacentEpisodesMergedCount = mergedCount,
            finalCanonicalEpisodesCount = validatedEpisodes.size,
            smallContinuityConnectorsCount = smallConnectors,
            trueTimelineGapsCount = trueGaps,
            finalOverlapViolationsCount = overlapViolations,
            spatialDiscontinuitiesCount = spatialDiscontinuitiesList.size,
            spatialDiscontinuities = spatialDiscontinuitiesList,
            sourceTimelineGapsCount = sourceTimelineGaps,
            canonicalizerIntroducedJumpsCount = canonicalizerJumps,
            repairedJumpsCount = repairedJumpsCount,
            suspiciousBacktracksCount = backtracks,
            backtrackWarnings = backtrackWarnings
        )

        return CanonicalMovementTimeline(
            canonicalSegments = validatedEpisodes,
            suppressedSegments = suppressed,
            diagnostics = diagnostics
        )
    }

    private fun groupIntoTemporalClusters(sortedSegments: List<MovementSegment>): List<List<MovementSegment>> {
        val clusters = ArrayList<MutableList<MovementSegment>>()
        var currentCluster = ArrayList<MovementSegment>()
        var clusterEndTs = Long.MIN_VALUE

        for (seg in sortedSegments) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(seg)
                clusterEndTs = seg.endTimestampEpochMs
            } else {
                if (seg.startTimestampEpochMs < clusterEndTs) {
                    currentCluster.add(seg)
                    clusterEndTs = max(clusterEndTs, seg.endTimestampEpochMs)
                } else {
                    clusters.add(currentCluster)
                    currentCluster = ArrayList()
                    currentCluster.add(seg)
                    clusterEndTs = seg.endTimestampEpochMs
                }
            }
        }
        if (currentCluster.isNotEmpty()) {
            clusters.add(currentCluster)
        }
        return clusters
    }

    private data class ViterbiResolution(
        val canonical: List<MovementSegment>,
        val suppressed: List<SuppressedSegmentRecord>,
        val sameModeNestedCount: Int,
        val differentModeConflictCount: Int,
        val duplicatesCount: Int,
        val trimmedSplitCount: Int,
        val repairedJumpsCount: Int = 0
    )

    /**
     * Resolves an overlapping cluster into strictly non-overlapping, spatially continuous
     * canonical segments using Global Viterbi Dynamic Programming (P0-03, P0-04, P0-06).
     */
    private fun resolveClusterByGlobalViterbi(cluster: List<MovementSegment>): ViterbiResolution {
        val timestampSet = sortedSetOf<Long>()
        for (seg in cluster) {
            timestampSet.add(seg.startTimestampEpochMs)
            timestampSet.add(seg.endTimestampEpochMs)
        }
        val boundaries = timestampSet.toList()

        data class AtomicInterval(
            val startTs: Long,
            val endTs: Long,
            val candidates: List<MovementSegment>
        )

        val intervals = ArrayList<AtomicInterval>()
        for (i in 0 until boundaries.size - 1) {
            val tStart = boundaries[i]
            val tEnd = boundaries[i + 1]
            if (tStart >= tEnd) continue

            val active = cluster.filter { it.startTimestampEpochMs <= tStart && tEnd <= it.endTimestampEpochMs }
            if (active.isNotEmpty()) {
                intervals.add(AtomicInterval(tStart, tEnd, active))
            }
        }

        if (intervals.isEmpty()) {
            return ViterbiResolution(emptyList(), emptyList(), 0, 0, 0, 0, 0)
        }

        val K = intervals.size
        val dp = Array(K) { k -> DoubleArray(intervals[k].candidates.size) { Double.NEGATIVE_INFINITY } }
        val backpointer = Array(K) { k -> IntArray(intervals[k].candidates.size) { -1 } }

        for (cIdx in intervals[0].candidates.indices) {
            val cand = intervals[0].candidates[cIdx]
            dp[0][cIdx] = scoreEmission(cand, intervals[0].startTs, intervals[0].endTs)
        }

        for (k in 1 until K) {
            val prevInterval = intervals[k - 1]
            val currInterval = intervals[k]
            val boundaryTs = currInterval.startTs

            for (currIdx in currInterval.candidates.indices) {
                val currCand = currInterval.candidates[currIdx]
                val currPosAtBoundary = pointAtTimestamp(currCand, boundaryTs)
                val emission = scoreEmission(currCand, currInterval.startTs, currInterval.endTs)

                var bestScore = Double.NEGATIVE_INFINITY
                var bestPrevIdx = -1

                for (prevIdx in prevInterval.candidates.indices) {
                    val prevCand = prevInterval.candidates[prevIdx]
                    val prevPosAtBoundary = pointAtTimestamp(prevCand, boundaryTs)
                    val prevScore = dp[k - 1][prevIdx]
                    if (prevScore == Double.NEGATIVE_INFINITY) continue

                    val transitionScore = computeTransitionScore(
                        prevCand = prevCand,
                        currCand = currCand,
                        prevPosAtBoundary = prevPosAtBoundary,
                        currPosAtBoundary = currPosAtBoundary
                    )

                    val totalScore = prevScore + transitionScore + emission
                    if (totalScore > bestScore) {
                        bestScore = totalScore
                        bestPrevIdx = prevIdx
                    }
                }

                dp[k][currIdx] = bestScore
                backpointer[k][currIdx] = bestPrevIdx
            }
        }

        var bestFinalScore = Double.NEGATIVE_INFINITY
        var bestFinalIdx = -1
        for (cIdx in intervals[K - 1].candidates.indices) {
            if (dp[K - 1][cIdx] > bestFinalScore) {
                bestFinalScore = dp[K - 1][cIdx]
                bestFinalIdx = cIdx
            }
        }

        val winnerSequence = Array<MovementSegment?>(K) { null }
        var repairedJumps = 0

        val bestSingleFallback = {
            cluster.maxByOrNull { seg ->
                val spanStart = max(seg.startTimestampEpochMs, intervals.first().startTs)
                val spanEnd = min(seg.endTimestampEpochMs, intervals.last().endTs)
                if (spanEnd > spanStart) scoreEmission(seg, spanStart, spanEnd) else Double.NEGATIVE_INFINITY
            } ?: cluster.first()
        }

        if (bestFinalScore == Double.NEGATIVE_INFINITY || bestFinalIdx == -1) {
            val bestSingle = bestSingleFallback()
            for (k in 0 until K) {
                val iv = intervals[k]
                if (bestSingle.startTimestampEpochMs <= iv.startTs && iv.endTs <= bestSingle.endTimestampEpochMs) {
                    winnerSequence[k] = bestSingle
                } else {
                    winnerSequence[k] = iv.candidates.maxByOrNull { scoreEmission(it, iv.startTs, iv.endTs) } ?: iv.candidates.first()
                }
            }
            repairedJumps++
        } else {
            var currTrackIdx = bestFinalIdx
            for (k in K - 1 downTo 0) {
                winnerSequence[k] = intervals[k].candidates[currTrackIdx]
                currTrackIdx = backpointer[k][currTrackIdx]
                if (currTrackIdx == -1 && k > 0) {
                    currTrackIdx = intervals[k - 1].candidates.indices.maxByOrNull {
                        scoreEmission(intervals[k - 1].candidates[it], intervals[k - 1].startTs, intervals[k - 1].endTs)
                    } ?: 0
                }
            }

            // P0-05: Check if the backtracked path contains any internal jump > 500m between consecutive intervals
            var hasInternalJump = false
            for (k in 0 until K - 1) {
                val w1 = winnerSequence[k]
                val w2 = winnerSequence[k + 1]
                if (w1 != null && w2 != null && w1.id != w2.id) {
                    val boundaryTs = intervals[k + 1].startTs
                    val pos1 = pointAtTimestamp(w1, boundaryTs)
                    val pos2 = pointAtTimestamp(w2, boundaryTs)
                    if (GeodesicUtils.distanceMeters(pos1, pos2) > 500.0) {
                        hasInternalJump = true
                        break
                    }
                }
            }

            if (hasInternalJump) {
                // Repair by falling back to the single highest-scoring continuous candidate across cluster
                val bestSingle = bestSingleFallback()
                for (k in 0 until K) {
                    val iv = intervals[k]
                    if (bestSingle.startTimestampEpochMs <= iv.startTs && iv.endTs <= bestSingle.endTimestampEpochMs) {
                        winnerSequence[k] = bestSingle
                    } else {
                        winnerSequence[k] = iv.candidates.maxByOrNull { scoreEmission(it, iv.startTs, iv.endTs) } ?: iv.candidates.first()
                    }
                }
                repairedJumps++
            }
        }

        var sameModeNested = 0
        var diffModeConflict = 0
        for (k in 0 until K) {
            val winner = winnerSequence[k] ?: continue
            for (cand in intervals[k].candidates) {
                if (cand.id != winner.id) {
                    if (cand.effectiveMode == winner.effectiveMode) {
                        sameModeNested++
                    } else {
                        diffModeConflict++
                    }
                }
            }
        }

        data class ContiguousSpan(
            val startTs: Long,
            val endTs: Long,
            val originalSegment: MovementSegment
        )

        val stitchedSpans = ArrayList<ContiguousSpan>()
        var currentSpan: ContiguousSpan? = null

        for (k in 0 until K) {
            val iv = intervals[k]
            val winner = winnerSequence[k] ?: continue

            if (currentSpan == null) {
                currentSpan = ContiguousSpan(iv.startTs, iv.endTs, winner)
            } else if (currentSpan.originalSegment.id == winner.id && currentSpan.endTs == iv.startTs) {
                currentSpan = currentSpan.copy(endTs = iv.endTs)
            } else {
                stitchedSpans.add(currentSpan)
                currentSpan = ContiguousSpan(iv.startTs, iv.endTs, winner)
            }
        }
        if (currentSpan != null) {
            stitchedSpans.add(currentSpan)
        }

        val canonicalSegments = ArrayList<MovementSegment>()
        var trimmedSplit = 0

        for (span in stitchedSpans) {
            val orig = span.originalSegment
            if (span.startTs == orig.startTimestampEpochMs && span.endTs == orig.endTimestampEpochMs) {
                canonicalSegments.add(orig)
            } else {
                val sliced = sliceSegmentWithIntermediateVertices(orig, span.startTs, span.endTs)
                if (sliced != null) {
                    canonicalSegments.add(sliced)
                    trimmedSplit++
                }
            }
        }

        val suppressed = ArrayList<SuppressedSegmentRecord>()
        val originalWinnerIds = stitchedSpans.map { it.originalSegment.id }.toSet()
        var duplicates = 0

        for (seg in cluster) {
            if (seg.id !in originalWinnerIds) {
                val winnerCandidate = canonicalSegments.firstOrNull()?.id
                val sameModeExists = cluster.any { it.id != seg.id && it.effectiveMode == seg.effectiveMode && it.id in originalWinnerIds }
                val reason = when {
                    sameModeExists -> SuppressionReason.SAME_MODE_NESTED
                    else -> SuppressionReason.CONFLICTING_MODE_LOWER_CONFIDENCE
                }
                suppressed.add(
                    SuppressedSegmentRecord(
                        segment = seg,
                        reason = reason,
                        canonicalSegmentId = winnerCandidate,
                        notes = "Global Viterbi optimizer selected higher evidence/spatially continuous segment(s) [${originalWinnerIds.joinToString()}]"
                    )
                )
                duplicates++
            }
        }

        return ViterbiResolution(
            canonical = canonicalSegments,
            suppressed = suppressed,
            sameModeNestedCount = sameModeNested,
            differentModeConflictCount = diffModeConflict,
            duplicatesCount = duplicates,
            trimmedSplitCount = trimmedSplit,
            repairedJumpsCount = repairedJumps
        )
    }

    private fun scoreEmission(segment: MovementSegment, startTs: Long, endTs: Long): Double {
        val durationRatio = (endTs - startTs).toDouble() / max(1.0, segment.durationMillis.toDouble())
        val baseScore = when (segment.geometryProvenance) {
            GeometryProvenance.OBSERVED -> 100.0
            GeometryProvenance.SIMPLIFIED_OBSERVED -> 80.0
            GeometryProvenance.ESTIMATED_GEODESIC -> 60.0
            GeometryProvenance.ENDPOINT_INTERPOLATED -> 30.0
            GeometryProvenance.CONTINUITY_ESTIMATE -> 20.0
            GeometryProvenance.UNKNOWN -> 10.0
        }

        val confMultiplier = if (segment.isUserOverride) 1.5 else segment.transport.confidence.toDouble()
        val polylineBonus = if (segment.simplifiedPoints.size > 2) 20.0 else 0.0
        val rawBonus = if (segment.rawPoints.isNotEmpty()) 15.0 else 0.0

        return (baseScore * confMultiplier + polylineBonus + rawBonus) * durationRatio
    }

    private fun computeTransitionScore(
        prevCand: MovementSegment,
        currCand: MovementSegment,
        prevPosAtBoundary: GeoPoint,
        currPosAtBoundary: GeoPoint
    ): Double {
        if (prevCand.id == currCand.id) {
            return 0.0
        }

        val boundaryDistMeters = GeodesicUtils.distanceMeters(prevPosAtBoundary, currPosAtBoundary)

        if (boundaryDistMeters > 1500.0) {
            return Double.NEGATIVE_INFINITY
        }

        val modeSwitchPenalty = if (prevCand.effectiveMode != currCand.effectiveMode) -40.0 else -15.0
        val distancePenalty = -0.05 * boundaryDistMeters

        return modeSwitchPenalty + distancePenalty
    }

    fun pointAtTimestamp(segment: MovementSegment, targetTs: Long): GeoPoint {
        if (targetTs <= segment.startTimestampEpochMs) return segment.startPoint
        if (targetTs >= segment.endTimestampEpochMs) return segment.endPoint
        val duration = segment.endTimestampEpochMs - segment.startTimestampEpochMs
        if (duration <= 0L) return segment.startPoint

        if (segment.rawPoints.size >= 2) {
            val sorted = segment.rawPoints.sortedBy { it.timestampEpochMs }
            if (targetTs <= sorted.first().timestampEpochMs) return sorted.first().coordinate
            if (targetTs >= sorted.last().timestampEpochMs) return sorted.last().coordinate

            for (i in 0 until sorted.size - 1) {
                val p1 = sorted[i]
                val p2 = sorted[i + 1]
                if (targetTs in p1.timestampEpochMs..p2.timestampEpochMs) {
                    val dt = p2.timestampEpochMs - p1.timestampEpochMs
                    val frac = if (dt > 0) (targetTs - p1.timestampEpochMs).toDouble() / dt.toDouble() else 0.0
                    return GeodesicUtils.interpolate(p1.coordinate, p2.coordinate, frac.coerceIn(0.0, 1.0))
                }
            }
        }

        val fraction = (targetTs - segment.startTimestampEpochMs).toDouble() / duration.toDouble()
        val polyline = if (segment.simplifiedPoints.size >= 2) segment.simplifiedPoints else listOf(segment.startPoint, segment.endPoint)
        return interpolatePointAlongPath(polyline, fraction.coerceIn(0.0, 1.0))
    }

    fun sliceSegmentWithIntermediateVertices(
        segment: MovementSegment,
        startTs: Long,
        endTs: Long
    ): MovementSegment? {
        val clampedStartTs = max(segment.startTimestampEpochMs, startTs)
        val clampedEndTs = min(segment.endTimestampEpochMs, endTs)
        if (clampedEndTs <= clampedStartTs) return null

        val exactStartPoint = pointAtTimestamp(segment, clampedStartTs)
        val exactEndPoint = pointAtTimestamp(segment, clampedEndTs)

        val slicedRawPoints = ArrayList<LocationPoint>()
        if (segment.rawPoints.isNotEmpty()) {
            val inner = segment.rawPoints.filter { it.timestampEpochMs in clampedStartTs..clampedEndTs }
            if (inner.isEmpty() || inner.first().timestampEpochMs > clampedStartTs) {
                slicedRawPoints.add(LocationPoint("${segment.id}_slice_start", clampedStartTs, exactStartPoint))
            }
            slicedRawPoints.addAll(inner)
            if (inner.isEmpty() || inner.last().timestampEpochMs < clampedEndTs) {
                slicedRawPoints.add(LocationPoint("${segment.id}_slice_end", clampedEndTs, exactEndPoint))
            }
        }

        val sourcePolyline = if (segment.simplifiedPoints.size >= 2) segment.simplifiedPoints else listOf(segment.startPoint, segment.endPoint)
        val slicedSimplifiedPoints = slicePolylineIntermediate(
            points = sourcePolyline,
            totalStartTs = segment.startTimestampEpochMs,
            totalEndTs = segment.endTimestampEpochMs,
            sliceStartTs = clampedStartTs,
            sliceEndTs = clampedEndTs,
            pStart = exactStartPoint,
            pEnd = exactEndPoint
        )

        val trueDistanceMeters = GeodesicUtils.pathDistanceMeters(slicedSimplifiedPoints)
        val durationMs = clampedEndTs - clampedStartTs

        val subId = "${segment.id}_sub_${clampedStartTs}_${clampedEndTs}"

        return MovementSegment(
            id = subId,
            startTimestampEpochMs = clampedStartTs,
            endTimestampEpochMs = clampedEndTs,
            startPoint = exactStartPoint,
            endPoint = exactEndPoint,
            rawPoints = slicedRawPoints,
            simplifiedPoints = slicedSimplifiedPoints,
            distanceMeters = trueDistanceMeters,
            durationMillis = durationMs,
            transport = segment.transport,
            startTimezoneId = segment.startTimezoneId,
            endTimezoneId = segment.endTimezoneId,
            userOverrideMode = segment.userOverrideMode,
            isUserOverride = segment.isUserOverride,
            geometryProvenance = segment.geometryProvenance
        )
    }

    fun slicePolylineByFraction(points: List<GeoPoint>, startFraction: Double, endFraction: Double): List<GeoPoint> {
        if (points.size <= 1) return points
        val fStart = startFraction.coerceIn(0.0, 1.0)
        val fEnd = endFraction.coerceIn(0.0, 1.0)
        if (fEnd <= fStart) return listOf(interpolatePointAlongPath(points, fStart))

        val pStart = interpolatePointAlongPath(points, fStart)
        val pEnd = interpolatePointAlongPath(points, fEnd)

        return slicePolylineIntermediate(
            points = points,
            totalStartTs = 0L,
            totalEndTs = 1000L,
            sliceStartTs = (1000.0 * fStart).toLong(),
            sliceEndTs = (1000.0 * fEnd).toLong(),
            pStart = pStart,
            pEnd = pEnd
        )
    }

    private fun slicePolylineIntermediate(
        points: List<GeoPoint>,
        totalStartTs: Long,
        totalEndTs: Long,
        sliceStartTs: Long,
        sliceEndTs: Long,
        pStart: GeoPoint,
        pEnd: GeoPoint
    ): List<GeoPoint> {
        if (points.size <= 1) {
            return listOf(pStart, pEnd)
        }

        val totalDuration = max(1L, totalEndTs - totalStartTs).toDouble()
        val fracStart = ((sliceStartTs - totalStartTs) / totalDuration).coerceIn(0.0, 1.0)
        val fracEnd = ((sliceEndTs - totalStartTs) / totalDuration).coerceIn(0.0, 1.0)

        val cumDists = ArrayList<Double>(points.size)
        cumDists.add(0.0)
        var totalDist = 0.0
        for (i in 1 until points.size) {
            totalDist += GeodesicUtils.distanceMeters(points[i - 1], points[i])
            cumDists.add(totalDist)
        }

        val targetDistStart = totalDist * fracStart
        val targetDistEnd = totalDist * fracEnd

        val result = ArrayList<GeoPoint>()
        result.add(pStart)

        for (i in points.indices) {
            val d = cumDists[i]
            if (d > targetDistStart + 1.0 && d < targetDistEnd - 1.0) {
                result.add(points[i])
            }
        }

        if (result.last() != pEnd) {
            result.add(pEnd)
        }

        return result
    }

    private fun interpolatePointAtDistance(points: List<GeoPoint>, cumDists: List<Double>, targetDist: Double): GeoPoint {
        if (targetDist <= 0.0) return points.first()
        if (targetDist >= cumDists.last()) return points.last()

        var idx = 0
        while (idx < cumDists.size - 1 && cumDists[idx + 1] < targetDist) {
            idx++
        }

        val p1 = points[idx]
        val p2 = points[min(idx + 1, points.size - 1)]
        val segDist = cumDists[min(idx + 1, points.size - 1)] - cumDists[idx]
        val frac = if (segDist > 0.0) ((targetDist - cumDists[idx]) / segDist).coerceIn(0.0, 1.0) else 0.0
        return GeodesicUtils.interpolate(p1, p2, frac)
    }

    private fun interpolatePointAlongPath(path: List<GeoPoint>, fraction: Double): GeoPoint {
        if (path.isEmpty()) return GeoPoint(0.0, 0.0)
        if (path.size == 1 || fraction <= 0.0) return path.first()
        if (fraction >= 1.0) return path.last()

        val totalDists = ArrayList<Double>()
        totalDists.add(0.0)
        var sum = 0.0
        for (i in 1 until path.size) {
            sum += GeodesicUtils.distanceMeters(path[i - 1], path[i])
            totalDists.add(sum)
        }

        val targetDist = sum * fraction
        return interpolatePointAtDistance(path, totalDists, targetDist)
    }

    private fun canMergeEpisodes(s1: MovementSegment, s2: MovementSegment): Boolean {
        if (s1.effectiveMode != s2.effectiveMode) return false

        val timeGapMs = s2.startTimestampEpochMs - s1.endTimestampEpochMs
        if (timeGapMs < 0L || timeGapMs > 900_000L) return false

        val endpointDistMeters = GeodesicUtils.distanceMeters(s1.endPoint, s2.startPoint)
        if (endpointDistMeters > 500.0) return false

        if (s1.effectiveMode == TransportMode.AIRPLANE) return false

        return true
    }

    private fun mergeTwoEpisodes(s1: MovementSegment, s2: MovementSegment): MovementSegment {
        val mergedRaw = ArrayList<LocationPoint>(s1.rawPoints.size + s2.rawPoints.size)
        mergedRaw.addAll(s1.rawPoints)
        mergedRaw.addAll(s2.rawPoints)

        val mergedSimplified = ArrayList<GeoPoint>()
        if (s1.simplifiedPoints.isNotEmpty()) {
            mergedSimplified.addAll(s1.simplifiedPoints)
        } else {
            mergedSimplified.add(s1.startPoint)
            mergedSimplified.add(s1.endPoint)
        }

        if (s2.simplifiedPoints.isNotEmpty()) {
            if (mergedSimplified.isNotEmpty() && s2.simplifiedPoints.isNotEmpty() &&
                mergedSimplified.last() == s2.simplifiedPoints.first()) {
                mergedSimplified.addAll(s2.simplifiedPoints.subList(1, s2.simplifiedPoints.size))
            } else {
                mergedSimplified.addAll(s2.simplifiedPoints)
            }
        } else {
            mergedSimplified.add(s2.startPoint)
            mergedSimplified.add(s2.endPoint)
        }

        val totalDistance = s1.distanceMeters + s2.distanceMeters + GeodesicUtils.distanceMeters(s1.endPoint, s2.startPoint)
        val totalDuration = s2.endTimestampEpochMs - s1.startTimestampEpochMs

        val bestProvenance = when {
            s1.geometryProvenance == GeometryProvenance.OBSERVED && s2.geometryProvenance == GeometryProvenance.OBSERVED -> GeometryProvenance.OBSERVED
            s1.geometryProvenance == GeometryProvenance.OBSERVED || s2.geometryProvenance == GeometryProvenance.OBSERVED -> GeometryProvenance.SIMPLIFIED_OBSERVED
            s1.geometryProvenance == GeometryProvenance.SIMPLIFIED_OBSERVED || s2.geometryProvenance == GeometryProvenance.SIMPLIFIED_OBSERVED -> GeometryProvenance.SIMPLIFIED_OBSERVED
            else -> GeometryProvenance.ENDPOINT_INTERPOLATED
        }

        val avgConfidence = (s1.transport.confidence + s2.transport.confidence) * 0.5f

        return MovementSegment(
            id = "${s1.id}_${s2.id}",
            startTimestampEpochMs = s1.startTimestampEpochMs,
            endTimestampEpochMs = s2.endTimestampEpochMs,
            startPoint = s1.startPoint,
            endPoint = s2.endPoint,
            rawPoints = mergedRaw,
            simplifiedPoints = mergedSimplified,
            distanceMeters = totalDistance,
            durationMillis = totalDuration,
            transport = TransportPrediction(s1.effectiveMode, avgConfidence, s1.transport.reason),
            startTimezoneId = s1.startTimezoneId,
            endTimezoneId = s2.endTimezoneId,
            userOverrideMode = s1.userOverrideMode ?: s2.userOverrideMode,
            isUserOverride = s1.isUserOverride || s2.isUserOverride,
            geometryProvenance = bestProvenance
        )
    }
}
