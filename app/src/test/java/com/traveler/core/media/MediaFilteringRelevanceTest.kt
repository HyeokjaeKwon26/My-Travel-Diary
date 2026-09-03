package com.traveler.core.media

import com.traveler.core.common.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFilteringRelevanceTest {

    @Test
    fun cameraPhotoWithExif_isClassifiedStrongCapture() {
        val evidence = AndroidMediaStoreScanner.classifyCaptureEvidence(
            relativePath = "DCIM/Camera/",
            bucketName = "Camera",
            fileName = "IMG_20260704_120000.jpg",
            dateTakenMs = 1783153800000L,
            dateModifiedMs = 1783153800000L,
            hasExifOriginal = true,
            hasExifOffset = true,
            hasGps = true
        )
        assertEquals(MediaCaptureEvidence.STRONG_CAPTURE, evidence)
        assertTrue(evidence.isEligibleForTrip)
    }

    @Test
    fun downloadedImageWithDateAddedOnly_isClassifiedDownloadedOrExternal_andExcluded() {
        val evidence = AndroidMediaStoreScanner.classifyCaptureEvidence(
            relativePath = "Download/",
            bucketName = "Download",
            fileName = "funny_cat_meme.jpg",
            dateTakenMs = null,
            dateModifiedMs = 1783153800000L,
            hasExifOriginal = false,
            hasExifOffset = false,
            hasGps = false
        )
        assertEquals(MediaCaptureEvidence.DOWNLOADED_OR_EXTERNAL, evidence)
        assertFalse(evidence.isEligibleForTrip)
    }

    @Test
    fun screenshotWithTripDates_isClassifiedScreenshot_andExcluded() {
        val evidence = AndroidMediaStoreScanner.classifyCaptureEvidence(
            relativePath = "Pictures/Screenshots/",
            bucketName = "Screenshots",
            fileName = "Screenshot_20260704_153022.png",
            dateTakenMs = 1783153800000L,
            dateModifiedMs = 1783153800000L,
            hasExifOriginal = false,
            hasExifOffset = false,
            hasGps = false
        )
        assertEquals(MediaCaptureEvidence.SCREENSHOT, evidence)
        assertFalse(evidence.isEligibleForTrip)
    }

    @Test
    fun messagingAppSavedImage_isClassifiedDownloadedOrExternal_andExcluded() {
        val evidence = AndroidMediaStoreScanner.classifyCaptureEvidence(
            relativePath = "Pictures/Telegram/",
            bucketName = "Telegram",
            fileName = "photo_5839201948201.jpg",
            dateTakenMs = null,
            dateModifiedMs = 1783153800000L,
            hasExifOriginal = false,
            hasExifOffset = false,
            hasGps = false
        )
        assertEquals(MediaCaptureEvidence.DOWNLOADED_OR_EXTERNAL, evidence)
        assertFalse(evidence.isEligibleForTrip)
    }

    @Test
    fun downloadedOriginalPhotoWithFullExifAndGps_isClassifiedStrongCapture() {
        val evidence = AndroidMediaStoreScanner.classifyCaptureEvidence(
            relativePath = "Download/",
            bucketName = "Download",
            fileName = "IMG_SHARED_ORIGINAL.jpg",
            dateTakenMs = 1783153800000L,
            dateModifiedMs = 1783153800000L,
            hasExifOriginal = true,
            hasExifOffset = true,
            hasGps = true
        )
        assertEquals(MediaCaptureEvidence.STRONG_CAPTURE, evidence)
        assertTrue(evidence.isEligibleForTrip)
    }

    @Test
    fun cameraPhotoWithDateTakenMissing_butValidExif_isClassifiedStrongCapture() {
        val evidence = AndroidMediaStoreScanner.classifyCaptureEvidence(
            relativePath = "DCIM/Camera/",
            bucketName = "Camera",
            fileName = "IMG_20260705_091500.jpg",
            dateTakenMs = null,
            dateModifiedMs = 1783153800000L,
            hasExifOriginal = true,
            hasExifOffset = true,
            hasGps = false
        )
        assertEquals(MediaCaptureEvidence.STRONG_CAPTURE, evidence)
        assertTrue(evidence.isEligibleForTrip)
    }
}
