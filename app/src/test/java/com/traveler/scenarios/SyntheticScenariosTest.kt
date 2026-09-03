package com.traveler.scenarios

import com.traveler.core.classifier.RuleBasedTransportClassifier
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import com.traveler.core.media.MediaRepository
import com.traveler.core.media.PhotoTimestampResolver
import com.traveler.core.media.RawMediaCandidate
import com.traveler.core.timeline.GoogleTimelineJsonParser
import com.traveler.domain.repository.TripRepository
import com.traveler.domain.usecase.CreateTripUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate

class SyntheticScenariosTest {

    private class TestTripRepo : TripRepository {
        var trip: Trip? = null
        override fun getAllTrips(): Flow<List<Trip>> = flowOf(listOfNotNull(trip))
        override suspend fun getTripById(tripId: String): Trip? = trip
        override suspend fun saveTrip(trip: Trip) { this.trip = trip }
        override suspend fun deleteTrip(tripId: String) { this.trip = null }
        override suspend fun updateTransportMode(tripId: String, segmentSourceId: String, mode: TransportMode) {}
        override suspend fun updateVisitName(tripId: String, visitSourceId: String, name: String) {}
        override suspend fun updateMediaVisit(tripId: String, mediaKey: String, visitSourceId: String?) {}
        override suspend fun setRepresentativeMedia(tripId: String, mediaKey: String, isRepresentative: Boolean) {}
        override suspend fun cleanOrphanTripData(): com.traveler.domain.repository.OrphanCleanupSummary = com.traveler.domain.repository.OrphanCleanupSummary(0, 0, 0)
        override suspend fun getDurableUserOverrides(): List<com.traveler.core.database.entity.UserOverrideEntity> = emptyList()
    }

    /**
     * Scenario A — Local Day Trip:
     * 08:00 Home -> 08:20 Car -> 09:10 Museum -> 12:00 Walk -> 12:15 Restaurant -> 14:30 Car -> 16:00 Home
     */
    @Test
    fun testScenarioA_LocalDayTrip() = runBlocking {
        val scenarioAJson = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-10T08:00:00.000Z",
              "endTime": "2026-07-10T08:20:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeId": "home",
                  "placeName": "Home",
                  "placeLocation": { "latLng": "42.3500°, -71.0800°" }
                }
              }
            },
            {
              "startTime": "2026-07-10T08:20:00.000Z",
              "endTime": "2026-07-10T09:10:00.000Z",
              "activity": {
                "topCandidate": { "type": "IN_PASSENGER_VEHICLE" },
                "distanceMeters": 25000.0,
                "simplifiedRawPath": {
                  "points": [
                    { "latLng": "42.3500°, -71.0800°", "timestamp": "2026-07-10T08:20:00.000Z" },
                    { "latLng": "42.3400°, -71.0900°", "timestamp": "2026-07-10T09:10:00.000Z" }
                  ]
                }
              }
            },
            {
              "startTime": "2026-07-10T09:10:00.000Z",
              "endTime": "2026-07-10T12:00:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeId": "museum",
                  "placeName": "Museum of Fine Arts",
                  "placeLocation": { "latLng": "42.3400°, -71.0900°" }
                }
              }
            },
            {
              "startTime": "2026-07-10T12:00:00.000Z",
              "endTime": "2026-07-10T12:15:00.000Z",
              "activity": {
                "topCandidate": { "type": "WALKING" },
                "distanceMeters": 800.0,
                "simplifiedRawPath": {
                  "points": [
                    { "latLng": "42.3400°, -71.0900°", "timestamp": "2026-07-10T12:00:00.000Z" },
                    { "latLng": "42.3420°, -71.0880°", "timestamp": "2026-07-10T12:15:00.000Z" }
                  ]
                }
              }
            },
            {
              "startTime": "2026-07-10T12:15:00.000Z",
              "endTime": "2026-07-10T14:30:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeId": "restaurant",
                  "placeName": "Italian Bistro",
                  "placeLocation": { "latLng": "42.3420°, -71.0880°" }
                }
              }
            }
          ]
        }
        """.trimIndent()

        val candidates = listOf(
            RawMediaCandidate(
                id = "IMG_1",
                contentUriString = "content://media/1",
                fileName = "IMG_Museum.jpg",
                mimeType = "image/jpeg",
                exifDateTimeOriginal = "2026:07:10 10:00:00",
                exifOffset = "+00:00",
                mediaStoreDateTaken = 1783677600000L, // 10:00 UTC (inside Museum stay)
                fileDateModifiedMs = 1783677600000L,
                directGps = null
            ),
            RawMediaCandidate(
                id = "IMG_2",
                contentUriString = "content://media/2",
                fileName = "IMG_Pasta.jpg",
                mimeType = "image/jpeg",
                exifDateTimeOriginal = "2026:07:10 13:00:00",
                exifOffset = "+00:00",
                mediaStoreDateTaken = 1783688400000L, // 13:00 UTC (inside Restaurant stay)
                fileDateModifiedMs = 1783688400000L,
                directGps = null
            )
        )

        val fakeMediaRepo = object : MediaRepository {
            override suspend fun queryMediaCandidatesForDateRange(startTimestampEpochMs: Long, endTimestampEpochMs: Long) = candidates
        }

        val useCase = CreateTripUseCase(
            locationHistorySource = GoogleTimelineJsonParser(),
            mediaRepository = fakeMediaRepo,
            transportClassifier = RuleBasedTransportClassifier(),
            tripRepository = TestTripRepo()
        )

        val trip = useCase.execute(
            timelineStream = ByteArrayInputStream(scenarioAJson.toByteArray()),
            startDate = LocalDate.of(2026, 7, 10),
            endDate = LocalDate.of(2026, 7, 10)
        )

        assertNotNull(trip)
        assertEquals(1, trip.days.size)
        val day = trip.days.first()
        assertEquals(5, day.items.size) // 3 visits, 2 movements

        val museumVisit = day.items[2] as TripDayItem.VisitItem
        assertEquals("Museum of Fine Arts", museumVisit.visit.placeName)
        assertEquals(1, museumVisit.photos.size)
        assertEquals(LocationConfidenceLevel.VISIT_INFERRED, museumVisit.photos[0].locationConfidence)
    }

    /**
     * Scenario B — Flight with Long GPS Gap:
     * 07:00 Manhattan -> 07:45 JFK -> 09:30 Departure -> GPS Gap -> 22:00 Seoul Arrival -> 23:00 Hotel
     */
    @Test
    fun testScenarioB_InternationalFlight() {
        val classifier = RuleBasedTransportClassifier()

        val jfk = GeoPoint(40.6413, -73.7781)
        val incheon = GeoPoint(37.4602, 126.4407)
        val durationMs = 14 * 3600 * 1000L // 14 hours

        val flightSegment = MovementSegment(
            id = "flight-ny-seoul",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + durationMs,
            startPoint = jfk,
            endPoint = incheon,
            distanceMeters = 11000_000.0, // 11,000 km
            durationMillis = durationMs,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0f, "")
        )

        val prediction = classifier.classify(flightSegment)
        assertEquals(TransportMode.AIRPLANE, prediction.mode)
        assertTrue(prediction.confidence >= 0.85f)
    }

    /**
     * Scenario C & D — Weak / Missing EXIF Metadata fallback
     */
    @Test
    fun testScenarioCD_WeakMetadataFallback() {
        val result = PhotoTimestampResolver.resolve(
            exifDateTimeOriginal = null,
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileName = "IMG_20260714_143550.jpg",
            fileDateModifiedMs = 1783100000000L,
            candidateTripTimezones = listOf(java.time.ZoneId.of("Asia/Seoul"))
        )

        assertEquals(TimestampConfidence.FILENAME_INFERRED, result.confidence)
        val ts = result.timestampEpochMs
        assertTrue(ts != null && ts > 0L)
    }

    /**
     * Scenario E — Ambiguous transport profile
     */
    @Test
    fun testScenarioE_AmbiguousTransport() {
        val classifier = RuleBasedTransportClassifier()

        // 120 km over 100 min = 72 km/h (typical car range)
        val segment = MovementSegment(
            id = "ambiguous-1",
            startTimestampEpochMs = 1000000L,
            endTimestampEpochMs = 1000000L + (100 * 60 * 1000L),
            startPoint = GeoPoint(40.0, -74.0),
            endPoint = GeoPoint(40.5, -73.5),
            distanceMeters = 120000.0,
            durationMillis = 100 * 60 * 1000L,
            transport = TransportPrediction(TransportMode.UNKNOWN, 0f, "")
        )

        val prediction = classifier.classify(segment)
        assertEquals(TransportMode.CAR, prediction.mode)
    }
}
