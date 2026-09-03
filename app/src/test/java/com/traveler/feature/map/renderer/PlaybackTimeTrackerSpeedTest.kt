package com.traveler.feature.map.renderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTimeTrackerSpeedTest {

    private class TestMonotonicClock(var currentNanos: Long = 0L) : MonotonicClock {
        override fun elapsedNanos(): Long = currentNanos
        fun advanceSeconds(sec: Double) {
            currentNanos += (sec * 1_000_000_000.0).toLong()
        }
    }

    @Test
    fun playbackSpeed_acceleratesProgress() {
        val clock = TestMonotonicClock(0L)
        val tracker = PlaybackTimeTracker(totalDurationSeconds = 10.0f, clock = clock)

        // 1x speed: 5 seconds elapsed -> 50% progress
        tracker.start()
        clock.advanceSeconds(5.0)
        assertEquals(0.5f, tracker.update(), 0.001f)

        // Change to 2x speed: 2.5 seconds elapsed -> advances by 50% -> reaches 100%
        tracker.playbackSpeed = 2.0f
        clock.advanceSeconds(2.5)
        assertEquals(1.0f, tracker.update(), 0.001f)
    }

    @Test
    fun playbackSpeed_slowMotion_halfSpeed() {
        val clock = TestMonotonicClock(0L)
        val tracker = PlaybackTimeTracker(totalDurationSeconds = 10.0f, clock = clock)

        tracker.playbackSpeed = 0.5f
        tracker.start()
        clock.advanceSeconds(10.0) // 10s at 0.5x = 5s progress on 10s story = 50%
        assertEquals(0.5f, tracker.update(), 0.001f)
    }
}
