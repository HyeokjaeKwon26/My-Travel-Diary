package com.traveler.core.common.geo

import org.junit.Assert.*
import org.junit.Test

class PlaceSemanticsNormalizationTest {

    @Test
    fun inferredHome_normalizesToHomeWithNearestCity() {
        val nyLoc = GeoPoint(40.7128, -74.0060)
        val label = OfflineCityResolver.getEffectivePlaceName(
            rawPlaceName = "INFERRED_HOME",
            isUserOverride = false,
            location = nyLoc
        )
        assertEquals("Home · New York", label.displayName)
        assertTrue(label.shouldShowOnMap)
        assertFalse(label.isCityFallback)
    }

    @Test
    fun inferredWork_normalizesToWorkWithNearestCity() {
        val bostonLoc = GeoPoint(42.3601, -71.0589)
        val label = OfflineCityResolver.getEffectivePlaceName(
            rawPlaceName = "INFERRED_WORK",
            isUserOverride = false,
            location = bostonLoc
        )
        assertEquals("Work · Boston", label.displayName)
        assertTrue(label.shouldShowOnMap)
        assertFalse(label.isCityFallback)
    }

    @Test
    fun rawUnknown_fallsBackToStopNearCity() {
        val buffaloLoc = GeoPoint(42.8864, -78.8784)
        val label = OfflineCityResolver.getEffectivePlaceName(
            rawPlaceName = "UNKNOWN",
            isUserOverride = false,
            location = buffaloLoc
        )
        assertEquals("Near Buffalo", label.displayName)
        assertTrue(label.shouldShowOnMap)
        assertTrue(label.isCityFallback)
    }

    @Test
    fun rawUnknown_withoutNearbyCity_fallsBackToUnlabeledStop() {
        val remoteLoc = GeoPoint(65.0, -140.0) // Remote wilderness
        val label = OfflineCityResolver.getEffectivePlaceName(
            rawPlaceName = "UNKNOWN",
            isUserOverride = false,
            location = remoteLoc
        )
        assertEquals("Unlabeled stop", label.displayName)
        assertFalse(label.shouldShowOnMap)
    }

    @Test
    fun validPlaceName_isPreserved() {
        val nyLoc = GeoPoint(40.7794, -73.9632)
        val label = OfflineCityResolver.getEffectivePlaceName(
            rawPlaceName = "Metropolitan Museum of Art",
            isUserOverride = false,
            location = nyLoc
        )
        assertEquals("Metropolitan Museum of Art", label.displayName)
        assertTrue(label.shouldShowOnMap)
        assertFalse(label.isCityFallback)
    }

    @Test
    fun nullPlaceName_usesAddressFallbackIfAvailable() {
        val nyLoc = GeoPoint(40.7128, -74.0060)
        val label = OfflineCityResolver.getEffectivePlaceName(
            rawPlaceName = null,
            isUserOverride = false,
            location = nyLoc,
            placeAddress = "Broadway Theater, New York, NY 10036"
        )
        assertEquals("Broadway Theater", label.displayName)
        assertTrue(label.shouldShowOnMap)
    }

    @Test
    fun rawUnknown_nearGrandCanyon_fallsBackToIconicLandmark() {
        // South Rim viewpoint: lat 36.057, lon -112.143 (close to Grand Canyon, far from any major city)
        val grandCanyonLoc = GeoPoint(36.057, -112.143)
        val label = OfflineCityResolver.getEffectivePlaceName(
            rawPlaceName = "UNKNOWN",
            isUserOverride = false,
            location = grandCanyonLoc
        )
        assertTrue("Expected landmark name to contain Grand Canyon but was ${label.displayName}", label.displayName.contains("Grand Canyon"))
        assertTrue(label.shouldShowOnMap)
        assertTrue(label.isCityFallback)
    }
}
