package com.traveler.feature.map.renderer

import org.junit.Assert.*
import org.junit.Test

class PlaybackTimeTrackerTest {

    private class FakeMonotonicClock : MonotonicClock {
        var currentNanos: Long = 0L
        override fun elapsedNanos(): Long = currentNanos

        fun advanceMillis(ms: Long) {
            currentNanos += ms * 1_000_000L
        }
    }

    @Test
    fun test60FpsProgression_ReachesCompletionAtExactTargetDuration() {
        val clock = FakeMonotonicClock()
        val tracker = PlaybackTimeTracker(totalDurationSeconds = 10.0f, clock = clock)

        tracker.start(0.0f)
        assertTrue(tracker.isPlaying)

        val frameIntervalMs = 16L // ~60fps
        var frames = 0
        while (tracker.isPlaying && frames < 1000) {
            clock.advanceMillis(frameIntervalMs)
            tracker.update()
            frames++
        }

        assertFalse(tracker.isPlaying)
        assertEquals(1.0f, tracker.progress, 0.001f)
        // 10.0s = 10,000ms. With 16ms step, 625 frames = 10,000ms
        assertEquals(10_000L * 1_000_000L, clock.currentNanos)
    }

    @Test
    fun test30FpsProgression_ReachesCompletionAtExactTargetDuration() {
        val clock = FakeMonotonicClock()
        val tracker = PlaybackTimeTracker(totalDurationSeconds = 10.0f, clock = clock)

        tracker.start(0.0f)

        val frameIntervalMs = 33L // ~30fps
        while (tracker.isPlaying) {
            clock.advanceMillis(frameIntervalMs)
            tracker.update()
        }

        assertFalse(tracker.isPlaying)
        assertEquals(1.0f, tracker.progress, 0.001f)
        // 10.0s reached around 10,000ms - 10,032ms
        assertTrue("Wall clock time around 10s", clock.currentNanos in (9_900_000_000L..10_100_000_000L))
    }

    @Test
    fun test200MsFrameStall_ProgressCatchesUpWithoutStretchingTotalStoryTime() {
        val clock = FakeMonotonicClock()
        val tracker = PlaybackTimeTracker(totalDurationSeconds = 5.0f, clock = clock)

        tracker.start(0.0f)

        // 1 second of normal 60fps playback
        for (i in 0 until 60) {
            clock.advanceMillis(16)
            tracker.update()
        }
        val progressBeforeStall = tracker.progress
        assertTrue("Progress around 0.20", progressBeforeStall in 0.18f..0.22f)

        // Severe 200ms frame drop / stall
        clock.advanceMillis(200)
        val progressAfterStall = tracker.update()
        val deltaProgress = progressAfterStall - progressBeforeStall
        // 200ms / 5000ms = 0.04 progress jump
        assertEquals(0.04f, deltaProgress, 0.005f)

        // Continue to finish
        while (tracker.isPlaying) {
            clock.advanceMillis(16)
            tracker.update()
        }

        assertFalse(tracker.isPlaying)
        assertEquals(1.0f, tracker.progress, 0.001f)
        assertTrue("Story finished at 5 seconds", clock.currentNanos in (5_000_000_000L..5_050_000_000L))
    }

    @Test
    fun testPauseAndResume_DoesNotJumpForwardDuringPausedInterval() {
        val clock = FakeMonotonicClock()
        val tracker = PlaybackTimeTracker(totalDurationSeconds = 10.0f, clock = clock)

        tracker.start(0.0f)
        clock.advanceMillis(2000) // 2.0s
        tracker.update()
        assertEquals(0.2f, tracker.progress, 0.01f)

        tracker.pause()
        assertFalse(tracker.isPlaying)
        val pausedProgress = tracker.progress

        // 10 minutes pass while paused!
        clock.advanceMillis(600_000)

        // Resume playback
        tracker.resume()
        assertTrue(tracker.isPlaying)
        assertEquals("Progress must not jump when resumed", pausedProgress, tracker.progress, 0.001f)

        // Advance 1 second
        clock.advanceMillis(1000)
        tracker.update()
        assertEquals("Progress should be 0.3 after 1s playback post-resume", 0.3f, tracker.progress, 0.01f)
    }

    @Test
    fun testSeekTo_UpdatesProgressAndPreservesPlaybackState() {
        val clock = FakeMonotonicClock()
        val tracker = PlaybackTimeTracker(totalDurationSeconds = 10.0f, clock = clock)

        tracker.start(0.0f)
        clock.advanceMillis(1000)
        tracker.update()

        // User scrubs to 75%
        tracker.seekTo(0.75f)
        assertEquals(0.75f, tracker.progress, 0.001f)

        // Advance 1 second
        clock.advanceMillis(1000) // +1.0s / 10s = +0.10
        tracker.update()
        assertEquals(0.85f, tracker.progress, 0.01f)
    }
}
