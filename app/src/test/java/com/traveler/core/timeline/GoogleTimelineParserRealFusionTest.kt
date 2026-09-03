package com.traveler.core.timeline

import com.traveler.core.classifier.RuleBasedTransportClassifier
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.GeometryProvenance
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.TransportMode
import com.traveler.core.model.TransportPrediction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class GoogleTimelineParserRealFusionTest {

    private val parser = GoogleTimelineJsonParser()
    private val classifier = RuleBasedTransportClassifier()

    @Test
    fun caseA_enclosingTimelinePathAndNestedSemanticActivity_fusesIntoOneCarMovement() = runBlocking {
        // CASE A: 09:00-11:00 timelinePath container + 10:29-10:46 IN_PASSENGER_VEHICLE activity inside it
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-08-16T09:00:00.000-04:00",
              "endTime": "2026-08-16T11:00:00.000-04:00",
              "timelinePath": [
                {
                  "point": { "latLng": "40.34500°, -74.65000°" },
                  "time": "2026-08-16T10:29:18.000-04:00"
                },
                {
                  "point": { "latLng": "40.39000°, -74.55000°" },
                  "time": "2026-08-16T10:37:00.000-04:00"
                },
                {
                  "point": { "latLng": "40.45000°, -74.45000°" },
                  "time": "2026-08-16T10:46:27.000-04:00"
                }
              ]
            },
            {
              "startTime": "2026-08-16T10:29:18.000-04:00",
              "endTime": "2026-08-16T10:46:27.000-04:00",
              "activity": {
                "topCandidate": {
                  "type": "IN_PASSENGER_VEHICLE",
                  "probability": 0.944
                },
                "distanceMeters": 14399.0,
                "start": { "latLng": "40.34500°, -74.65000°" },
                "end": { "latLng": "40.45000°, -74.45000°" }
              }
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))

        // Must produce exactly ONE movement segment (no duplicate 2-hour Running segment)
        assertEquals("Must produce exactly 1 fused movement segment", 1, result.movementSegments.size)

        val seg = result.movementSegments[0]
        assertEquals(TransportMode.CAR, seg.transport.mode)
        assertEquals(14399.0, seg.distanceMeters, 1.0)

        // Duration must be 17 minutes 9 seconds (~1029 seconds = 1029000 ms), NOT 2 hours (7200000 ms)
        val expectedDurationMs = 17 * 60_000L + 9_000L
        assertEquals(expectedDurationMs, seg.durationMillis)

        // Geometry provenance must be OBSERVED and contain fused intermediate points
        assertEquals(GeometryProvenance.OBSERVED, seg.geometryProvenance)
        assertTrue("Must contain intermediate points from timelinePath", seg.simplifiedPoints.size >= 3)
        assertTrue("Must contain timestamped raw points", seg.rawPoints.size >= 3)
    }

    @Test
    fun caseB_activitySpanningMultipleTimelinePathChunks_fusesGeometryAcrossChunkBoundary() = runBlocking {
        // CASE B: Activity from 07:58 to 10:06 spanning 07:00-09:00 and 09:00-11:00 chunks
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-08-16T07:00:00.000-04:00",
              "endTime": "2026-08-16T09:00:00.000-04:00",
              "timelinePath": [
                {
                  "point": { "latLng": "40.00000°, -74.00000°" },
                  "time": "2026-08-16T08:00:00.000-04:00"
                },
                {
                  "point": { "latLng": "40.20000°, -74.20000°" },
                  "time": "2026-08-16T08:45:00.000-04:00"
                }
              ]
            },
            {
              "startTime": "2026-08-16T09:00:00.000-04:00",
              "endTime": "2026-08-16T11:00:00.000-04:00",
              "timelinePath": [
                {
                  "point": { "latLng": "40.40000°, -74.40000°" },
                  "time": "2026-08-16T09:30:00.000-04:00"
                },
                {
                  "point": { "latLng": "40.60000°, -74.60000°" },
                  "time": "2026-08-16T10:00:00.000-04:00"
                }
              ]
            },
            {
              "startTime": "2026-08-16T07:58:00.000-04:00",
              "endTime": "2026-08-16T10:06:00.000-04:00",
              "activity": {
                "topCandidate": {
                  "type": "IN_BUS",
                  "probability": 0.88
                },
                "distanceMeters": 85000.0,
                "start": { "latLng": "40.00000°, -74.00000°" },
                "end": { "latLng": "40.60000°, -74.60000°" }
              }
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))

        assertEquals(1, result.movementSegments.size)
        val seg = result.movementSegments[0]
        assertEquals(TransportMode.BUS, seg.transport.mode)
        assertEquals(85000.0, seg.distanceMeters, 1.0)
        assertEquals(GeometryProvenance.OBSERVED, seg.geometryProvenance)

        // Points from both chunks should be present
        assertTrue("Must contain points from both 07:00-09:00 and 09:00-11:00 chunks", seg.simplifiedPoints.size >= 4)
    }

    @Test
    fun caseC_timelinePathDuringVisit_doesNotCreateMovementSegment() = runBlocking {
        // CASE C: timelinePath observations inside a Visit
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-08-16T12:00:00.000-04:00",
              "endTime": "2026-08-16T14:00:00.000-04:00",
              "visit": {
                "topCandidate": {
                  "placeId": "place_princeton",
                  "placeName": "Princeton University",
                  "probability": 0.95,
                  "placeLocation": {
                    "latLng": "40.34400°, -74.65140°"
                  }
                }
              }
            },
            {
              "startTime": "2026-08-16T12:00:00.000-04:00",
              "endTime": "2026-08-16T14:00:00.000-04:00",
              "timelinePath": [
                {
                  "point": { "latLng": "40.34405°, -74.65142°" },
                  "time": "2026-08-16T12:30:00.000-04:00"
                },
                {
                  "point": { "latLng": "40.34410°, -74.65138°" },
                  "time": "2026-08-16T13:15:00.000-04:00"
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))

        assertEquals(1, result.visits.size)
        assertEquals("Princeton University", result.visits[0].placeName)
        assertEquals(0, result.movementSegments.size) // P0-05: NO Movement created
    }

    @Test
    fun caseD_trulyUncoveredTimelinePath_createsFallbackMovement() = runBlocking {
        // CASE D: Useful timelinePath movement with NO semantic activity or visit
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-08-16T15:00:00.000-04:00",
              "endTime": "2026-08-16T15:30:00.000-04:00",
              "timelinePath": [
                {
                  "point": { "latLng": "40.00000°, -74.00000°" },
                  "time": "2026-08-16T15:00:00.000-04:00"
                },
                {
                  "point": { "latLng": "40.05000°, -74.05000°" },
                  "time": "2026-08-16T15:15:00.000-04:00"
                },
                {
                  "point": { "latLng": "40.10000°, -74.10000°" },
                  "time": "2026-08-16T15:30:00.000-04:00"
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))

        assertEquals(0, result.visits.size)
        assertEquals(1, result.movementSegments.size) // P0-06: Fallback created
        val seg = result.movementSegments[0]
        assertEquals(TransportMode.UNKNOWN, seg.transport.mode)
        assertTrue(seg.distanceMeters > 5000.0)
    }

    @Test
    fun caseE_duplicateVisitCandidates_canonicalizesToOnePrimaryVisit() = runBlocking {
        // CASE E: Two visit candidates for the exact same interval (alternative place hypotheses)
        val json = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-08-16T18:00:00.000-04:00",
              "endTime": "2026-08-16T19:30:00.000-04:00",
              "visit": {
                "topCandidate": {
                  "placeId": "place_low_conf",
                  "placeName": "Generic Store",
                  "probability": 0.40,
                  "placeLocation": {
                    "latLng": "40.50000°, -74.50000°"
                  }
                }
              }
            },
            {
              "startTime": "2026-08-16T18:00:00.000-04:00",
              "endTime": "2026-08-16T19:30:00.000-04:00",
              "visit": {
                "topCandidate": {
                  "placeId": "place_high_conf",
                  "placeName": "Fine Dining Restaurant",
                  "probability": 0.92,
                  "placeLocation": {
                    "latLng": "40.50020°, -74.50010°"
                  }
                }
              }
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray()))

        assertEquals("Must deduplicate to exactly 1 canonical visit", 1, result.visits.size)
        assertEquals("Fine Dining Restaurant", result.visits[0].placeName)
        assertEquals("place_high_conf", result.visits[0].placeId)
        assertEquals(0.92f, result.visits[0].confidence, 0.001f)
    }

    @Test
    fun testTransportPolicy_preservesSpecificGoogleModesEvenAtLowConfidence() {
        // P0-10: 203km intercity IN_BUS at 0.51 confidence -> BUS (not CAR)
        val busSegment = MovementSegment(
            id = "bus_seg",
            startTimestampEpochMs = 1782800000000L,
            endTimestampEpochMs = 1782800000000L + 7200000L, // 2 hours -> ~101.8 km/h
            startPoint = GeoPoint(40.0, -74.0),
            endPoint = GeoPoint(41.5, -74.0),
            distanceMeters = 203600.0,
            durationMillis = 7200000L,
            transport = TransportPrediction(
                mode = TransportMode.BUS,
                confidence = 0.514f,
                reason = "Google Timeline Semantic Activity: IN_BUS"
            )
        )

        val classified = classifier.classify(busSegment)
        assertEquals("Must preserve specific BUS mode at 0.51 confidence", TransportMode.BUS, classified.mode)
        assertEquals(0.514f, classified.confidence, 0.001f)

        // Low confidence FERRY -> FERRY
        val ferrySegment = MovementSegment(
            id = "ferry_seg",
            startTimestampEpochMs = 1782800000000L,
            endTimestampEpochMs = 1782800000000L + 3600000L,
            startPoint = GeoPoint(40.0, -74.0),
            endPoint = GeoPoint(40.2, -74.0),
            distanceMeters = 22000.0,
            durationMillis = 3600000L,
            transport = TransportPrediction(
                mode = TransportMode.FERRY,
                confidence = 0.40f,
                reason = "Google Timeline Semantic Activity: IN_FERRY"
            )
        )
        val classifiedFerry = classifier.classify(ferrySegment)
        assertEquals("Must preserve specific FERRY mode", TransportMode.FERRY, classifiedFerry.mode)

        // UNKNOWN at highway speed -> CAR
        val unknownSegment = MovementSegment(
            id = "unknown_seg",
            startTimestampEpochMs = 1782800000000L,
            endTimestampEpochMs = 1782800000000L + 3600000L,
            startPoint = GeoPoint(40.0, -74.0),
            endPoint = GeoPoint(40.8, -74.0),
            distanceMeters = 90000.0,
            durationMillis = 3600000L,
            transport = TransportPrediction(
                mode = TransportMode.UNKNOWN,
                confidence = 0.5f,
                reason = "Unknown"
            )
        )
        val classifiedUnknown = classifier.classify(unknownSegment)
        assertEquals("UNKNOWN at highway speed should be classified as CAR", TransportMode.CAR, classifiedUnknown.mode)
    }
}
