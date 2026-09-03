package com.traveler.core.media

import com.traveler.core.media.MediaAccessCapabilities.AccessLevel
import kotlinx.serialization.Serializable

/**
 * Diagnostic tracking model for media candidate ingestion across all pipeline stages (P0-01).
 * Local-only debug and verification structure; never transmitted over network.
 */
@Serializable
data class MediaPipelineDiagnostics(
    val visibleImageRows: Int = 0,
    val visibleVideoRows: Int = 0,
    val permissionMode: AccessLevel = AccessLevel.DENIED,
    val accessMediaLocation: Boolean = false,
    val rowsWithValidDateTaken: Int = 0,
    val rowsWithMissingOrZeroDateTaken: Int = 0,
    val rowsPassingBroadIngestionWindow: Int = 0,
    val rowsParsedExifTimestamp: Int = 0,
    val rowsParsedFallbackTimestamp: Int = 0,
    val rowsExcludedUnresolvedTimestamp: Int = 0,
    val rowsWithGpsExact: Int = 0,
    val rowsMatchedVisit: Int = 0,
    val rowsMatchedMovement: Int = 0,
    val rowsDateConfidentUnassigned: Int = 0,
    val rowsAmbiguousDate: Int = 0,
    val strongOrLikelyCaptures: Int = 0,
    val downloadsExcluded: Int = 0,
    val screenshotsExcluded: Int = 0,
    val weakDateOnlyExcluded: Int = 0,
    val finalTripMediaCount: Int = 0
) {
    fun summary(): String = buildString {
        appendLine("=== Media Pipeline Diagnostics ===")
        appendLine("1. MediaStore visible images: $visibleImageRows")
        appendLine("2. MediaStore visible videos: $visibleVideoRows")
        appendLine("3. Permission mode: $permissionMode")
        appendLine("4. ACCESS_MEDIA_LOCATION: $accessMediaLocation")
        appendLine("5. Rows with valid DATE_TAKEN: $rowsWithValidDateTaken")
        appendLine("6. Rows with missing/zero DATE_TAKEN: $rowsWithMissingOrZeroDateTaken")
        appendLine("7. Rows passing broad UTC ingestion window: $rowsPassingBroadIngestionWindow")
        appendLine("8. Strong/likely travel photo captures: $strongOrLikelyCaptures")
        appendLine("9. Downloads excluded: $downloadsExcluded")
        appendLine("10. Screenshots excluded: $screenshotsExcluded")
        appendLine("11. Weak date-only excluded: $weakDateOnlyExcluded")
        appendLine("12. Rows parsed with EXIF timestamp: $rowsParsedExifTimestamp")
        appendLine("13. Rows parsed via fallback timestamp: $rowsParsedFallbackTimestamp")
        appendLine("14. Rows excluded (timestamp unresolved): $rowsExcludedUnresolvedTimestamp")
        appendLine("15. Rows with GPS_EXACT: $rowsWithGpsExact")
        appendLine("16. Rows matched to Visit: $rowsMatchedVisit")
        appendLine("17. Rows matched to MovementSegment: $rowsMatchedMovement")
        appendLine("18. Date-confident unassigned rows: $rowsDateConfidentUnassigned")
        appendLine("19. Ambiguous-date rows: $rowsAmbiguousDate")
        appendLine("20. Final unique Trip media count: $finalTripMediaCount")
    }
}
