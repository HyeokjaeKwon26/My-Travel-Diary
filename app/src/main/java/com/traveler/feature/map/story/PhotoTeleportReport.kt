package com.traveler.feature.map.story

/**
 * Diagnostic report from [TravelStoryTimeline.validateInvariants] (P0-05 ~ P0-08).
 *
 * All counts must be 0 for a valid cinematic playback.
 */
data class PhotoTeleportReport(
    /** Number of times a photo's active episode at its display midpoint differs from its parentId */
    val completedEpisodeReactivationCount: Int,
    /** Number of photos whose display window extends outside their parent episode's story interval */
    val outOfBoundsPhotoWindowCount: Int,
    /** Number of photos whose parentId doesn't match any episode */
    val photoParentMismatchCount: Int,
    /** Total photo moments in the timeline */
    val totalPhotoMoments: Int,
    /** Per-photo diagnostic details (for debugging) */
    val details: List<PhotoMomentDiagnostic> = emptyList()
) {
    val isClean: Boolean
        get() = completedEpisodeReactivationCount == 0 &&
                outOfBoundsPhotoWindowCount == 0 &&
                photoParentMismatchCount == 0
}

/**
 * Diagnostic detail for a single photo moment.
 */
data class PhotoMomentDiagnostic(
    val mediaId: String,
    val parentId: String,
    val parentType: String,
    val parentEpisodeIndex: Int,
    val displayStartStorySeconds: Float,
    val displayEndStorySeconds: Float,
    val parentEpisodeStoryStart: Float,
    val parentEpisodeStoryEnd: Float,
    val isOutOfBounds: Boolean,
    val isEpisodeMismatch: Boolean
)
