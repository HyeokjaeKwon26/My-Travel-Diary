package com.traveler.core.timeline

import com.traveler.core.model.TransportMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class RealisticTimelineExportTest {

    private val parser = GoogleTimelineJsonParser()

    /**
     * Fixture A: semanticSegments visit using placeLocation.latLng string format
     */
    @Test
    fun testFixtureA_SemanticVisit_LatLng() = runBlocking {
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-04T12:00:00.000Z",
              "endTime": "2026-07-04T13:30:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeId": "ChIJ_BostonCommon",
                  "placeName": "Boston Common",
                  "placeLocation": {
                    "latLng": "42.3550°, -71.0656°"
                  },
                  "probability": 0.95
                }
              }
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))
        assertEquals(1, result.visits.size)
        assertEquals("Boston Common", result.visits[0].placeName)
        assertEquals(42.3550, result.visits[0].location.latitude, 0.0001)
        assertEquals(-71.0656, result.visits[0].location.longitude, 0.0001)
    }

    /**
     * Fixture B: activity using ONLY activity.start.latLng and activity.end.latLng (NO simplifiedRawPath!)
     */
    @Test
    fun testFixtureB_ActivityWithStartEndLatLng_NoSimplifiedRawPath() = runBlocking {
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-04T14:00:00.000Z",
              "endTime": "2026-07-04T15:00:00.000Z",
              "activity": {
                "start": {
                  "latLng": "42.3550°, -71.0656°"
                },
                "end": {
                  "latLng": "42.3601°, -71.0589°"
                },
                "distanceMeters": 1500.0,
                "topCandidate": {
                  "type": "WALKING",
                  "probability": 0.90
                }
              }
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))
        assertEquals(1, result.movementSegments.size)
        val seg = result.movementSegments[0]
        assertEquals(TransportMode.WALK, seg.transport.mode)
        assertEquals(1500.0, seg.distanceMeters, 1.0)
        // MUST NOT be (0.0, 0.0)
        assertNotEquals(0.0, seg.startPoint.latitude, 0.0001)
        assertNotEquals(0.0, seg.startPoint.longitude, 0.0001)
        assertEquals(42.3550, seg.startPoint.latitude, 0.0001)
        assertEquals(-71.0656, seg.startPoint.longitude, 0.0001)
        assertEquals(42.3601, seg.endPoint.latitude, 0.0001)
        assertEquals(-71.0589, seg.endPoint.longitude, 0.0001)
    }

    /**
     * Fixture C: timelinePath with point and time
     */
    @Test
    fun testFixtureC_TimelinePath_PointAndTime() = runBlocking {
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-04T16:00:00.000Z",
              "endTime": "2026-07-04T17:00:00.000Z",
              "timelinePath": [
                {
                  "point": "42.3601°, -71.0589°",
                  "time": "2026-07-04T16:15:00.000Z"
                },
                {
                  "point": "42.3650°, -71.0500°",
                  "time": "2026-07-04T16:45:00.000Z"
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))
        assertEquals(2, result.rawLocationPoints.size)
        assertEquals(42.3601, result.rawLocationPoints[0].coordinate.latitude, 0.0001)
        assertEquals(-71.0589, result.rawLocationPoints[0].coordinate.longitude, 0.0001)
    }

    /**
     * Fixture D: timelinePath with durationMinutesOffsetFromStartTime
     */
    @Test
    fun testFixtureD_TimelinePath_DurationOffset() = runBlocking {
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-04T18:00:00.000Z",
              "endTime": "2026-07-04T19:00:00.000Z",
              "timelinePath": [
                {
                  "point": "43.0896°, -79.0849°",
                  "durationMinutesOffsetFromStartTime": 15
                },
                {
                  "point": "43.0950°, -79.0750°",
                  "durationMinutesOffsetFromStartTime": 45
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))
        assertEquals(2, result.rawLocationPoints.size)
        assertEquals(43.0896, result.rawLocationPoints[0].coordinate.latitude, 0.0001)
        assertEquals(-79.0849, result.rawLocationPoints[0].coordinate.longitude, 0.0001)
        // 18:00 + 15 min = 18:15:00Z -> 1783188900000L
        val expectedEpoch = java.time.Instant.parse("2026-07-04T18:15:00.000Z").toEpochMilli()
        assertEquals(expectedEpoch, result.rawLocationPoints[0].timestampEpochMs)
    }

    /**
     * Fixture E: rawSignals with position.LatLng (capital L)
     */
    @Test
    fun testFixtureE_RawSignals_CapitalLatLng() = runBlocking {
        val json = """
        {
          "rawSignals": [
            {
              "timestamp": "2026-07-04T20:00:00.000Z",
              "position": {
                "LatLng": "37.5665°, 126.9780°",
                "accuracyMeters": 15.0,
                "altitudeMeters": 45.0,
                "speedMetersPerSecond": 1.2
              }
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))
        assertEquals(1, result.rawLocationPoints.size)
        assertEquals(37.5665, result.rawLocationPoints[0].coordinate.latitude, 0.0001)
        assertEquals(126.9780, result.rawLocationPoints[0].coordinate.longitude, 0.0001)
        assertEquals(45.0, result.rawLocationPoints[0].coordinate.altitudeMeters ?: 0.0, 0.1)
        assertEquals(15.0f, result.rawLocationPoints[0].coordinate.accuracyMeters ?: 0f, 0.1f)
    }

    /**
     * Fixture F: Full realistic day with visit, activity (start/end), timelinePath, and rawSignals
     */
    @Test
    fun testFixtureF_CombinedFullDay() = runBlocking {
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-04T09:00:00.000Z",
              "endTime": "2026-07-04T10:30:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeId": "hotel_1",
                  "placeName": "Boston Harbor Hotel",
                  "placeLocation": { "latLng": "42.3556°, -71.0494°" }
                }
              }
            },
            {
              "startTime": "2026-07-04T10:30:00.000Z",
              "endTime": "2026-07-04T11:15:00.000Z",
              "activity": {
                "start": { "latLng": "42.3556°, -71.0494°" },
                "end": { "latLng": "42.3601°, -71.0589°" },
                "distanceMeters": 1200.0,
                "topCandidate": { "type": "WALKING" }
              },
              "timelinePath": [
                { "point": "42.3570°, -71.0530°", "durationMinutesOffsetFromStartTime": 20 }
              ]
            },
            {
              "startTime": "2026-07-04T11:15:00.000Z",
              "endTime": "2026-07-04T13:00:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeId": "faneuil_1",
                  "placeName": "Faneuil Hall",
                  "placeLocation": { "latLng": "42.3601°, -71.0589°" }
                }
              }
            }
          ],
          "rawSignals": [
            {
              "timestamp": "2026-07-04T10:45:00.000Z",
              "position": { "LatLng": "42.3570°, -71.0530°" }
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))
        assertEquals(2, result.visits.size)
        assertEquals(1, result.movementSegments.size)
        // 1 from timelinePath + 1 from rawSignals = 2 raw points
        assertTrue(result.rawLocationPoints.size >= 2)
        assertNotEquals(0.0, result.movementSegments[0].startPoint.latitude, 0.0001)
    }
}
