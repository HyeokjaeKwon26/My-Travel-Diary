package com.traveler.core.timeline

import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.model.Visit
import kotlin.math.abs

/**
 * Pure domain component that canonicalizes duplicate or alternative Visit candidates (P0-01 ~ P0-06).
 *
 * Modern Google Timeline exports frequently contain multiple Visit semantic segments with identical,
 * near-identical, or nested time intervals representing competing place hypotheses for the same physical dwell.
 *
 * Requirements:
 * 1. Substantial Containment & Overlap: Alternative candidates where intersection covers >= 90% of the
 *    shorter interval or temporal overlap with local proximity (<= 500m) are merged.
 * 2. Outer Union Dwell Interval: The merged canonical visit preserves the full dwell coverage [min(start), max(end)].
 * 3. Best-Supported Place Identity: Metadata (name, placeId, location) is adopted from the best-supported hypothesis.
 * 4. Hard Non-Overlapping Invariant: For one traveler, the canonical visit timeline must never contain
 *    two simultaneous physical visits (prev.endTimestamp <= next.startTimestamp).
 */
object VisitCandidateCanonicalizer {

    private const val MAX_START_END_DELTA_MS = 10_000L // 10s tolerance for near-identical intervals
    private const val LOCAL_PROXIMITY_METERS = 500.0   // 500m proximity for co-located dwell

    fun deduplicate(rawVisits: List<Visit>): List<Visit> {
        if (rawVisits.size <= 1) return rawVisits

        val sorted = rawVisits.sortedWith(
            compareBy<Visit> { it.startTimestampEpochMs }
                .thenBy { it.endTimestampEpochMs }
                .thenByDescending { it.confidence }
                .thenBy { it.id }
        )

        // Phase 1: Cluster alternative place hypotheses & nested dwells
        val clusters = mutableListOf<MutableList<Visit>>()
        for (visit in sorted) {
            var matchedCluster: MutableList<Visit>? = null
            for (cluster in clusters) {
                if (cluster.any { isAlternativeCandidate(it, visit) }) {
                    matchedCluster = cluster
                    break
                }
            }
            if (matchedCluster != null) {
                matchedCluster.add(visit)
            } else {
                clusters.add(mutableListOf(visit))
            }
        }

        val mergedVisits = clusters.map { mergeCluster(it) }.sortedBy { it.startTimestampEpochMs }

        // Phase 2: Enforce hard non-overlapping invariant across all consecutive visits (P0-06)
        return resolveRemainingOverlaps(mergedVisits)
    }

    private fun isAlternativeCandidate(a: Visit, b: Visit): Boolean {
        val startDelta = abs(a.startTimestampEpochMs - b.startTimestampEpochMs)
        val endDelta = abs(a.endTimestampEpochMs - b.endTimestampEpochMs)

        if (startDelta <= MAX_START_END_DELTA_MS && endDelta <= MAX_START_END_DELTA_MS) {
            return true
        }

        val overlapStart = maxOf(a.startTimestampEpochMs, b.startTimestampEpochMs)
        val overlapEnd = minOf(a.endTimestampEpochMs, b.endTimestampEpochMs)
        val overlapDuration = overlapEnd - overlapStart

        if (overlapDuration <= 0L) return false

        val durA = maxOf(1L, a.endTimestampEpochMs - a.startTimestampEpochMs)
        val durB = maxOf(1L, b.endTimestampEpochMs - b.startTimestampEpochMs)
        val minDur = minOf(durA, durB)
        val overlapRatioShorter = overlapDuration.toDouble() / minDur.toDouble()

        val distanceM = GeodesicUtils.distanceMeters(a.location, b.location)

        // Case 1: Substantial containment (>= 90% of shorter interval) with reasonable vicinity (<= 1500m)
        if (overlapRatioShorter >= 0.90 && distanceM <= 1500.0) {
            return true
        }

        // Case 2: Temporal overlap with close local proximity (<= 500m)
        if (overlapRatioShorter >= 0.50 && distanceM <= LOCAL_PROXIMITY_METERS) {
            return true
        }

        // Case 3: Overlapping dwell >= 10 minutes within 500m
        if (overlapDuration >= 600_000L && distanceM <= LOCAL_PROXIMITY_METERS) {
            return true
        }

        return false
    }

    private fun mergeCluster(candidates: List<Visit>): Visit {
        if (candidates.size == 1) return candidates.first()

        val unionStart = candidates.minOf { it.startTimestampEpochMs }
        val unionEnd = candidates.maxOf { it.endTimestampEpochMs }

        val bestCandidate = candidates.maxWithOrNull(
            compareBy<Visit> { if (it.isUserOverride) 1 else 0 }
                .thenBy { if (!it.placeName.isNullOrBlank() && !it.placeName.equals("UNKNOWN", ignoreCase = true)) 1 else 0 }
                .thenBy { if (!it.placeId.isNullOrBlank()) 1 else 0 }
                .thenBy { it.confidence }
                .thenBy { it.endTimestampEpochMs - it.startTimestampEpochMs }
                .thenBy { it.id }
        ) ?: candidates.first()

        val hasUserOverride = candidates.any { it.isUserOverride }

        return bestCandidate.copy(
            startTimestampEpochMs = unionStart,
            endTimestampEpochMs = unionEnd,
            isUserOverride = hasUserOverride
        )
    }

    /**
     * Resolves any remaining temporal overlaps between consecutive visits to ensure
     * strictly non-overlapping timeline semantics: previous.endTimestamp <= next.startTimestamp.
     */
    private fun resolveRemainingOverlaps(visits: List<Visit>): List<Visit> {
        if (visits.size <= 1) return visits

        val result = mutableListOf<Visit>()
        for (v in visits) {
            if (result.isEmpty()) {
                result.add(v)
            } else {
                val prev = result.last()
                if (prev.endTimestampEpochMs > v.startTimestampEpochMs) {
                    // Overlap detected between distinct visits
                    val dist = GeodesicUtils.distanceMeters(prev.location, v.location)
                    if (dist <= LOCAL_PROXIMITY_METERS) {
                        // Co-located: Merge into one visit with union dwell
                        result.removeAt(result.size - 1)
                        result.add(mergeCluster(listOf(prev, v)))
                    } else {
                        // Spatially distinct (> 500m): Decide based on evidence or clamp partition boundary
                        val prevScore = (if (prev.isUserOverride) 100f else 0f) + (if (!prev.placeName.isNullOrBlank()) 10f else 0f) + prev.confidence
                        val vScore = (if (v.isUserOverride) 100f else 0f) + (if (!v.placeName.isNullOrBlank()) 10f else 0f) + v.confidence

                        if (v.endTimestampEpochMs <= prev.endTimestampEpochMs && prevScore >= vScore + 0.2f) {
                            // Subordinate nested visit with much lower score: suppress
                            continue
                        } else if (prev.startTimestampEpochMs >= v.startTimestampEpochMs && vScore >= prevScore + 0.2f) {
                            // Current visit dominates previous: replace previous
                            result.removeAt(result.size - 1)
                            result.add(v)
                        } else {
                            // Partition at midpoint or clamped boundary
                            val splitTime = maxOf(prev.startTimestampEpochMs + 1000L, minOf(prev.endTimestampEpochMs, (prev.endTimestampEpochMs + v.startTimestampEpochMs) / 2))
                            val adjustedPrev = prev.copy(endTimestampEpochMs = splitTime)
                            val adjustedCurr = v.copy(startTimestampEpochMs = maxOf(splitTime, v.startTimestampEpochMs))

                            result[result.size - 1] = adjustedPrev
                            if (adjustedCurr.endTimestampEpochMs > adjustedCurr.startTimestampEpochMs) {
                                result.add(adjustedCurr)
                            }
                        }
                    }
                } else {
                    result.add(v)
                }
            }
        }

        return result.sortedBy { it.startTimestampEpochMs }
    }
}
