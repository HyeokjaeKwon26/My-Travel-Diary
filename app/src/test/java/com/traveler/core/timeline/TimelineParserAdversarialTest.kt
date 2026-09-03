package com.traveler.core.timeline

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class TimelineParserAdversarialTest {

    private val parser = GoogleTimelineJsonParser()

    @Test
    fun testMalformedJson_DoesNotCrash() = runBlocking {
        val malformedJson = "{ \"semanticSegments\": [ { \"startTime\": 2026-07-04 invalid json ..."
        val result = parser.parse(ByteArrayInputStream(malformedJson.toByteArray()))

        assertTrue(result.visits.isEmpty())
        assertTrue(result.movementSegments.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun testEmptyJson_ReturnsEmptyResultGracefully() = runBlocking {
        val emptyJson = "{}"
        val result = parser.parse(ByteArrayInputStream(emptyJson.toByteArray()))

        assertTrue(result.visits.isEmpty())
        assertTrue(result.movementSegments.isEmpty())
        assertTrue(result.rawLocationPoints.isEmpty())
    }

    @Test
    fun testUnknownAndUnexpectedFields_AreTolerated() = runBlocking {
        val jsonWithExtraFields = """
        {
          "extraRootMetadata": { "version": 999, "unknownArray": [1, 2, 3] },
          "semanticSegments": [
            {
              "startTime": "2026-07-04T12:00:00.000Z",
              "endTime": "2026-07-04T13:30:00.000Z",
              "extraSegmentField": "should be ignored",
              "visit": {
                "topCandidate": {
                  "placeId": "ChIJ_Unknown",
                  "placeName": "Test Place",
                  "randomNewGoogleField": true,
                  "placeLocation": {
                    "latLng": "37.5665°, 126.9780°"
                  }
                }
              }
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(jsonWithExtraFields.toByteArray()))

        assertEquals(1, result.visits.size)
        assertEquals("Test Place", result.visits[0].placeName)
        assertEquals(37.5665, result.visits[0].location.latitude, 0.0001)
    }

    @Test
    fun testRawSignalsFormat_ParsedSuccessfully() = runBlocking {
        val rawSignalsJson = """
        {
          "rawSignals": [
            {
              "timestamp": "2026-07-04T10:00:00.000Z",
              "position": {
                "latitudeE7": 375665000,
                "longitudeE7": 1269780000
              }
            },
            {
              "timestamp": "2026-07-04T10:05:00.000Z",
              "position": {
                "latitudeE7": 375670000,
                "longitudeE7": 1269790000
              }
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(rawSignalsJson.toByteArray()))

        assertEquals(2, result.rawLocationPoints.size)
        assertEquals(37.5665, result.rawLocationPoints[0].coordinate.latitude, 0.0001)
        assertEquals(126.9780, result.rawLocationPoints[0].coordinate.longitude, 0.0001)
    }
}
