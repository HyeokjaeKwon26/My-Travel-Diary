package com.traveler.feature.map.story

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.feature.map.renderer.TravelPlaybackState

/**
 * Lightweight runtime diagnostic that detects if playback progress, episode index,
 * or map position ever jumps backward during continuous playback (without user scrubbing).
 */
class PlaybackContinuityDiagnostic {
    var playbackProgressBackwardCount: Int = 0
        private set
    var episodeIndexBackwardCount: Int = 0
        private set
    var completedEpisodeReactivationCount: Int = 0
        private set
    var spatialJumpCount: Int = 0
        private set
    var unresolvedCrossTypeOverlapCount: Int = 0
        private set
    var backwardEpisodeChronologyCount: Int = 0
        private set
    var overlapGeneratedBridgeCount: Int = 0
        private set

    private var previousProgress: Float = -1.0f
    private var previousEpisodeIndex: Int = -1
    private var previousPosition: GeoPoint? = null
    private var previousStoryTimeMs: Long = -1L
    private val completedEpisodes = mutableSetOf<Int>()

    var lastAnomalyDescription: String? = null
        private set

    fun recordTimelineDiagnostics(diagnostics: TimelineStoryDiagnostics) {
        unresolvedCrossTypeOverlapCount = diagnostics.unresolvedCrossTypeOverlapCount
        backwardEpisodeChronologyCount = diagnostics.backwardEpisodeChronologyCount
        overlapGeneratedBridgeCount = diagnostics.overlapGeneratedBridgeCount
    }

    fun reset() {
        playbackProgressBackwardCount = 0
        episodeIndexBackwardCount = 0
        completedEpisodeReactivationCount = 0
        spatialJumpCount = 0
        unresolvedCrossTypeOverlapCount = 0
        backwardEpisodeChronologyCount = 0
        overlapGeneratedBridgeCount = 0
        previousProgress = -1.0f
        previousEpisodeIndex = -1
        previousPosition = null
        previousStoryTimeMs = -1L
        completedEpisodes.clear()
        lastAnomalyDescription = null
    }

    /**
     * Called on each rendered frame during continuous playback.
     */
    fun recordFrame(state: TravelPlaybackState, isUserScrubbing: Boolean = false) {
        if (isUserScrubbing) {
            previousProgress = state.progress
            previousEpisodeIndex = state.episodeIndex
            previousPosition = state.currentPosition
            previousStoryTimeMs = state.storyTimeMs
            return
        }

        if (previousProgress >= 0f) {
            // 1. Check progress monotonicity
            if (state.progress < previousProgress - 0.0001f) {
                playbackProgressBackwardCount++
                lastAnomalyDescription = "violation: prevProgress=$previousProgress currProgress=${state.progress} prevStoryTimeMs=$previousStoryTimeMs currStoryTimeMs=${state.storyTimeMs}"
            }

            // 2. Check episodeIndex monotonicity
            if (state.episodeIndex >= 0 && previousEpisodeIndex >= 0 && state.episodeIndex < previousEpisodeIndex) {
                episodeIndexBackwardCount++
                lastAnomalyDescription = "violation: prevEp=$previousEpisodeIndex currEp=${state.episodeIndex} prevStoryTimeMs=$previousStoryTimeMs currStoryTimeMs=${state.storyTimeMs}"
            }

            // 3. Check completed episode reactivation (P0-05)
            if (state.episodeIndex >= 0 && state.episodeIndex in completedEpisodes) {
                completedEpisodeReactivationCount++
                lastAnomalyDescription = "violation: completedEpReactivation ep=${state.episodeIndex} prevEp=$previousEpisodeIndex prevStoryTimeMs=$previousStoryTimeMs currStoryTimeMs=${state.storyTimeMs}"
            }

            // 4. Spatial jump check if episodeIndex is monotonic but position jumps > 200km in a single frame
            val prevPos = previousPosition
            if (prevPos != null && state.episodeIndex >= previousEpisodeIndex) {
                val jumpDist = GeodesicUtils.distanceMeters(prevPos, state.currentPosition)
                if (jumpDist > 200_000.0) {
                    spatialJumpCount++
                    lastAnomalyDescription = "Spatial jump of ${jumpDist / 1000.0} km from $prevPos to ${state.currentPosition} (ep $previousEpisodeIndex -> ${state.episodeIndex})"
                }
            }
        }

        if (previousEpisodeIndex >= 0 && state.episodeIndex > previousEpisodeIndex) {
            completedEpisodes.add(previousEpisodeIndex)
        }

        previousProgress = state.progress
        previousEpisodeIndex = state.episodeIndex
        previousPosition = state.currentPosition
        previousStoryTimeMs = state.storyTimeMs
    }
}
