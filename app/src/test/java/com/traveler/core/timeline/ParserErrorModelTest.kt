package com.traveler.core.timeline

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ParserErrorModelTest {

    @Test
    fun testTruncatedJson_ReturnsPartialWithWarnings_NotSilentSuccess() = runBlocking {
        val truncatedJson = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-10T10:00:00.000Z",
              "endTime": "2026-07-10T12:00:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeId": "place_1",
                  "placeName": "First Valid Place",
                  "placeLocation": { "latLng": "37.5665°, 126.9780°" }
                }
              }
            },
            {
              "startTime": "2026-07-10T12:00:00.000Z",
              "endTime": "2026-07-10T13:00:00.000Z",
              "activity": {
                "topCandidate": { "type": "WALKING" },
                "distanceMeters": 500.0,
                "start": { "latLng": "37.5665°, 126.9780°" },
                "end": { "latLng": "37.5700°, 126.9800°" }
              }
            },
            {
              "startTime": "2026-07-10T13:00:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeId": "place_trunca
        """.trimIndent() // Truncated mid-stream

        val parser = GoogleTimelineJsonParser()
        val result = parser.parse(ByteArrayInputStream(truncatedJson.toByteArray()))

        assertEquals(TimelineParseStatus.PARTIAL_WITH_WARNINGS, result.status)
        assertEquals("Should have recovered 1 valid visit", 1, result.visits.size)
        assertEquals("Should have recovered 1 valid movement segment", 1, result.movementSegments.size)
        assertTrue("Warnings should mention truncation or incomplete stream", result.warnings.any { it.contains("incomplete", ignoreCase = true) || it.contains("truncated", ignoreCase = true) || it.contains("EOF", ignoreCase = true) })
    }

    @Test
    fun testTotallyUnsupportedValidJson_ReturnsFatalUnsupportedFormat() = runBlocking {
        val unsupportedJson = """
        {
          "users": [
            { "id": 1, "name": "Alice" },
            { "id": 2, "name": "Bob" }
          ],
          "settings": {
            "theme": "dark",
            "notifications": true
          }
        }
        """.trimIndent()

        val parser = GoogleTimelineJsonParser()
        val result = parser.parse(ByteArrayInputStream(unsupportedJson.toByteArray()))

        assertEquals(TimelineParseStatus.FATAL_UNSUPPORTED_FORMAT, result.status)
        assertTrue(result.visits.isEmpty())
        assertTrue(result.movementSegments.isEmpty())
        assertNotNull(result.errorMessage)
    }

    @Test
    fun testTotallyCorruptedInput_ReturnsFatalMalformedInput() = runBlocking {
        val garbageInput = "THIS IS NOT JSON AT ALL {{{ [[ 12345"

        val parser = GoogleTimelineJsonParser()
        val result = parser.parse(ByteArrayInputStream(garbageInput.toByteArray()))

        assertEquals(TimelineParseStatus.FATAL_MALFORMED_INPUT, result.status)
        assertTrue(result.visits.isEmpty())
        assertTrue(result.movementSegments.isEmpty())
        assertNotNull(result.errorMessage)
    }
}
