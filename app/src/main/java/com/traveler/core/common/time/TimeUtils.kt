package com.traveler.core.common.time

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object TimeUtils {

    private val ISO_FORMATTERS = listOf(
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_INSTANT,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
    )

    /**
     * Parses an ISO 8601 timestamp string with explicit timezone/offset, an epoch millisecond string,
     * or an Instant string into an Instant.
     * Returns null if parsing fails or if the string represents timezone-less local wall-clock time (P2-11).
     */
    fun parseToInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        // 1. Numeric epoch timestamp (milliseconds, microseconds, seconds)
        trimmed.toLongOrNull()?.let { num ->
            return when {
                num > 100_000_000_000_000L -> Instant.ofEpochMilli(num / 1000L) // Microseconds
                num > 100_000_000_000L -> Instant.ofEpochMilli(num) // Milliseconds
                num > 1_000_000_000L -> Instant.ofEpochSecond(num) // Seconds
                else -> null
            }
        }

        // 2. Standard Instant ISO-8601 parse (e.g. "2026-07-01T14:00:00Z")
        try {
            return Instant.parse(trimmed)
        } catch (_: DateTimeParseException) {}

        // 3. OffsetDateTime parse (e.g. "2026-07-01T10:00:00-04:00")
        try {
            return OffsetDateTime.parse(trimmed).toInstant()
        } catch (_: Exception) {}

        // 4. Timezone-less strings must NOT invent UTC (P2-11)
        return null
    }

    /**
     * Formats an Instant into a human-readable string in the given ZoneId.
     */
    fun formatTime(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        return instant.atZone(zoneId).format(formatter)
    }

    fun formatDate(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
        return instant.atZone(zoneId).format(formatter)
    }

    fun formatDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    /**
     * Formats movement departure and arrival across timezones, indicating (+1 day / -1 day) if applicable.
     */
    fun formatMovementTimeSpan(
        startInstant: Instant,
        startZone: ZoneId,
        endInstant: Instant,
        endZone: ZoneId
    ): String {
        return if (startZone.id == endZone.id) {
            val startTime = formatTime(startInstant, startZone)
            val endTime = formatTime(endInstant, startZone)
            "$startTime - $endTime"
        } else {
            val startZdt = startInstant.atZone(startZone)
            val endZdt = endInstant.atZone(endZone)
            val startStr = DateTimeFormatter.ofPattern("HH:mm z", Locale.US).format(startZdt)
            val endStr = DateTimeFormatter.ofPattern("HH:mm z", Locale.US).format(endZdt)
            val dayDiff = java.time.temporal.ChronoUnit.DAYS.between(startZdt.toLocalDate(), endZdt.toLocalDate())
            val dayDiffStr = when {
                dayDiff > 0 -> " (+$dayDiff day)"
                dayDiff < 0 -> " ($dayDiff day)"
                else -> ""
            }
            "$startStr → $endStr$dayDiffStr"
        }
    }
}
