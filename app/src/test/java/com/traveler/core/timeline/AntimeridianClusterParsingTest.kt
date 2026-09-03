package com.traveler.core.timeline

import com.traveler.core.common.geo.GeoPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntimeridianClusterParsingTest {

    @Test
    fun rawGpsCluster_aroundAntimeridian_reconstructsNearDateLineNotGreenwich() = runBlocking {
        val parser = GoogleTimelineJsonParser()

        // Cluster straddling the 180th meridian in Fiji (each point ~10m from meridian, dwell = 20 mins):
        // 179.9999°E, 179.9998°E, 179.9999°W (-179.9999°), 179.9998°W (-179.9998°)
        val startMs = 1782871200000L
        val rawPoints = listOf(
            GeoPoint(latitude = -16.8, longitude = 179.9999),
            GeoPoint(latitude = -16.8, longitude = 179.9998),
            GeoPoint(latitude = -16.8, longitude = -179.9999),
            GeoPoint(latitude = -16.8, longitude = -179.9998)
        )

        // Synthesize JSON payload with Records.json / raw location points
        val jsonPayload = buildString {
            append("{\"locations\": [")
            rawPoints.forEachIndexed { idx, pt ->
                if (idx > 0) append(",")
                val ts = startMs + idx * 7 * 60 * 1000L
                append("{\"latitudeE7\": ${(pt.latitude * 1e7).toLong()}, \"longitudeE7\": ${(pt.longitude * 1e7).toLong()}, \"timestampMs\": \"$ts\"}")
            }
            append("]}")
        }

        val result = parser.parse(jsonPayload.byteInputStream())

        assertEquals("Must reconstruct 1 clustered visit from 21-minute dwell", 1, result.visits.size)
        val reconstructedVisit = result.visits.first()

        val reconstructedLng = reconstructedVisit.location.longitude
        // Must be near 180° or -180° (e.g. >= 175.0 or <= -175.0), NOT near 0° Greenwich!
        assertTrue(
            "Reconstructed longitude ($reconstructedLng) must be near the 180° antimeridian, NOT near 0° Greenwich",
            Math.abs(reconstructedLng) >= 175.0
        )
    }
}
