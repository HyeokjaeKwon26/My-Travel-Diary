package com.traveler.core.timeline

import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.model.MovementSegment
import kotlinx.serialization.Serializable

@Serializable
enum class SpatialDiscontinuityType {
    SOURCE_TIMELINE_GAP,
    CANONICALIZER_INTRODUCED_JUMP,
    EXPECTED_VISIT_TRANSITION,
    UNKNOWN_DATA_GAP
}

@Serializable
data class SpatialDiscontinuity(
    val previousSegmentId: String,
    val nextSegmentId: String,
    val previousEndTimestamp: Long,
    val nextStartTimestamp: Long,
    val spatialGapMeters: Double,
    val temporalGapMs: Long,
    val type: SpatialDiscontinuityType,
    val reason: String
)

/**
 * Pure domain validator that mechanically enforces the hard non-overlapping timeline invariant (P0-02)
 * and evaluates spatial continuity at transitions (P0-01 ~ P0-06).
 *
 * Invariants:
 * 1. Temporal: For any sorted list of canonical [MovementSegment]s:
 *    `previous.endTimestampEpochMs <= next.startTimestampEpochMs`
 * 2. Playback Exclusivity: At any wall-clock instant [startTimestampEpochMs, endTimestampEpochMs),
 *    there is AT MOST 1 active canonical movement episode.
 * 3. Spatial Robustness: Spatial gaps that existed in the source timeline are classified as [SpatialDiscontinuityType.SOURCE_TIMELINE_GAP]
 *    and do NOT abort Trip creation. Only unrepairable [SpatialDiscontinuityType.CANONICALIZER_INTRODUCED_JUMP] violations indicate algorithm failure.
 */
object CanonicalTimelineValidator {

    /**
     * Asserts that [segments] sorted by time are strictly non-overlapping.
     * Throws [IllegalStateException] if an overlap is detected.
     * Returns the validated list if compliant.
     */
    fun requireNonOverlapping(segments: List<MovementSegment>): List<MovementSegment> {
        val violations = findOverlapViolations(segments)
        if (violations.isNotEmpty()) {
            val first = violations.first()
            throw IllegalStateException(
                "Canonical timeline invariant violated! Overlap of ${first.overlapDurationMs}ms detected between " +
                "segment '${first.previousSegment.id}' (${first.previousSegment.startTimestampEpochMs}..${first.previousSegment.endTimestampEpochMs}) and " +
                "segment '${first.nextSegment.id}' (${first.nextSegment.startTimestampEpochMs}..${first.nextSegment.endTimestampEpochMs})"
            )
        }
        return segments
    }

    data class OverlapViolation(
        val previousSegment: MovementSegment,
        val nextSegment: MovementSegment,
        val overlapDurationMs: Long
    )

    /**
     * Finds all overlapping pairs in a chronological segment list.
     */
    fun findOverlapViolations(segments: List<MovementSegment>): List<OverlapViolation> {
        if (segments.size <= 1) return emptyList()

        val sorted = segments.sortedWith(
            compareBy<MovementSegment> { it.startTimestampEpochMs }
                .thenBy { it.endTimestampEpochMs }
                .thenBy { it.id }
        )

        val violations = ArrayList<OverlapViolation>()
        for (i in 0 until sorted.size - 1) {
            val prev = sorted[i]
            val next = sorted[i + 1]
            if (prev.endTimestampEpochMs > next.startTimestampEpochMs) {
                val overlap = prev.endTimestampEpochMs - next.startTimestampEpochMs
                violations.add(
                    OverlapViolation(
                        previousSegment = prev,
                        nextSegment = next,
                        overlapDurationMs = overlap
                    )
                )
            }
        }
        return violations
    }

    /**
     * Returns the count of overlap violations in [segments].
     */
    fun countOverlapViolations(segments: List<MovementSegment>): Int {
        return findOverlapViolations(segments).size
    }

    data class SpatialDiscontinuityViolation(
        val previousSegment: MovementSegment,
        val nextSegment: MovementSegment,
        val timeGapMs: Long,
        val distanceMeters: Double
    )

    /**
     * Finds spatial discontinuities where time gap is near zero (<= 1000ms)
     * but physical distance exceeds [maxAcceptableJumpMeters] (P0-01, P0-05).
     */
    fun findSpatialDiscontinuities(
        segments: List<MovementSegment>,
        maxAcceptableJumpMeters: Double = 500.0
    ): List<SpatialDiscontinuityViolation> {
        if (segments.size <= 1) return emptyList()

        val sorted = segments.sortedBy { it.startTimestampEpochMs }
        val violations = ArrayList<SpatialDiscontinuityViolation>()

        for (i in 0 until sorted.size - 1) {
            val prev = sorted[i]
            val next = sorted[i + 1]
            val timeGapMs = next.startTimestampEpochMs - prev.endTimestampEpochMs
            if (timeGapMs in 0L..1000L) {
                val dist = GeodesicUtils.distanceMeters(prev.endPoint, next.startPoint)
                if (dist > maxAcceptableJumpMeters) {
                    violations.add(
                        SpatialDiscontinuityViolation(
                            previousSegment = prev,
                            nextSegment = next,
                            timeGapMs = timeGapMs,
                            distanceMeters = dist
                        )
                    )
                }
            }
        }
        return violations
    }

    /**
     * Validates that at any arbitrary instant [timestampEpochMs], there is at most 1 active segment,
     * using half-open canonical intervals [startTimestampEpochMs, endTimestampEpochMs) (P0-08).
     */
    fun countActiveSegmentsAt(segments: List<MovementSegment>, timestampEpochMs: Long): Int {
        if (segments.isEmpty()) return 0
        val maxEnd = segments.maxOf { it.endTimestampEpochMs }

        return segments.count { isSegmentActiveAt(it, timestampEpochMs, maxEnd) }
    }

    /**
     * Standardized half-open interval [startTimestampEpochMs, endTimestampEpochMs) evaluation (P0-04).
     * Used uniformly by validator, renderer, and story compressor.
     * At the final boundary instant [maxEndEpochMs], allows the final segment to match at its exact end.
     */
    fun isSegmentActiveAt(
        segment: MovementSegment,
        timestampEpochMs: Long,
        maxEndEpochMs: Long? = null
    ): Boolean {
        val sStart = segment.startTimestampEpochMs
        val sEnd = segment.endTimestampEpochMs
        if (maxEndEpochMs != null && timestampEpochMs >= maxEndEpochMs) {
            return sEnd == maxEndEpochMs
        }
        return sStart <= timestampEpochMs && timestampEpochMs < sEnd
    }
}
