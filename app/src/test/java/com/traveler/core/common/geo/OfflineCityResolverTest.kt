package com.traveler.core.common.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCityResolverTest {

    @Test
    fun resolveNearestCity_findsBostonForCambridgeCoordinates() {
        val cambridgeLoc = GeoPoint(42.3736, -71.1097)
        val city = OfflineCityResolver.resolveNearestCity(cambridgeLoc, maxDistanceKm = 50.0)
        assertNotNull(city)
        assertTrue(city?.name == "Cambridge" || city?.name == "Boston")
    }

    @Test
    fun getEffectivePlaceName_preservesValidPlaceName() {
        val loc = GeoPoint(42.3601, -71.0589)
        val label = OfflineCityResolver.getEffectivePlaceName("Boston Common", false, loc)
        assertEquals("Boston Common", label.displayName)
        assertTrue(label.shouldShowOnMap)
        assertFalse(label.isCityFallback)
    }

    @Test
    fun getEffectivePlaceName_replacesUnknownWithNearestCity() {
        val loc = GeoPoint(42.3601, -71.0589) // Boston Harbor
        val label = OfflineCityResolver.getEffectivePlaceName("UNKNOWN", false, loc)
        assertTrue(label.displayName.startsWith("Near "))
        assertTrue(label.shouldShowOnMap)
        assertTrue(label.isCityFallback)
    }

    @Test
    fun getEffectivePlaceName_replacesNullInOceanWithUnlabeledStop() {
        val oceanLoc = GeoPoint(0.0, -30.0) // Middle of Atlantic Ocean
        val label = OfflineCityResolver.getEffectivePlaceName(null, false, oceanLoc)
        assertEquals("Unlabeled stop", label.displayName)
        assertFalse(label.shouldShowOnMap) // Map omits text label to prevent clutter
    }
}
