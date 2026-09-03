package com.traveler.core.timeline

import com.traveler.core.model.LocationPoint
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.Visit
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneOffset

data class DateRangeFilter(
    val startEpochMs: Long,
    val endEpochMs: Long
) {
    /**
     * Checks if an instantaneous timestamp falls inside this date range filter.
     */
    fun contains(epochMs: Long): Boolean = epochMs in startEpochMs..endEpochMs

    /**
     * P1-02: Checks if a duration/interval from intervalStartMs to intervalEndMs overlaps with this filter.
     * An interval overlaps if its end is >= filter start AND its start is <= filter end.
     */
    fun overlaps(intervalStartMs: Long, intervalEndMs: Long): Boolean {
        return intervalEndMs >= startEpochMs && intervalStartMs <= endEpochMs
    }

    companion object {
        /**
         * Creates a wide global UTC query window spanning UTC+14 (earliest timezone)
         * to UTC-12 (latest timezone) ensuring no local travel calendar events are clipped out.
         */
        fun forLocalDateRange(startDate: LocalDate, endDate: LocalDate): DateRangeFilter {
            val startEpochMs = startDate.atStartOfDay(ZoneOffset.ofHours(14)).toInstant().toEpochMilli()
            val endEpochMs = endDate.plusDays(1).atStartOfDay(ZoneOffset.ofHours(-12)).toInstant().toEpochMilli() - 1L
            return DateRangeFilter(startEpochMs, endEpochMs)
        }
    }
}

enum class TimelineParseStatus {
    SUCCESS,
    PARTIAL_WITH_WARNINGS,
    FATAL_UNSUPPORTED_FORMAT,
    FATAL_MALFORMED_INPUT
}

data class TimelineParseResult(
    val visits: List<Visit>,
    val movementSegments: List<MovementSegment>,
    val rawLocationPoints: List<LocationPoint>,
    val status: TimelineParseStatus = TimelineParseStatus.SUCCESS,
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null
)

interface LocationHistorySource {
    val sourceName: String
    suspend fun parse(inputStream: InputStream, filter: DateRangeFilter? = null): TimelineParseResult
}
