package com.traveler.feature.map.flat

import com.traveler.core.model.TransportMode
import com.traveler.feature.map.renderer.TravelPlaybackState

/** Stable scale by mode; no orbit, arrival zoom, or per-frame route scan. */
object FlatCamera {
    fun span(state: TravelPlaybackState): Double = when (state.currentTransportMode) {
        TransportMode.AIRPLANE -> ((state.currentSegment?.distanceMeters ?: 600000.0) / 111000.0 * .5).coerceIn(2.0, 90.0)
        TransportMode.TRAIN, TransportMode.SUBWAY, TransportMode.FERRY -> .14
        TransportMode.WALK, TransportMode.RUN, TransportMode.BICYCLE -> .025
        else -> .06
    }
}
