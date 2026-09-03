package com.traveler.core.timeline

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.model.TransportMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class RawGpsWalkingVsDwellTest {

    private val parser = GoogleTimelineJsonParser()

    @Test
    fun stationaryJitter_20MinutesWithin30Meters_mustReconstructAsVisit() = runTest {
        val baseTime = 1782800000000L // arbitrary base time
        val anchorLat = 37.5665
        val anchorLng = 126.9780

        // 20 points, 1 minute apart, with slight noise (+-0.0001 deg ~ 10m)
        val sb = StringBuilder()
        sb.append("""{"rawSignals": [""")
        for (i in 0 until 20) {
            val t = baseTime + i * 60 * 1000L
            val lat = anchorLat + (if (i % 2 == 0) 0.0001 else -0.0001)
            val lng = anchorLng + (if (i % 3 == 0) 0.0001 else -0.0001)
            if (i > 0) sb.append(",")
            sb.append("""{"timestampMs": "$t", "position": {"LatLng": "$lat, $lng"}}""")
        }
        sb.append("]}")

        val stream = ByteArrayInputStream(sb.toString().toByteArray(StandardCharsets.UTF_8))
        val result = parser.parse(stream, null)

        assertEquals("Stationary dwell must reconstruct 1 Visit", 1, result.visits.size)
        assertEquals("Stationary dwell must NOT reconstruct Movement", 0, result.movementSegments.size)

        val v = result.visits.first()
        val dist = GeodesicUtils.distanceMeters(v.location, GeoPoint(anchorLat, anchorLng))
        assertTrue("Visit centroid must be within 20m of anchor", dist < 20.0)
    }

    @Test
    fun slowWalking_20MinutesMoving70MetersPerMinute_mustReconstructAsMovementNotVisit() = runTest {
        val baseTime = 1782800000000L
        val startLat = 40.7128
        val startLng = -74.0060

        // 20 samples, 1 minute apart, walking ~70m per minute south (0.00063 deg per minute ~ 70m)
        // Total displacement: ~1.4 km in 20 minutes (speed ~ 4.2 km/h)
        val sb = StringBuilder()
        sb.append("""{"rawSignals": [""")
        for (i in 0 until 20) {
            val t = baseTime + i * 60 * 1000L
            val lat = startLat - i * 0.00063 // moving south ~70m/min
            val lng = startLng
            if (i > 0) sb.append(",")
            sb.append("""{"timestampMs": "$t", "position": {"LatLng": "$lat, $lng"}}""")
        }
        sb.append("]}")

        val stream = ByteArrayInputStream(sb.toString().toByteArray(StandardCharsets.UTF_8))
        val result = parser.parse(stream, null)

        assertEquals("Continuous walking must NOT be classified as a Visit", 0, result.visits.size)
        assertEquals("Continuous walking must be classified as a MovementSegment", 1, result.movementSegments.size)

        val seg = result.movementSegments.first()
        assertTrue("Movement distance must be > 1000m", seg.distanceMeters > 1000.0)
        assertEquals("TransportMode should be WALK", TransportMode.WALK, seg.transport.mode)
    }

    @Test
    fun trafficQueueJitter_15MinutesInSmallRegion_mustReconstructAsVisit() = runTest {
        val baseTime = 1782800000000L
        val anchorLat = 35.6762
        val anchorLng = 139.6503

        // 15 points, 1 minute apart, noisy GPS within ~25m
        val sb = StringBuilder()
        sb.append("""{"rawSignals": [""")
        for (i in 0 until 15) {
            val t = baseTime + i * 60 * 1000L
            val lat = anchorLat + (i % 4 - 2) * 0.00008
            val lng = anchorLng + (i % 3 - 1) * 0.00008
            if (i > 0) sb.append(",")
            sb.append("""{"timestampMs": "$t", "position": {"LatLng": "$lat, $lng"}}""")
        }
        sb.append("]}")

        val stream = ByteArrayInputStream(sb.toString().toByteArray(StandardCharsets.UTF_8))
        val result = parser.parse(stream, null)

        assertEquals("Queue/traffic stationary jitter must reconstruct 1 Visit", 1, result.visits.size)
        assertEquals("Queue/traffic stationary jitter must NOT reconstruct Movement", 0, result.movementSegments.size)
    }
}
