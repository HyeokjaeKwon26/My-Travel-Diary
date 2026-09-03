package com.traveler.core.timeline

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ParserChunkFailureTest {

    private val parser = GoogleTimelineJsonParser()

    @Test
    fun testBrokenSemanticChunk_ReturnsPartialWithWarnings_NotSilentSuccess() = runBlocking {
        // Input containing 2 valid visits, 1 valid activity, and 1 structurally valid but semantically broken object
        val mixedJson = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-04T08:00:00.000Z",
              "endTime": "2026-07-04T09:00:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeId": "boston_common",
                  "placeName": "Boston Common",
                  "placeLocation": { "latLng": "42.3550°, -71.0656°" }
                }
              }
            },
            {
              "startTime": "2026-07-04T09:00:00.000Z",
              "endTime": "2026-07-04T09:45:00.000Z",
              "activity": {
                "topCandidate": { "type": "WALKING", "probability": 0.95 },
                "distanceMeters": 2500.0,
                "start": { "latLng": "42.3550°, -71.0656°" },
                "end": { "latLng": "42.3601°, -71.0589°" }
              }
            },
            {
              "startTime": "2026-07-04T09:45:00.000Z",
              "endTime": "2026-07-04T10:30:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeName": "Broken Coordinates Place",
                  "placeLocation": { "latLng": "invalid_coordinates" }
                }
              }
            },
            {
              "startTime": "2026-07-04T10:30:00.000Z",
              "endTime": "2026-07-04T12:00:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeId": "faneuil_hall",
                  "placeName": "Faneuil Hall",
                  "placeLocation": { "latLng": "42.3601°, -71.0560°" }
                }
              }
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(mixedJson.toByteArray()))

        assertEquals(TimelineParseStatus.PARTIAL_WITH_WARNINGS, result.status)
        assertTrue("Warnings must record the broken chunk", result.warnings.isNotEmpty())
        assertEquals(2, result.visits.size)
        assertEquals(1, result.movementSegments.size)
    }
}
