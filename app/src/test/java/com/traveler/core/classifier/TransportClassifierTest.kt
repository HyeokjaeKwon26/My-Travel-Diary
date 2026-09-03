package com.traveler.core.classifier

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.TransportMode
import com.traveler.core.model.TransportPrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportClassifierTest {

    private val classifier = RuleBasedTransportClassifier()

    @Test
    fun testWalkingClassification() {
        // 1.5 km over 25 minutes = 3.6 km/h
        val start = GeoPoint(40.7580, -73.9855)
        val end = GeoPoint(40.7680, -73.9810)
        val durationMs = 25 * 60 * 1000L

        val segment = MovementSegment(
            id = "walk-1",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = start,
            endPoint = end,
            distanceMeters = 1500.0,
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0f, "")
        )

        val prediction = classifier.classify(segment)
        assertEquals(TransportMode.WALK, prediction.mode)
        assertTrue(prediction.confidence >= 0.85f)
    }

    @Test
    fun testCarHighwayClassification() {
        // 45 km over 35 minutes = ~77 km/h
        val start = GeoPoint(42.3601, -71.0589) // Boston
        val end = GeoPoint(42.6334, -71.3162) // Lowell
        val durationMs = 35 * 60 * 1000L

        val segment = MovementSegment(
            id = "car-1",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = start,
            endPoint = end,
            distanceMeters = 45000.0,
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0f, "")
        )

        val prediction = classifier.classify(segment)
        assertEquals(TransportMode.CAR, prediction.mode)
        assertTrue(prediction.confidence >= 0.80f)
    }

    @Test
    fun testFlightClassification_LongDisplacementAndGpsGap() {
        // NY (JFK) -> Seoul (Incheon): ~11,000 km over 14 hours
        val jfk = GeoPoint(40.6413, -73.7781)
        val incheon = GeoPoint(37.4602, 126.4407)
        val durationMs = 14 * 3600 * 1000L

        val segment = MovementSegment(
            id = "flight-1",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = jfk,
            endPoint = endPointWithGap(incheon),
            distanceMeters = 11000_000.0,
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0f, "")
        )

        val prediction = classifier.classify(segment)
        assertEquals(TransportMode.AIRPLANE, prediction.mode)
        assertTrue("Flight confidence should be high", prediction.confidence >= 0.85f)
    }

    @Test
    fun testSemanticPriority_OverridesHeuristicsWhenHighConfidence() {
        val start = GeoPoint(0.0, 0.0)
        val end = GeoPoint(0.0, 0.01)

        val segment = MovementSegment(
            id = "train-semantic",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + 600000L,
            startPoint = start,
            endPoint = end,
            distanceMeters = 5000.0,
            durationMillis = 600000L,
            transport = TransportPrediction(
                mode = TransportMode.TRAIN,
                confidence = 0.95f,
                reason = "Google Timeline Semantic Activity: IN_TRAIN"
            )
        )

        val prediction = classifier.classify(segment)
        assertEquals(TransportMode.TRAIN, prediction.mode)
        assertEquals(0.95f, prediction.confidence, 0.01f)
    }

    @Test
    fun testRunningClassification() {
        // 4.25 km over 30 minutes = 8.5 km/h (Jogging / Running)
        val start = GeoPoint(37.5500, 126.9800)
        val end = GeoPoint(37.5800, 127.0000)
        val durationMs = 30 * 60 * 1000L

        val segment = MovementSegment(
            id = "run-1",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = start,
            endPoint = end,
            distanceMeters = 4250.0,
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0f, "")
        )

        val prediction = classifier.classify(segment)
        assertEquals(TransportMode.RUN, prediction.mode)
        assertTrue(prediction.confidence >= 0.60f)
    }

    @Test
    fun testCyclingClassification() {
        // 10 km over 30 minutes = 20.0 km/h (Cycling)
        val start = GeoPoint(37.5500, 126.9800)
        val end = GeoPoint(37.6200, 127.0500)
        val durationMs = 30 * 60 * 1000L

        val segment = MovementSegment(
            id = "bike-1",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = start,
            endPoint = end,
            distanceMeters = 10000.0,
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0f, "")
        )

        val prediction = classifier.classify(segment)
        assertEquals(TransportMode.BICYCLE, prediction.mode)
        assertTrue(prediction.confidence >= 0.70f)
    }

    private fun endPointWithGap(point: GeoPoint) = point
}
