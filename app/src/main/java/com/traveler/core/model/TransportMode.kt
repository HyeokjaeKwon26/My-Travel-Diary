package com.traveler.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransportMode {
    WALK,
    RUN,
    BICYCLE,
    CAR,
    BUS,
    TRAIN,
    SUBWAY,
    AIRPLANE,
    FERRY,
    UNKNOWN;

    val emoji: String
        get() = when (this) {
            WALK -> "🚶"
            RUN -> "🏃"
            BICYCLE -> "🚲"
            CAR -> "🚗"
            BUS -> "🚌"
            TRAIN -> "🚆"
            SUBWAY -> "🚇"
            AIRPLANE -> "✈️"
            FERRY -> "⛴️"
            UNKNOWN -> "📍"
        }

    val displayName: String
        get() = when (this) {
            WALK -> "Walking"
            RUN -> "Running"
            BICYCLE -> "Cycling"
            CAR -> "Driving"
            BUS -> "Bus"
            TRAIN -> "Train"
            SUBWAY -> "Subway"
            AIRPLANE -> "Flight"
            FERRY -> "Ferry"
            UNKNOWN -> "Movement"
        }
}

@Serializable
data class TransportPrediction(
    val mode: TransportMode,
    val confidence: Float, // 0.0 to 1.0
    val reason: String,
    val isUserOverride: Boolean = false
)
