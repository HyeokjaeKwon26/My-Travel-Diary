package com.traveler.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaCaptureEvidence {
    STRONG_CAPTURE,          // EXIF DateTimeOriginal with explicit offset, GPS_EXACT matching timeline, or camera DCIM + reliable DATE_TAKEN
    LIKELY_CAPTURE,          // Camera folder provenance with valid capture timestamp or matched to Visit/Movement
    WEAK_DATE_ONLY,          // Only filesystem DATE_ADDED / DATE_MODIFIED, no camera provenance, no EXIF/GPS
    DOWNLOADED_OR_EXTERNAL,  // Download/Downloads/Pictures/WhatsApp/Telegram/Reddit/Saved/Browser folder
    SCREENSHOT,              // Screenshots folder or Screenshot_ filename pattern
    UNKNOWN;

    val isEligibleForTrip: Boolean
        get() = this == STRONG_CAPTURE || this == LIKELY_CAPTURE
}
