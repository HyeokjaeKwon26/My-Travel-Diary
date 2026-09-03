package com.traveler.core.model

import com.traveler.core.common.geo.GeoPoint
import kotlinx.serialization.Serializable

@Serializable
data class Visit(
    val id: String,
    val placeName: String? = null,
    val placeAddress: String? = null,
    val placeId: String? = null,
    val location: GeoPoint,
    val startTimestampEpochMs: Long,
    val endTimestampEpochMs: Long,
    val confidence: Float = 1.0f,
    val timezoneId: String? = null,
    val isUserOverride: Boolean = false
) {
    val durationMillis: Long
        get() = maxOf(0L, endTimestampEpochMs - startTimestampEpochMs)
}
