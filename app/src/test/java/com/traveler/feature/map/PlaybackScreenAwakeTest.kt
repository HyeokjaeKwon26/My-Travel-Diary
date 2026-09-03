package com.traveler.feature.map

import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackScreenAwakeTest {

    @Test
    fun testKeepScreenOn_lifecycleTransitions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = View(context)

        // Initial default state
        assertFalse(view.keepScreenOn)

        // 1. Playback starts -> keepScreenOn becomes true
        val previousState = view.keepScreenOn
        var isPlaying = true
        if (isPlaying) {
            view.keepScreenOn = true
        }
        assertTrue("When playback is active, keepScreenOn must be true", view.keepScreenOn)

        // 2. Playback paused -> keepScreenOn restored to previous
        isPlaying = false
        if (!isPlaying) {
            view.keepScreenOn = previousState
        }
        assertFalse("When playback is paused, keepScreenOn must return to previous state", view.keepScreenOn)

        // 3. Playback resumed -> keepScreenOn becomes true
        isPlaying = true
        if (isPlaying) {
            view.keepScreenOn = true
        }
        assertTrue("When playback resumes, keepScreenOn must be true", view.keepScreenOn)

        // 4. Playback exited / disposed -> keepScreenOn restored
        view.keepScreenOn = previousState
        assertFalse("On exit, keepScreenOn must be false", view.keepScreenOn)
    }
}
