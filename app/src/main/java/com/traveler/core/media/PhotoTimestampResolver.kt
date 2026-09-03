package com.traveler.core.media

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.time.TimeUtils
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.TimestampConfidence
import com.traveler.core.model.Visit
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.regex.Pattern

data class ResolvedTimestamp(
    val timestampEpochMs: Long?,
    val resolvedZoneId: String? = null,
    val confidence: TimestampConfidence,
    val provenance: String? = null
)

data class RawMediaCandidate(
    val id: String,
    val contentUriString: String,
    val fileName: String,
    val mimeType: String,
    val exifDateTimeOriginal: String?,
    val exifOffset: String?,
    val mediaStoreDateTaken: Long?,
    val fileDateModifiedMs: Long?,
    val directGps: GeoPoint?,
    val directGpsZoneId: String? = null,
    val relativePath: String? = null,
    val bucketDisplayName: String? = null,
    val captureEvidence: MediaCaptureEvidence = MediaCaptureEvidence.UNKNOWN
) {
    val effectiveEvidence: MediaCaptureEvidence
        get() = if (captureEvidence != MediaCaptureEvidence.UNKNOWN) {
            captureEvidence
        } else when {
            !exifOffset.isNullOrBlank() || (!exifDateTimeOriginal.isNullOrBlank() && directGps != null) -> MediaCaptureEvidence.STRONG_CAPTURE
            !exifDateTimeOriginal.isNullOrBlank() || (mediaStoreDateTaken != null && mediaStoreDateTaken > 0L) -> MediaCaptureEvidence.LIKELY_CAPTURE
            directGps != null -> MediaCaptureEvidence.STRONG_CAPTURE
            else -> MediaCaptureEvidence.WEAK_DATE_ONLY
        }
}

object PhotoTimestampResolver {

    private val FILENAME_PATTERN = Pattern.compile(".*(20\\d{2})(\\d{2})(\\d{2})_(\\d{2})(\\d{2})(\\d{2}).*")

    fun resolve(
        candidate: RawMediaCandidate,
        candidateTripTimezones: List<ZoneId> = emptyList(),
        visits: List<Visit> = emptyList(),
        segments: List<MovementSegment> = emptyList(),
        tripIntervalStartMs: Long? = null,
        tripIntervalEndMs: Long? = null
    ): ResolvedTimestamp {
        return resolve(
            exifDateTimeOriginal = candidate.exifDateTimeOriginal,
            exifOffset = candidate.exifOffset,
            mediaStoreDateTaken = candidate.mediaStoreDateTaken,
            fileName = candidate.fileName,
            fileDateModifiedMs = candidate.fileDateModifiedMs,
            referenceLocation = candidate.directGps,
            referenceLocationZoneId = candidate.directGpsZoneId,
            candidateTripTimezones = candidateTripTimezones,
            visits = visits,
            segments = segments,
            tripIntervalStartMs = tripIntervalStartMs,
            tripIntervalEndMs = tripIntervalEndMs
        )
    }

    /**
     * Resolves capture timestamp using the strict confidence hierarchy.
     * Pure function with respect to TimeShape - receives precomputed timezone context.
     * Evaluates Timeline context to resolve ambiguous local times without arbitrarily picking the first timezone.
     * Preserves structured resolvedZoneId for durable persistence and cross-restart consistency.
     * P1-04 / P1-05: NEVER fabricates UTC for timezone-less local wall-clock times.
     */
    fun resolve(
        exifDateTimeOriginal: String?,
        exifOffset: String?,
        mediaStoreDateTaken: Long?,
        fileName: String,
        fileDateModifiedMs: Long?,
        referenceLocation: GeoPoint? = null,
        referenceLocationZoneId: String? = null,
        candidateTripTimezones: List<ZoneId> = emptyList(),
        visits: List<Visit> = emptyList(),
        segments: List<MovementSegment> = emptyList(),
        tripIntervalStartMs: Long? = null,
        tripIntervalEndMs: Long? = null
    ): ResolvedTimestamp {

        // 1. EXIF DateTimeOriginal + explicit Offset (Exact absolute UTC)
        if (!exifDateTimeOriginal.isNullOrBlank() && !exifOffset.isNullOrBlank()) {
            val isoCandidate = exifDateTimeOriginal.trim()
                .replaceFirst(':', '-')
                .replaceFirst(':', '-')
                .replace(' ', 'T') + exifOffset.trim()
            TimeUtils.parseToInstant(isoCandidate)?.let {
                val zoneId = try { ZoneOffset.of(exifOffset.trim()).id } catch (_: Exception) { null }
                return ResolvedTimestamp(it.toEpochMilli(), zoneId, TimestampConfidence.EXIF_EXACT, "EXIF DateTimeOriginal with explicit offset")
            }
        }

        // 2. Trustworthy MediaStore DATE_TAKEN (Verified absolute timestamp)
        if (mediaStoreDateTaken != null && mediaStoreDateTaken > 946684800000L) { // Post-2000
            return ResolvedTimestamp(mediaStoreDateTaken, referenceLocationZoneId, TimestampConfidence.MEDIASTORE, "MediaStore DATE_TAKEN")
        }

        // 3. EXIF DateTimeOriginal + EXIF GPS -> exact geographic timezone (P0-04: Authoritative)
        if (!exifDateTimeOriginal.isNullOrBlank() && referenceLocationZoneId != null) {
            val geoZone = try { ZoneId.of(referenceLocationZoneId) } catch (_: Exception) { null }
            if (geoZone != null) {
                val epochMs = parseLocalWallClock(exifDateTimeOriginal, geoZone, visits, segments)
                return if (epochMs != null) {
                    ResolvedTimestamp(epochMs, geoZone.id, TimestampConfidence.EXIF_LOCAL, "EXIF local time with direct GPS timezone ($geoZone)")
                } else {
                    ResolvedTimestamp(null, null, TimestampConfidence.UNKNOWN, "Authoritative GPS timezone ($geoZone) local time is ambiguous or nonexistent (DST gap/overlap)")
                }
            }
        }

        // 4. EXIF DateTimeOriginal + Contextual Timeline Scoring across candidate timezones
        if (!exifDateTimeOriginal.isNullOrBlank()) {
            val ldt = parseToLocalDateTime(exifDateTimeOriginal)
            if (ldt != null) {
                val scored = scoreCandidateLocalTime(
                    ldt = ldt,
                    candidateZones = candidateTripTimezones,
                    visits = visits,
                    segments = segments,
                    tripIntervalStartMs = tripIntervalStartMs,
                    tripIntervalEndMs = tripIntervalEndMs,
                    targetConfidence = TimestampConfidence.EXIF_LOCAL
                )
                if (scored != null) {
                    return scored
                }
            }
            // P1-04: Never fabricate UTC for wall clock time without context
            return ResolvedTimestamp(null, null, TimestampConfidence.UNKNOWN, "Local wall-clock time has no timezone context.")
        }

        // 5. Filename Pattern with Contextual Timeline Scoring
        val filenameLdt = parseLocalDateTimeFromFilename(fileName)
        if (filenameLdt != null) {
            val scored = scoreCandidateLocalTime(
                ldt = filenameLdt,
                candidateZones = candidateTripTimezones,
                visits = visits,
                segments = segments,
                tripIntervalStartMs = tripIntervalStartMs,
                tripIntervalEndMs = tripIntervalEndMs,
                targetConfidence = TimestampConfidence.FILENAME_INFERRED
            )
            if (scored != null) {
                return scored
            }
            return ResolvedTimestamp(null, null, TimestampConfidence.UNKNOWN, "Local wall-clock time has no timezone context.")
        }

        // 6. File Modification Time Fallback
        if (fileDateModifiedMs != null && fileDateModifiedMs > 946684800000L) {
            return ResolvedTimestamp(fileDateModifiedMs, referenceLocationZoneId, TimestampConfidence.FILETIME_INFERRED, "File system last modified timestamp")
        }

        // 7. Unknown / Unresolved
        return ResolvedTimestamp(null, null, TimestampConfidence.UNKNOWN, "Capture time unavailable or ambiguous")
    }

    private fun scoreCandidateLocalTime(
        ldt: LocalDateTime,
        candidateZones: List<ZoneId>,
        visits: List<Visit>,
        segments: List<MovementSegment>,
        tripIntervalStartMs: Long?,
        tripIntervalEndMs: Long?,
        targetConfidence: TimestampConfidence
    ): ResolvedTimestamp? {
        if (candidateZones.isEmpty()) return null

        val distinctZones = candidateZones.distinct()
        data class ScoredCandidate(
            val zone: ZoneId,
            val epochMs: Long,
            val score: Int
        )

        val scoredList = mutableListOf<ScoredCandidate>()

        for (zone in distinctZones) {
            val validOffsets = try { zone.rules.getValidOffsets(ldt) } catch (_: Exception) { emptyList() }
            if (validOffsets.isEmpty()) continue // P1-03: DST gap (nonexistent local time)

            for (offset in validOffsets) {
                val instant = try {
                    ldt.toInstant(offset)
                } catch (_: Exception) {
                    null
                } ?: continue

                val epochMs = instant.toEpochMilli()

                // Discard candidate times outside the trip interval (with 4h tolerance for boundary transit)
                if (tripIntervalStartMs != null && epochMs < tripIntervalStartMs - 4 * 3600_000L) continue
                if (tripIntervalEndMs != null && epochMs > tripIntervalEndMs + 4 * 3600_000L) continue

                // P1-05: Evaluate all overlapping Visits and MovementSegments for context compatibility / contradiction
                val overlappingVisits = visits.filter { epochMs >= it.startTimestampEpochMs && epochMs <= it.endTimestampEpochMs }
                val overlappingSegments = segments.filter { epochMs >= it.startTimestampEpochMs && epochMs <= it.endTimestampEpochMs }

                var hasCompatibleVisit = false
                var hasContradictoryVisit = false
                var hasUnknownVisit = false

                for (v in overlappingVisits) {
                    when {
                        v.timezoneId == zone.id -> hasCompatibleVisit = true
                        v.timezoneId != null -> hasContradictoryVisit = true
                        else -> hasUnknownVisit = true
                    }
                }

                var hasCompatibleSegment = false
                var hasContradictorySegment = false
                var hasUnknownSegment = false

                for (s in overlappingSegments) {
                    val matches = s.startTimezoneId == zone.id || s.endTimezoneId == zone.id
                    val hasKnownEndpoint = s.startTimezoneId != null || s.endTimezoneId != null
                    when {
                        matches -> hasCompatibleSegment = true
                        hasKnownEndpoint -> hasContradictorySegment = true
                        else -> hasUnknownSegment = true
                    }
                }

                val hasCompatible = hasCompatibleVisit || hasCompatibleSegment
                val hasContradictory = hasContradictoryVisit || hasContradictorySegment

                // P1-05: Known timezone contradiction must invalidate candidate context, not just give zero bonus
                if (hasContradictory && !hasCompatible) {
                    continue // Candidate Instant directly contradicts verified timeline context
                }

                var score = 10 // Baseline valid score inside trip window
                if (hasCompatibleVisit) score += 100
                if (hasCompatibleSegment) score += 50
                if (!hasCompatible && (hasUnknownVisit || hasUnknownSegment)) score += 15

                scoredList.add(ScoredCandidate(zone, epochMs, score))
            }
        }

        if (scoredList.isEmpty()) return null

        val maxScore = scoredList.maxOf { it.score }
        val topCandidates = scoredList.filter { it.score == maxScore }

        // P0-01C & P1-03: If all candidates only receive generic baseline valid score (10),
        // we can only resolve if there is a single candidate zone and single valid offset; otherwise it is ambiguous
        if (maxScore <= 10) {
            return if (topCandidates.size == 1 && distinctZones.size == 1) {
                val winner = topCandidates.first()
                ResolvedTimestamp(
                    timestampEpochMs = winner.epochMs,
                    resolvedZoneId = winner.zone.id,
                    confidence = targetConfidence,
                    provenance = "Single trip candidate timezone (${winner.zone})"
                )
            } else {
                // Ambiguous local wall-clock time without context or unresolved DST overlap
                ResolvedTimestamp(
                    timestampEpochMs = null,
                    resolvedZoneId = null,
                    confidence = TimestampConfidence.UNKNOWN,
                    provenance = "Ambiguous local wall-clock time with multiple plausible timezones or DST overlap"
                )
            }
        }

        // If multiple distinct candidates tie at a high score (> 10), treat as ambiguous
        if (topCandidates.size > 1) {
            return ResolvedTimestamp(
                timestampEpochMs = null,
                resolvedZoneId = null,
                confidence = TimestampConfidence.UNKNOWN,
                provenance = "Candidate times match multiple timeline intervals equally"
            )
        }

        val winner = topCandidates.first()
        return ResolvedTimestamp(
            timestampEpochMs = winner.epochMs,
            resolvedZoneId = winner.zone.id,
            confidence = targetConfidence,
            provenance = "Contextually scored against trip timeline (${winner.zone})"
        )
    }

    private fun parseLocalWallClock(
        exifDate: String,
        zone: ZoneId,
        visits: List<Visit> = emptyList(),
        segments: List<MovementSegment> = emptyList()
    ): Long? {
        val ldt = parseToLocalDateTime(exifDate) ?: return null
        val validOffsets = try { zone.rules.getValidOffsets(ldt) } catch (_: Exception) { emptyList() }
        return when (validOffsets.size) {
            1 -> {
                ldt.toInstant(validOffsets.first()).toEpochMilli()
            }
            2 -> {
                // P1-03: DST Overlap - evaluate both candidate instants against timeline context
                val instants = validOffsets.map { ldt.toInstant(it).toEpochMilli() }
                val matching = instants.filter { epochMs ->
                    val matchingVisits = visits.filter { epochMs in it.startTimestampEpochMs..it.endTimestampEpochMs }
                    val matchingSegments = segments.filter { epochMs in it.startTimestampEpochMs..it.endTimestampEpochMs }

                    val visitCompatible = matchingVisits.any { it.timezoneId == null || it.timezoneId == zone.id }
                    val visitContradictory = matchingVisits.any { it.timezoneId != null && it.timezoneId != zone.id }

                    val segCompatible = matchingSegments.any {
                        (it.startTimezoneId == null && it.endTimezoneId == null) ||
                        it.startTimezoneId == zone.id || it.endTimezoneId == zone.id
                    }
                    val segContradictory = matchingSegments.any {
                        (it.startTimezoneId != null || it.endTimezoneId != null) &&
                        it.startTimezoneId != zone.id && it.endTimezoneId != zone.id
                    }

                    (visitCompatible && !visitContradictory) || (segCompatible && !segContradictory)
                }
                if (matching.size == 1) matching.first() else null
            }
            else -> null // P1-03: DST Gap (0 offsets) -> nonexistent local time
        }
    }

    private fun parseToLocalDateTime(raw: String): LocalDateTime? {
        val cleaned = raw.trim()
        val formatted = if (cleaned.length >= 19 && cleaned[4] == ':' && cleaned[7] == ':') {
            cleaned.substring(0, 4) + "-" +
                    cleaned.substring(5, 7) + "-" +
                    cleaned.substring(8, 10) + "T" +
                    cleaned.substring(11, 19)
        } else if (cleaned.contains("T")) {
            cleaned.take(19)
        } else {
            cleaned.replace(" ", "T").take(19)
        }

        return try {
            LocalDateTime.parse(formatted)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLocalDateTimeFromFilename(fileName: String): LocalDateTime? {
        val matcher = FILENAME_PATTERN.matcher(fileName)
        if (matcher.matches()) {
            return try {
                val year = matcher.group(1)!!.toInt()
                val month = matcher.group(2)!!.toInt()
                val day = matcher.group(3)!!.toInt()
                val hour = matcher.group(4)!!.toInt()
                val minute = matcher.group(5)!!.toInt()
                val second = matcher.group(6)!!.toInt()
                LocalDateTime.of(year, month, day, hour, minute, second)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }
}
