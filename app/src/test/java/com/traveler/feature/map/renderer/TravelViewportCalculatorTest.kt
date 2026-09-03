package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class TravelViewportCalculatorTest {

    @Test
    fun tokyoToSanFrancisco_framesShortPacificSpan() {
        val tokyo = GeoPoint(35.6762, 139.6503)
        val sf = GeoPoint(37.7749, -122.4194)

        val calculator = TravelViewportCalculator(
            width = 1000,
            height = 600,
            insets = SafeContentInsets(left = 20f, top = 20f, right = 20f, bottom = 20f),
            allPoints = listOf(tokyo, sf)
        )

        // Across the Pacific, Tokyo (139.7E) to SF (122.4W) is ~98° longitude span (world X span ~ 0.27).
        // Without antimeridian unwrapping, the naive span across Europe/Atlantic is ~262° (world X span ~ 0.73).
        assertTrue("Span X must be less than 0.60 for Pacific crossing", calculator.spanX < 0.60)

        val coordsOut1 = FloatArray(2)
        val coordsOut2 = FloatArray(2)
        calculator.toScreen(tokyo.latitude, tokyo.longitude, coordsOut1)
        calculator.toScreen(sf.latitude, sf.longitude, coordsOut2)

        assertTrue("Tokyo screen X must be within canvas width", coordsOut1[0] in 0f..1000f)
        assertTrue("SF screen X must be within canvas width", coordsOut2[0] in 0f..1000f)
        assertTrue("Tokyo screen Y must be within canvas height", coordsOut1[1] in 0f..600f)
        assertTrue("SF screen Y must be within canvas height", coordsOut2[1] in 0f..600f)
    }

    @Test
    fun preservesCanvasAspectRatio_preventsStretching() {
        val boston = GeoPoint(42.3503, -71.0810)
        val cambridge = GeoPoint(42.3736, -71.1097)

        val calculator = TravelViewportCalculator(
            width = 1200,
            height = 600,
            allPoints = listOf(boston, cambridge)
        )

        // Canvas aspect ratio is 1200 / 600 = 2.0
        val renderedAspect = calculator.spanX / calculator.spanY
        assertEquals("Viewport world aspect ratio must match canvas content aspect ratio (2.0)", 2.0, renderedAspect, 0.01)
    }
}
