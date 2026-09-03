package com.traveler.feature.map.audio

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TravelSoundtrackPlayerTest {

    private lateinit var context: Context
    private lateinit var player: TravelSoundtrackPlayer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        player = TravelSoundtrackPlayer(context)
    }

    @Test
    fun testInitialState_isEnabledByDefault() {
        assertTrue(player.isEnabled)
        assertFalse(player.isCurrentlyPlaying())
        assertEquals(0.65f, player.baseVolume, 0.01f)
    }

    @Test
    fun testEnableDisable_controlsPlayback() {
        player.isEnabled = false
        assertFalse(player.isEnabled)
        player.start()
        assertFalse(player.isCurrentlyPlaying())

        player.isEnabled = true
        assertTrue(player.isEnabled)
    }

    @Test
    fun testVolumeClamping() {
        player.baseVolume = 1.5f
        assertEquals(1.0f, player.baseVolume, 0.01f)

        player.baseVolume = -0.5f
        assertEquals(0.0f, player.baseVolume, 0.01f)

        player.baseVolume = 0.5f
        assertEquals(0.5f, player.baseVolume, 0.01f)
    }

    @Test
    fun testAudioFocusChanges() {
        // Transient loss with ducking
        player.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        // Regain focus
        player.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        // Loss transient
        player.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        // Permanent loss
        player.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
    }

    @Test
    fun testSafeLifecycleCalls() {
        player.start()
        player.pause()
        player.resume()
        player.stop()
        player.release()
        // Calling methods after release must be safe
        player.pause()
        player.stop()
        player.release()
    }
}
