package com.traveler.core.media

import com.traveler.core.model.TimestampConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoTimestampResolverTest {

    @Test
    fun testResolve_ExifWithOffset() {
        val result = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = "2026:07:04 15:30:00",
            exifOffset = "-04:00",
            mediaStoreDateTaken = null,
            fileName = "IMG_001.jpg",
            fileDateModifiedMs = null
        )

        assertEquals(TimestampConfidence.EXIF_EXACT, result.confidence)
        val ts = result.timestampEpochMs
        assertTrue(ts != null && ts > 0L)
    }

    @Test
    fun testResolve_FilenameFallback_WhenExifMissing() {
        val result = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = null,
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileName = "PXL_20260714_143550123.jpg",
            fileDateModifiedMs = 1783100000000L,
            candidateTripTimezones = listOf(java.time.ZoneOffset.UTC)
        )

        assertEquals(TimestampConfidence.FILENAME_INFERRED, result.confidence)
        val ts = result.timestampEpochMs
        assertTrue(ts != null && ts > 0L)
    }

    @Test
    fun testResolve_MediaStoreDateTaken() {
        val mediaStoreEpoch = 1783178000000L
        val result = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = null,
            exifOffset = null,
            mediaStoreDateTaken = mediaStoreEpoch,
            fileName = "random_photo_name.jpg",
            fileDateModifiedMs = 1783190000000L
        )

        assertEquals(TimestampConfidence.MEDIASTORE, result.confidence)
        assertEquals(mediaStoreEpoch, result.timestampEpochMs)
    }

    @Test
    fun testResolve_UnknownTimestamp_ReturnsNull_NeverSystemCurrentTime() {
        val result = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = null,
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileName = "unnamed.png",
            fileDateModifiedMs = null
        )

        assertEquals(TimestampConfidence.UNKNOWN, result.confidence)
        assertNull(result.timestampEpochMs)
    }

    @Test
    fun testResolve_FilenameWithoutContextOrFileTime_ReturnsUnknown() {
        val result = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = null,
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileName = "PXL_20260714_143550123.jpg",
            fileDateModifiedMs = null,
            candidateTripTimezones = emptyList()
        )

        assertEquals(TimestampConfidence.UNKNOWN, result.confidence)
        assertNull(result.timestampEpochMs)
    }
}
