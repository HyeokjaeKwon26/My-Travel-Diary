package com.traveler.core.classifier

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.TransportMode
import com.traveler.core.model.TransportPrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportClassifierAdversarialTest {

    private val classifier = RuleBasedTransportClassifier()

    @Test
    fun testStationaryGpsJitter_DoesNotClassifyAsWalk() {
        // User remains in hotel for 2 hours while GPS drifts by 20 meters
        val p1 = GeoPoint(40.7580, -73.9855)
        val p2 = GeoPoint(40.7581, -73.9856) // ~20m away
        val durationMs = 2 * 3600 * 1000L // 2 hours

        val segment = MovementSegment(
            id = "jitter-1",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = p1,
            endPoint = p2,
            distanceMeters = 20.0,
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0f, "")
        )

        val prediction = classifier.classify(segment)
        assertEquals(TransportMode.UNKNOWN, prediction.mode)
        assertTrue(prediction.reason.contains("Stationary dwell"))
    }

    @Test
    fun testImpossibleGpsSpike_DoesNotClassifyAsAirplane() {
        // Glitch: 6,000 km in 5 seconds (velocity: 4,320,000 km/h)
        val p1 = GeoPoint(42.3601, -71.0589) // Boston
        val p2 = GeoPoint(48.8566, 2.3522)   // Paris
        val durationMs = 5000L // 5 seconds

        val segment = MovementSegment(
            id = "spike-1",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = p1,
            endPoint = p2,
            distanceMeters = 5500_000.0,
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0f, "")
        )

        val prediction = classifier.classify(segment)
        assertEquals(TransportMode.UNKNOWN, prediction.mode)
        assertTrue(prediction.reason.contains("suspected GPS glitch"))
    }

    @Test
    fun testShortHighwayTripNearAirport_ClassifiedAsCarNotFlight() {
        // Boston to Providence drive (80 km in 50 min = 96 km/h) starting near Logan airport
        val logan = GeoPoint(42.3656, -71.0096)
        val providence = GeoPoint(41.8240, -71.4128)
        val durationMs = 50 * 60 * 1000L

        val segment = MovementSegment(
            id = "drive-near-airport",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = logan,
            endPoint = providence,
            distanceMeters = 80000.0,
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0f, "")
        )

        val prediction = classifier.classify(segment)
        assertEquals(TransportMode.CAR, prediction.mode)
    }

    @Test
    fun testHighSpeedRail_ClassifiedAsTrain() {
        // KTX Seoul to Daejeon: 150 km in 45 min = 200 km/h
        val seoul = GeoPoint(37.5665, 126.9780)
        val daejeon = GeoPoint(36.3504, 127.3845)
        val durationMs = 45 * 60 * 1000L

        val segment = MovementSegment(
            id = "ktx-train",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = seoul,
            endPoint = daejeon,
            distanceMeters = 150000.0,
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0f, "")
        )

        val prediction = classifier.classify(segment)
        assertEquals(TransportMode.TRAIN, prediction.mode)
    }
}
