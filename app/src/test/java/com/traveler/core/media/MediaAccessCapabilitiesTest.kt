package com.traveler.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAccessCapabilitiesTest {

    @Test
    fun qualifiedFullWithoutLocation_isRecognizedCorrectly() {
        val caps = MediaAccessCapabilities(
            images = MediaAccessCapabilities.AccessLevel.FULL,
            videos = MediaAccessCapabilities.AccessLevel.FULL,
            locationMetadata = MediaAccessCapabilities.LocationMetadataAccess.UNAVAILABLE
        )

        assertTrue("Should be qualified full without location", caps.isQualifiedFullWithoutLocation)
        assertFalse("Should NOT be unqualified full with location", caps.isFullWithLocation)
        assertFalse("Should NOT be photos only", caps.isPhotosOnly)
        assertFalse("Should NOT be partial", caps.isPartial)
        assertFalse("Should NOT be denied", caps.isDenied)
    }

    @Test
    fun fullWithLocation_isRecognizedCorrectly() {
        val caps = MediaAccessCapabilities(
            images = MediaAccessCapabilities.AccessLevel.FULL,
            videos = MediaAccessCapabilities.AccessLevel.FULL,
            locationMetadata = MediaAccessCapabilities.LocationMetadataAccess.AVAILABLE
        )

        assertTrue(caps.isFullWithLocation)
        assertFalse(caps.isQualifiedFullWithoutLocation)
    }

    @Test
    fun photosOnly_isRecognizedCorrectly() {
        val caps = MediaAccessCapabilities(
            images = MediaAccessCapabilities.AccessLevel.FULL,
            videos = MediaAccessCapabilities.AccessLevel.DENIED,
            locationMetadata = MediaAccessCapabilities.LocationMetadataAccess.AVAILABLE
        )

        assertTrue(caps.isPhotosOnly)
        assertFalse(caps.isFullWithLocation)
        assertFalse(caps.isQualifiedFullWithoutLocation)
    }
}
