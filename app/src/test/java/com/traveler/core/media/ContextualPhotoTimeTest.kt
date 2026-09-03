package com.traveler.core.media

import com.traveler.core.classifier.RuleBasedTransportClassifier
import com.traveler.core.common.geo.GeoPoint
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class ContextualPhotoTimeTest {

    private class MockTripRepo : TripRepository {
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
        override suspend fun getDurableUserOverrides(): List<UserOverrideEntity> = emptyList()
    }

    @Test
    fun testContextualTimezoneResolution_ResolvesSeoulPhotoWhenDeviceIsInNewYork() = runBlocking {
        val originalTz = TimeZone.getDefault()
        try {
            // Set device default timezone to America/New_York
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            // Timeline export with a Seoul visit from 14:00 to 16:00 KST on July 10, 2026
            // 14:00 KST = 05:00 UTC (2026-07-10T05:00:00Z)
            // 16:00 KST = 07:00 UTC (2026-07-10T07:00:00Z)
            val timelineJson = """
            {
              "semanticSegments": [
                {
                  "startTime": "2026-07-10T05:00:00.000Z",
                  "endTime": "2026-07-10T07:00:00.000Z",
                  "visit": {
                    "topCandidate": {
                      "placeId": "seoul_tower",
                      "placeName": "N Seoul Tower",
                      "placeLocation": { "latLng": "37.5512°, 126.9882°" }
                    }
                  }
                }
              ]
            }
            """.trimIndent()

            // Photo taken in Seoul at 15:00 local time (2026:07:10 15:00:00)
            // Missing EXIF offset, missing GPS, missing MediaStore DATE_TAKEN
            val candidate = RawMediaCandidate(
                id = "IMG_SEOUL_01",
                contentUriString = "content://media/external/images/media/100",
                fileName = "IMG_20260710_150000.jpg",
                mimeType = "image/jpeg",
                exifDateTimeOriginal = "2026:07:10 15:00:00",
                exifOffset = null,
                mediaStoreDateTaken = null,
                fileDateModifiedMs = null,
                directGps = null
            )

            val mediaRepo = object : MediaRepository {
                override suspend fun queryMediaCandidatesForDateRange(startTimestampEpochMs: Long, endTimestampEpochMs: Long): List<RawMediaCandidate> {
                    return listOf(candidate)
                }
            }

            val useCase = CreateTripUseCase(
                locationHistorySource = GoogleTimelineJsonParser(),
                mediaRepository = mediaRepo,
                transportClassifier = RuleBasedTransportClassifier(),
                tripRepository = MockTripRepo()
            )

            val trip = useCase.execute(
                timelineStream = ByteArrayInputStream(timelineJson.toByteArray()),
                startDate = LocalDate.of(2026, 7, 10),
                endDate = LocalDate.of(2026, 7, 10)
            )

            assertNotNull(trip)
            assertEquals(1, trip.days.size)
            val day = trip.days[0]
            val visitItem = day.items[0] as TripDayItem.VisitItem
            assertEquals("N Seoul Tower", visitItem.visit.placeName)
            assertEquals("Photo must be contextually matched to N Seoul Tower visit", 1, visitItem.photos.size)

            val matchedPhoto = visitItem.photos[0]
            assertEquals("IMG_SEOUL_01", matchedPhoto.id)

            // 15:00 KST corresponds to 06:00:00 UTC (1783663200000 ms)
            // If it had been incorrectly interpreted in New York (EDT = UTC-4), it would be 19:00:00 UTC (1783710000000 ms)
            val expectedUtcInstant = Instant.parse("2026-07-10T06:00:00Z")
            assertEquals(expectedUtcInstant.toEpochMilli(), matchedPhoto.timestampEpochMs)
            assertEquals("Asia/Seoul", matchedPhoto.captureTimezoneId)
            assertEquals(LocationConfidenceLevel.VISIT_INFERRED, matchedPhoto.locationConfidence)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun testValidAbsoluteMediaStoreDateTaken_IsNotOverriddenBySpeculativeConversion() = runBlocking {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            val explicitEpoch = Instant.parse("2026-07-10T06:00:00Z").toEpochMilli() // 15:00 KST

            val candidate = RawMediaCandidate(
                id = "IMG_ABSOLUTE_01",
                contentUriString = "content://media/external/images/media/101",
                fileName = "IMG_photo.jpg",
                mimeType = "image/jpeg",
                exifDateTimeOriginal = "2026:07:10 15:00:00",
                exifOffset = null,
                mediaStoreDateTaken = explicitEpoch,
                fileDateModifiedMs = explicitEpoch,
                directGps = null
            )

            val result = PhotoTimestampResolver.resolve(
                candidate = candidate,
                candidateTripTimezones = listOf(ZoneId.of("Asia/Seoul"))
            )

            assertEquals(TimestampConfidence.MEDIASTORE, result.confidence)
            assertEquals(explicitEpoch, result.timestampEpochMs)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun testUnknownWallClockWithoutContext_ReturnsUnknownAndNullTimestamp() {
        // P1-04: EXIF local wall clock with NO offset, NO GPS, and NO candidate trip timezones
        val candidate = RawMediaCandidate(
            id = "IMG_NO_CONTEXT",
            contentUriString = "content://media/external/images/media/102",
            fileName = "IMG_20260710_150000.jpg",
            mimeType = "image/jpeg",
            exifDateTimeOriginal = "2026:07:10 15:00:00",
            exifOffset = null,
            mediaStoreDateTaken = null,
            fileDateModifiedMs = null,
            directGps = null
        )

        val result = PhotoTimestampResolver.resolve(
            candidate = candidate,
            candidateTripTimezones = emptyList() // No trip timezone context available
        )

        assertEquals(TimestampConfidence.UNKNOWN, result.confidence)
        assertNull("timestampEpochMs must be NULL when timezone is unknown (never fake UTC)", result.timestampEpochMs)
        assertNull("resolvedZoneId must be NULL", result.resolvedZoneId)
    }
}
