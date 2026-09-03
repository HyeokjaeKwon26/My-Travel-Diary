package com.traveler.core.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface TripDayItem {
    val timestampEpochMs: Long

    @Serializable
    data class VisitItem(
        val visit: Visit,
        val photos: List<MediaItem> = emptyList()
    ) : TripDayItem {
        override val timestampEpochMs: Long get() = visit.startTimestampEpochMs
    }

    @Serializable
    data class MovementItem(
        val segment: MovementSegment,
        val photos: List<MediaItem> = emptyList()
    ) : TripDayItem {
        override val timestampEpochMs: Long get() = segment.startTimestampEpochMs
    }

    @Serializable
    data class ContextualPhotosItem(
        val parentVisit: Visit? = null,
        val parentSegment: MovementSegment? = null,
        val contextLabel: String,
        val photos: List<MediaItem> = emptyList(),
        override val timestampEpochMs: Long = photos.firstOrNull()?.timestampEpochMs ?: 0L
    ) : TripDayItem

    @Serializable
    data class UnassignedPhotosItem(
        val photos: List<MediaItem> = emptyList(),
        override val timestampEpochMs: Long = 0L
    ) : TripDayItem
}

@Serializable
data class TripDay(
    val dayIndex: Int, // 1-indexed (Day 1, Day 2, etc.)
    val dateIso: String, // "2026-07-04"
    val timezoneId: String? = null, // "America/New_York" or null if unknown
    val items: List<TripDayItem> = emptyList(),
    val unassignedPhotos: List<MediaItem> = emptyList(),
    val totalDistanceMeters: Double = 0.0,
    val photoCount: Int = 0
)
