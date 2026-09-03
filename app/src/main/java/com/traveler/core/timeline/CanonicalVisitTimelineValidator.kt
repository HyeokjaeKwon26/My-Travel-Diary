package com.traveler.core.timeline

import com.traveler.core.model.Visit

/**
 * Pure domain validator that enforces temporal non-overlapping invariants on canonical Visit timelines (P0-06).
 *
 * Invariants:
 * - Half-open interval semantics: [start, end)
 * - Zero overlap violations: For any consecutive pair (V_i, V_{i+1}) sorted by startTimestamp,
 *   V_i.endTimestampEpochMs <= V_{i+1}.startTimestampEpochMs must strictly hold.
 */
object CanonicalVisitTimelineValidator {

    /**
     * Counts the number of temporal overlap violations in the given visit timeline.
     */
    fun countOverlapViolations(visits: List<Visit>): Int {
        if (visits.size <= 1) return 0
        val sorted = visits.sortedBy { it.startTimestampEpochMs }
        var violations = 0
        for (i in 0 until sorted.size - 1) {
            if (sorted[i].endTimestampEpochMs > sorted[i + 1].startTimestampEpochMs) {
                violations++
            }
        }
        return violations
    }

    /**
     * Asserts that the visit timeline has zero overlap violations, canonicalizing if necessary.
     */
    fun requireNonOverlapping(visits: List<Visit>): List<Visit> {
        val count = countOverlapViolations(visits)
        if (count == 0) {
            return visits.sortedBy { it.startTimestampEpochMs }
        }
        val canonicalized = VisitCandidateCanonicalizer.deduplicate(visits)
        val remaining = countOverlapViolations(canonicalized)
        if (remaining > 0) {
            throw IllegalStateException("Visit timeline invariant violated: $remaining overlap violations remain after canonicalization.")
        }
        return canonicalized
    }
}
