package com.traveler.core.timeline

import com.traveler.core.model.GeometryProvenance
import com.traveler.core.model.TransportMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ModernTimelinePathGeometryTest {

    private val parser = GoogleTimelineJsonParser()

    @Test
    fun semanticSegmentWithActivityAndTimelinePath_incorporatesPathIntoSimplifiedPoints() = runBlocking {
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-04T10:00:00Z",
              "endTime": "2026-07-04T10:30:00Z",
              "activity": {
                "start": { "lat": 42.3503, "lng": -71.0810 },
                "end": { "lat": 42.3650, "lng": -71.0100 },
                "activityType": "IN_PASSENGER_VEHICLE",
                "probability": 0.95
              },
              "timelinePath": [
                { "point": "geo:42.3550,-71.0600", "durationMinutesOffsetFromStartTime": 10 },
                { "point": "geo:42.3600,-71.0300", "durationMinutesOffsetFromStartTime": 20 }
              ]
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(json.byteInputStream(), null)
        assertEquals(TimelineParseStatus.SUCCESS, result.status)
        assertEquals(1, result.movementSegments.size)

        val segment = result.movementSegments.first()
        assertEquals(TransportMode.CAR, segment.transport.mode)
        assertEquals(GeometryProvenance.OBSERVED, segment.geometryProvenance)

        // Must contain all 4 waypoints: Start, Path1, Path2, End
        assertTrue(
            "simplifiedPoints must include timelinePath geometry (size: ${segment.simplifiedPoints.size})",
            segment.simplifiedPoints.size >= 4
        )
        assertEquals(42.3503, segment.simplifiedPoints.first().latitude, 0.001)
        assertEquals(42.3650, segment.simplifiedPoints.last().latitude, 0.001)
    }

    @Test
    fun timelinePathOnlySegment_createsMovementSegment() = runBlocking {
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-04T14:00:00Z",
              "endTime": "2026-07-04T14:45:00Z",
              "timelinePath": [
                { "point": "geo:35.6895,139.6917", "durationMinutesOffsetFromStartTime": 0 },
                { "point": "geo:35.6950,139.7200", "durationMinutesOffsetFromStartTime": 20 },
                { "point": "geo:35.7000,139.7500", "durationMinutesOffsetFromStartTime": 45 }
              ]
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(json.byteInputStream(), null)
        assertEquals(TimelineParseStatus.SUCCESS, result.status)
        assertEquals(1, result.movementSegments.size)

        val segment = result.movementSegments.first()
        assertEquals(TransportMode.UNKNOWN, segment.transport.mode)
        assertEquals(GeometryProvenance.OBSERVED, segment.geometryProvenance)
        assertEquals(3, segment.simplifiedPoints.size)
        assertEquals(35.6895, segment.startPoint.latitude, 0.001)
        assertEquals(35.7000, segment.endPoint.latitude, 0.001)
    }
}
