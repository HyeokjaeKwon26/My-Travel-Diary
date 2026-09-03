package com.traveler.core.timeline

import com.traveler.core.model.TransportMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class GoogleTimelineJsonParserTest {

    private val parser = GoogleTimelineJsonParser()

    @Test
    fun testParseSemanticSegments() = runBlocking {
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-04T12:00:00.000Z",
              "endTime": "2026-07-04T13:30:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeId": "ChIJ_Niagara",
                  "placeName": "Niagara Falls State Park",
                  "probability": 0.95,
                  "placeLocation": {
                    "latLng": "43.0896°, -79.0849°"
                  }
                }
              }
            },
            {
              "startTime": "2026-07-04T13:30:00.000Z",
              "endTime": "2026-07-04T14:15:00.000Z",
              "activity": {
                "topCandidate": {
                  "type": "IN_PASSENGER_VEHICLE",
                  "probability": 0.90
                },
                "distanceMeters": 42000.0,
                "simplifiedRawPath": {
                  "points": [
                    {
                      "latLng": "43.0896°, -79.0849°",
                      "timestamp": "2026-07-04T13:30:00.000Z"
                    },
                    {
                      "latLng": "43.6532°, -79.3832°",
                      "timestamp": "2026-07-04T14:15:00.000Z"
                    }
                  ]
                }
              }
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))

        assertEquals(1, result.visits.size)
        assertEquals("Niagara Falls State Park", result.visits[0].placeName)
        assertEquals(43.0896, result.visits[0].location.latitude, 0.0001)

        assertEquals(1, result.movementSegments.size)
        assertEquals(TransportMode.CAR, result.movementSegments[0].transport.mode)
        assertEquals(42000.0, result.movementSegments[0].distanceMeters, 0.1)
    }

    @Test
    fun testParseLegacyTimelineObjects() = runBlocking {
        val json = """
        {
          "timelineObjects": [
            {
              "placeVisit": {
                "location": {
                  "latitudeE7": 423601000,
                  "longitudeE7": -710589000,
                  "name": "Boston Common",
                  "address": "Boston, MA"
                },
                "duration": {
                  "startTimestamp": "2026-07-01T10:00:00Z",
                  "endTimestamp": "2026-07-01T11:30:00Z"
                },
                "placeConfidence": "HIGH_CONFIDENCE"
              }
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))

        assertEquals(1, result.visits.size)
        assertEquals("Boston Common", result.visits[0].placeName)
        assertEquals(42.3601, result.visits[0].location.latitude, 0.0001)
        assertEquals(-71.0589, result.visits[0].location.longitude, 0.0001)
    }
}
