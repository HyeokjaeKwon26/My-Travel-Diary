package com.traveler.feature.map

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import com.traveler.feature.map.flat.FlatCamera
import com.traveler.feature.map.renderer.*
import org.junit.Assert.*
import org.junit.Test

class FlatCameraTest {
    private val point = GeoPoint(40.7, -74.0)
    private val state = TravelPlaybackState(.5f, 1000, point, TransportMode.CAR, 0f,
        cameraCenter=point, cameraSpanLat=.01, cameraSpanLng=.01)

    @Test fun headingAndArrivalDoNotChangeMapScale() {
        val span=FlatCamera.span(state)
        assertEquals(span,FlatCamera.span(state.copy(currentHeadingDegrees=180f,progress=.99f,cameraSpanLat=.0001)),0.0)
        val viewport=TravelViewportCalculator(720,1280,SafeContentInsets(),listOf(point),point,span)
        val origin=FloatArray(2);val north=FloatArray(2)
        viewport.toScreen(point.latitude,point.longitude,origin)
        viewport.toScreen(point.latitude+.01,point.longitude,north)
        assertEquals(360f,origin[0],.1f); assertEquals(640f,origin[1],.1f)
        assertTrue(north[1]<origin[1]);assertEquals(origin[0],north[0],.1f)
        assertTrue("Local map must not be clamped to regional scale",viewport.spanY<.001)
    }

    @Test fun flightScaleIsBoundedAndAllModesHaveUsefulScale() {
        TransportMode.values().forEach { assertTrue(FlatCamera.span(state.copy(currentTransportMode=it))>0) }
        assertTrue(FlatCamera.span(state.copy(currentTransportMode=TransportMode.AIRPLANE))>
            FlatCamera.span(state.copy(currentTransportMode=TransportMode.CAR)))
    }
    @Test fun shortJourneyOverviewFitsItsActualBounds() {
        val points=listOf(point,GeoPoint(40.72,-73.96))
        val viewport=TravelViewportCalculator(720,1280,SafeContentInsets(),points)
        assertTrue("A city trip must not become a hundred-kilometre overview",viewport.spanY<.0005)
        points.forEach {
            val xy=FloatArray(2);viewport.toScreen(it.latitude,it.longitude,xy)
            assertTrue(xy[0] in 0f..720f && xy[1] in 0f..1280f)
        }
    }

}
