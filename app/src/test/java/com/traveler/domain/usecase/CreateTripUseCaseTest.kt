package com.traveler.domain.usecase

import com.traveler.core.classifier.RuleBasedTransportClassifier
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import com.traveler.core.media.MediaRepository
import com.traveler.core.timeline.GoogleTimelineJsonParser
import com.traveler.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate

class CreateTripUseCaseTest {

    private val fakeTimelineJson = """
    {
      "semanticSegments": [
        {
          "startTime": "2026-07-04T08:00:00.000Z",
          "endTime": "2026-07-04T09:00:00.000Z",
          "visit": {
            "topCandidate": {
              "placeId": "ChIJ_Boston",
              "placeName": "Boston Harbor",
              "probability": 0.95,
              "placeLocation": {
                "latLng": "42.3601°, -71.0589°"
              }
            }
          }
        },
        {
          "startTime": "2026-07-04T09:00:00.000Z",
          "endTime": "2026-07-04T10:00:00.000Z",
          "activity": {
            "topCandidate": {
              "type": "IN_PASSENGER_VEHICLE",
              "probability": 0.90
            },
            "distanceMeters": 50000.0,
            "simplifiedRawPath": {
              "points": [
                {
                  "latLng": "42.3601°, -71.0589°",
                  "timestamp": "2026-07-04T09:00:00.000Z"
                },
                {
                  "latLng": "42.6334°, -71.3162°",
                  "timestamp": "2026-07-04T10:00:00.000Z"
                }
              ]
            }
          }
        },
        {
          "startTime": "2026-07-04T10:00:00.000Z",
          "endTime": "2026-07-04T12:00:00.000Z",
          "visit": {
            "topCandidate": {
              "placeId": "ChIJ_Lowell",
              "placeName": "Lowell National Park",
              "probability": 0.95,
              "placeLocation": {
                "latLng": "42.6334°, -71.3162°"
              }
            }
          }
        }
      ]
    }
    """.trimIndent()

    private class FakeMediaRepository : MediaRepository {
        override suspend fun queryMediaCandidatesForDateRange(startTimestampEpochMs: Long, endTimestampEpochMs: Long): List<com.traveler.core.media.RawMediaCandidate> {
            return listOf(
                com.traveler.core.media.RawMediaCandidate(
                    id = "IMG_101",
                    contentUriString = "content://media/101",
                    fileName = "IMG_20260704_083000.jpg",
                    mimeType = "image/jpeg",
                    exifDateTimeOriginal = "2026:07:04 08:30:00",
                    exifOffset = "+00:00",
                    mediaStoreDateTaken = 1783153800000L,
                    fileDateModifiedMs = 1783153800000L,
                    directGps = null
                ),
                com.traveler.core.media.RawMediaCandidate(
                    id = "IMG_102",
                    contentUriString = "content://media/102",
                    fileName = "IMG_20260704_093000.jpg",
                    mimeType = "image/jpeg",
                    exifDateTimeOriginal = "2026:07:04 09:30:00",
                    exifOffset = "+00:00",
                    mediaStoreDateTaken = 1783157400000L,
                    fileDateModifiedMs = 1783157400000L,
                    directGps = null
                )
            )
        }
    }

    private class FakeTripRepository : TripRepository {
        var savedTrip: Trip? = null
        override fun getAllTrips(): Flow<List<Trip>> = flowOf(listOfNotNull(savedTrip))
        override suspend fun getTripById(tripId: String): Trip? = savedTrip
        override suspend fun saveTrip(trip: Trip) { savedTrip = trip }
        override suspend fun deleteTrip(tripId: String) { savedTrip = null }
        override suspend fun updateTransportMode(tripId: String, segmentSourceId: String, mode: TransportMode) {}
        override suspend fun updateVisitName(tripId: String, visitSourceId: String, name: String) {}
        override suspend fun updateMediaVisit(tripId: String, mediaKey: String, visitSourceId: String?) {}
        override suspend fun setRepresentativeMedia(tripId: String, mediaKey: String, isRepresentative: Boolean) {}
        override suspend fun cleanOrphanTripData(): com.traveler.domain.repository.OrphanCleanupSummary = com.traveler.domain.repository.OrphanCleanupSummary(0, 0, 0)
        override suspend fun getDurableUserOverrides(): List<com.traveler.core.database.entity.UserOverrideEntity> = emptyList()
    }

    @Test
    fun testExecute_ReconstructsFullTripWithMatchedMedia() = runBlocking {
        val useCase = CreateTripUseCase(
            locationHistorySource = GoogleTimelineJsonParser(),
            mediaRepository = FakeMediaRepository(),
            transportClassifier = RuleBasedTransportClassifier(),
            tripRepository = FakeTripRepository()
        )

        val trip = useCase.execute(
            timelineStream = ByteArrayInputStream(fakeTimelineJson.toByteArray()),
            startDate = LocalDate.of(2026, 7, 4),
            endDate = LocalDate.of(2026, 7, 4)
        )

        assertNotNull(trip)
        assertTrue(trip.days.isNotEmpty())
        assertEquals(2, trip.totalMediaCount)
        assertTrue(trip.totalDistanceMeters >= 50000.0)

        val day = trip.days.first()
        // Check items: Visit (Boston Harbor), Movement (Car), Visit (Lowell National Park)
        assertEquals(3, day.items.size)

        val firstVisit = day.items[0] as TripDayItem.VisitItem
        assertEquals("Boston Harbor", firstVisit.visit.placeName)
        assertEquals(1, firstVisit.photos.size)
        assertEquals(LocationConfidenceLevel.VISIT_INFERRED, firstVisit.photos[0].locationConfidence)

        val movement = day.items[1] as TripDayItem.MovementItem
        assertEquals(TransportMode.CAR, movement.segment.transport.mode)
        assertEquals(1, movement.photos.size)
        assertEquals(LocationConfidenceLevel.TIMELINE_INTERPOLATED, movement.photos[0].locationConfidence)
    }
}
