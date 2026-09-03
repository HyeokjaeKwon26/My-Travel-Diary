package com.traveler.core.media

import com.traveler.core.classifier.RuleBasedTransportClassifier
import com.traveler.core.database.entity.UserOverrideEntity
import com.traveler.core.model.*
import com.traveler.core.timeline.GoogleTimelineJsonParser
import com.traveler.domain.repository.TripRepository
import com.traveler.domain.usecase.CreateTripUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate

class UnassignedPhotosPreservationTest {

    private class InMemoryTripRepo : TripRepository {
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
        override suspend fun getDurableUserOverrides(): List<UserOverrideEntity> = emptyList()
    }

    @Test
    fun testUnassignedPhotos_NeverDisappearFromDiary() = runBlocking {
        // Timeline with 1 visit and 1 car movement on July 4, 2026
        val timelineJson = """
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
              "endTime": "2026-07-04T10:00:00.000Z",
              "activity": {
                "topCandidate": { "type": "IN_PASSENGER_VEHICLE", "probability": 0.95 },
                "distanceMeters": 35000.0,
                "simplifiedRawPath": {
                  "points": [
                    { "latLng": "42.3550°, -71.0656°", "timestamp": "2026-07-04T09:00:00.000Z" },
                    { "latLng": "42.5000°, -71.2000°", "timestamp": "2026-07-04T10:00:00.000Z" }
                  ]
                }
              }
            }
          ]
        }
        """.trimIndent()

        // 3 Photos:
        // Photo A: Taken at 08:30 UTC (inside Boston Common visit)
        // Photo B: Taken at 09:30 UTC (inside Car movement)
        // Photo C: Taken at 14:00 UTC (during the travel day, but no timeline visit/activity recorded)
        val candidateA = RawMediaCandidate(
            id = "PHOTO_A_VISIT",
            contentUriString = "content://media/1",
            fileName = "IMG_Visit.jpg",
            mimeType = "image/jpeg",
            exifDateTimeOriginal = "2026:07:04 08:30:00",
            exifOffset = "+00:00",
            mediaStoreDateTaken = 1783153800000L,
            fileDateModifiedMs = 1783153800000L,
            directGps = null
        )

        val candidateB = RawMediaCandidate(
            id = "PHOTO_B_SEGMENT",
            contentUriString = "content://media/2",
            fileName = "IMG_Car.jpg",
            mimeType = "image/jpeg",
            exifDateTimeOriginal = "2026:07:04 09:30:00",
            exifOffset = "+00:00",
            mediaStoreDateTaken = 1783157400000L,
            fileDateModifiedMs = 1783157400000L,
            directGps = null
        )

        val candidateC = RawMediaCandidate(
            id = "PHOTO_C_UNASSIGNED",
            contentUriString = "content://media/3",
            fileName = "IMG_Unassigned.jpg",
            mimeType = "image/jpeg",
            exifDateTimeOriginal = "2026:07:04 14:00:00",
            exifOffset = "+00:00",
            mediaStoreDateTaken = 1783173600000L,
            fileDateModifiedMs = 1783173600000L,
            directGps = null
        )

        val mediaRepo = object : MediaRepository {
            override suspend fun queryMediaCandidatesForDateRange(startTimestampEpochMs: Long, endTimestampEpochMs: Long): List<RawMediaCandidate> {
                return listOf(candidateA, candidateB, candidateC)
            }
        }

        val tripRepo = InMemoryTripRepo()
        val useCase = CreateTripUseCase(
            locationHistorySource = GoogleTimelineJsonParser(),
            mediaRepository = mediaRepo,
            transportClassifier = RuleBasedTransportClassifier(),
            tripRepository = tripRepo
        )

        val trip = useCase.execute(
            timelineStream = ByteArrayInputStream(timelineJson.toByteArray()),
            startDate = LocalDate.of(2026, 7, 4),
            endDate = LocalDate.of(2026, 7, 4)
        )

        assertNotNull(trip)
        assertEquals("Total media count must include all 3 photos", 3, trip.totalMediaCount)
        assertEquals(1, trip.days.size)

        val day = trip.days[0]
        assertEquals(3, day.photoCount)

        // Photo A in Visit
        val visitItem = day.items.filterIsInstance<TripDayItem.VisitItem>().first()
        assertEquals(1, visitItem.photos.size)
        assertEquals("PHOTO_A_VISIT", visitItem.photos[0].id)

        // Photo B in Movement
        val movementItem = day.items.filterIsInstance<TripDayItem.MovementItem>().first()
        assertEquals(1, movementItem.photos.size)
        assertEquals("PHOTO_B_SEGMENT", movementItem.photos[0].id)

        // Photo C in UnassignedPhotos
        assertEquals("Unassigned photo must be preserved in day.unassignedPhotos", 1, day.unassignedPhotos.size)
        assertEquals("PHOTO_C_UNASSIGNED", day.unassignedPhotos[0].id)

        // Also verify UnassignedPhotosItem exists in day.items for UI rendering
        val unassignedItem = day.items.filterIsInstance<TripDayItem.UnassignedPhotosItem>().firstOrNull()
        assertNotNull("UnassignedPhotosItem should be present in day.items", unassignedItem)
        assertEquals(1, unassignedItem!!.photos.size)
        assertEquals("PHOTO_C_UNASSIGNED", unassignedItem.photos[0].id)
    }
}
