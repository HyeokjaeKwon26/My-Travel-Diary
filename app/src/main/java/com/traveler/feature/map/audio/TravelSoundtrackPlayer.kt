package com.traveler.feature.map.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import com.traveler.R

/**
 * Manages the optional offline background travel soundtrack during trip playback (P1-21 ~ P1-24).
 *
 * Requirements:
 * - 100% offline, zero network, works in airplane mode.
 * - Plays only during active playback.
 * - Respects Android audio focus (duck/pause on incoming calls or competing media).
 * - Story speed changes (0.5x, 1x, 2x, 3x) NEVER affect music tempo or pitch.
 * - Fails safely and silently if audio hardware or decoding encounters errors.
 */
class TravelSoundtrackPlayer(
    private val context: Context
) : AudioManager.OnAudioFocusChangeListener {

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var isPlayingRequested = false
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                pause()
            }
        }

    var baseVolume: Float = 0.65f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyVolume()
        }

    private var duckMultiplier: Float = 1.0f

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private fun initializePlayer() {
        if (mediaPlayer != null) return
        try {
            val player = MediaPlayer.create(context, R.raw.traveler_memories) ?: return
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.isLooping = true
            player.setOnErrorListener { _, _, _ ->
                release()
                true
            }
            mediaPlayer = player
            isPrepared = true
            applyVolume()
        } catch (_: Exception) {
            mediaPlayer = null
            isPrepared = false
        }
    }

    fun start() {
        if (!isEnabled) return
        isPlayingRequested = true
        if (requestAudioFocus()) {
            if (mediaPlayer == null) {
                initializePlayer()
            }
            try {
                mediaPlayer?.start()
            } catch (_: Exception) {
                // Silently fallback if audio fails
            }
        }
    }

    fun pause() {
        isPlayingRequested = false
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {}
    }

    fun resume() {
        if (!isEnabled) return
        isPlayingRequested = true
        if (requestAudioFocus()) {
            try {
                if (mediaPlayer == null) {
                    initializePlayer()
                }
                mediaPlayer?.start()
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        isPlayingRequested = false
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (_: Exception) {}
        abandonAudioFocus()
    }

    fun release() {
        isPlayingRequested = false
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isPrepared = false
        abandonAudioFocus()
    }

    fun isCurrentlyPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }
    }

    private fun applyVolume() {
        val vol = (baseVolume * duckMultiplier).coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(vol, vol)
        } catch (_: Exception) {}
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        if (hasAudioFocus) return true

        val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(this)
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }

        hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (!hasAudioFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(this)
        }
        hasAudioFocus = false
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                duckMultiplier = 1.0f
                applyVolume()
                if (isPlayingRequested) {
                    try { mediaPlayer?.start() } catch (_: Exception) {}
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duckMultiplier = 0.30f
                applyVolume()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                duckMultiplier = 1.0f
                try { mediaPlayer?.pause() } catch (_: Exception) {}
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                duckMultiplier = 1.0f
                try { mediaPlayer?.pause() } catch (_: Exception) {}
            }
        }
    }
}
