package com.traveler.feature.map.renderer

/**
 * Interface representing a monotonically increasing nanosecond time source.
 */
fun interface MonotonicClock {
    fun elapsedNanos(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun elapsedNanos(): Long = System.nanoTime()
}

/**
 * Frame-rate independent, monotonic time tracker for cinematic story playback (P1-05, P1-06).
 * Supports variable playback speeds (0.5x .. 4.0x) with seamless pause, resume, and scrubbing.
 */
class PlaybackTimeTracker(
    val totalDurationSeconds: Float,
    private val clock: MonotonicClock = SystemMonotonicClock
) {
    private val totalDurationNanos: Long = (totalDurationSeconds * 1_000_000_000.0).toLong().coerceAtLeast(1_000_000L)

    var progress: Float = 0.0f
        private set

    var isPlaying: Boolean = false
        private set

    var playbackSpeed: Float = 1.0f
        set(value) {
            if (field != value && value > 0f) {
                if (isPlaying) {
                    update()
                    startProgress = progress
                    startNanos = clock.elapsedNanos()
                }
                field = value
            }
        }

    private var startProgress: Float = 0.0f
    private var startNanos: Long = 0L

    fun start(fromProgress: Float = 0.0f) {
        progress = fromProgress.coerceIn(0.0f, 1.0f)
        if (progress >= 1.0f) {
            progress = 0.0f
        }
        startProgress = progress
        startNanos = clock.elapsedNanos()
        isPlaying = true
    }

    fun pause() {
        if (isPlaying) {
            update()
            isPlaying = false
        }
    }

    fun resume() {
        if (!isPlaying) {
            if (progress >= 1.0f) {
                progress = 0.0f
            }
            startProgress = progress
            startNanos = clock.elapsedNanos()
            isPlaying = true
        }
    }

    fun seekTo(newProgress: Float) {
        progress = newProgress.coerceIn(0.0f, 1.0f)
        startProgress = progress
        startNanos = clock.elapsedNanos()
    }

    fun update(): Float {
        if (!isPlaying) return progress

        val now = clock.elapsedNanos()
        val elapsed = (now - startNanos).coerceAtLeast(0L)
        val deltaProgress = (elapsed.toDouble() * playbackSpeed.toDouble()) / totalDurationNanos.toDouble()
        val nextProgress = (startProgress + deltaProgress).toFloat()

        if (nextProgress >= 1.0f) {
            progress = 1.0f
            isPlaying = false
        } else {
            progress = nextProgress
        }
        return progress
    }
}
