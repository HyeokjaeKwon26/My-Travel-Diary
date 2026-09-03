package com.traveler.core.classifier

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.TransportMode
import com.traveler.core.model.TransportPrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighSpeedRailClassifierTest {

    private val classifier = RuleBasedTransportClassifier()

    @Test
    fun testBeijingShanghaiHighSpeedRail_IsClassifiedAsTrain_NotAirplane() {
        // Beijing to Shanghai: ~1300 km over 4.5 hours = ~290 km/h avg speed
        val beijing = GeoPoint(39.9042, 116.4074)
        val shanghai = GeoPoint(31.2304, 121.4737)
        val durationMs = (4.5 * 3600 * 1000).toLong()

        val segment = MovementSegment(
            id = "beijing-shanghai-hsr",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = beijing,
            endPoint = shanghai,
            distanceMeters = 1318_000.0,
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0.0f, "")
        )

        val result = classifier.classify(segment)
        assertEquals("300 km/h ground route must be TRAIN, not AIRPLANE", TransportMode.TRAIN, result.mode)
        assertTrue(result.confidence >= 0.75f)
    }

    @Test
    fun testTokyoOsakaShinkansen_IsClassifiedAsTrain() {
        // Tokyo to Shin-Osaka: ~515 km over 2.5 hours = ~206 km/h
        val tokyo = GeoPoint(35.6812, 139.7671)
        val osaka = GeoPoint(34.7335, 135.5003)
        val durationMs = (2.5 * 3600 * 1000).toLong()

        val segment = MovementSegment(
            id = "tokyo-osaka-shinkansen",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = tokyo,
            endPoint = osaka,
            distanceMeters = 515_000.0,
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0.0f, "")
        )

        val result = classifier.classify(segment)
        assertEquals(TransportMode.TRAIN, result.mode)
    }

    @Test
    fun testTranscontinentalFlight_IsClassifiedAsAirplane() {
        // New York to London: ~5500 km over 7 hours = ~785 km/h
        val jfk = GeoPoint(40.6413, -73.7781)
        val lhr = GeoPoint(51.4700, -0.4543)
        val durationMs = (7.0 * 3600 * 1000).toLong()

        val segment = MovementSegment(
            id = "jfk-lhr-flight",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = jfk,
            endPoint = lhr,
            distanceMeters = 5500_000.0,
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0.0f, "")
        )

        val result = classifier.classify(segment)
        assertEquals(TransportMode.AIRPLANE, result.mode)
        assertTrue(result.confidence >= 0.90f)
    }
}
