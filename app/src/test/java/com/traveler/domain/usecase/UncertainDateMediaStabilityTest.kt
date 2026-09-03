package com.traveler.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.traveler.core.classifier.RuleBasedTransportClassifier
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.time.TimezoneResolution
import com.traveler.core.common.time.TimezoneResolver
import com.traveler.core.database.TravelerDatabase
import com.traveler.core.media.MediaRepository
import com.traveler.core.media.RawMediaCandidate
import com.traveler.core.model.DayAssignmentConfidence
import com.traveler.core.timeline.GoogleTimelineJsonParser
import com.traveler.data.repository.TripRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class UncertainDateMediaStabilityTest {

    private lateinit var database: TravelerDatabase
    private lateinit var tripRepository: TripRepositoryImpl

    private val fakeMultiZoneResolver = object : TimezoneResolver {
        override suspend fun resolve(point: GeoPoint?): TimezoneResolution {
            return if ((point?.longitude ?: 0.0) < 0) {
                TimezoneResolution.Resolved(ZoneId.of("America/New_York"))
            } else {
                TimezoneResolution.Resolved(ZoneId.of("Asia/Seoul"))
            }
        }
        override fun resolveSync(point: GeoPoint?): TimezoneResolution {
            return if ((point?.longitude ?: 0.0) < 0) {
                TimezoneResolution.Resolved(ZoneId.of("America/New_York"))
            } else {
                TimezoneResolution.Resolved(ZoneId.of("Asia/Seoul"))
            }
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TravelerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tripRepository = TripRepositoryImpl(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ambiguousDatePhoto_remainsInUncertainDateMedia_acrossCreationAndRoomReload() = runTest {
        // Timestamp: 2026-07-02 21:00:00 UTC
        // In America/New_York (UTC-4): 2026-07-02 17:00:00 (July 2)
        // In Asia/Seoul (UTC+9): 2026-07-03 06:00:00 (July 3) -> CONFLICTING DATES
        val photoEpochMs = Instant.parse("2026-07-02T21:00:00Z").toEpochMilli()

        val mediaRepo = object : MediaRepository {
            override suspend fun queryMediaCandidatesForDateRange(startTimestampEpochMs: Long, endTimestampEpochMs: Long): List<RawMediaCandidate> {
                return listOf(
                    RawMediaCandidate(
                        id = "ambiguous_photo_1",
                        contentUriString = "content://media/external/images/media/1001",
                        fileName = "IMG_ambiguous.jpg",
                        mimeType = "image/jpeg",
                        exifDateTimeOriginal = null,
                        exifOffset = null,
                        mediaStoreDateTaken = photoEpochMs,
                        fileDateModifiedMs = photoEpochMs,
                        directGps = null
                    )
                )
            }
        }

        // Timeline includes NY visit and Seoul visit
        val timelineJson = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-02T08:00:00-04:00",
              "endTime": "2026-07-02T10:00:00-04:00",
              "visit": {
                "topCandidate": {
                  "placeId": "nyc_central_park",
                  "placeName": "Central Park",
                  "probability": 0.95,
                  "placeLocation": { "latLng": "40.785091°, -73.968285°" }
                }
              }
            },
            {
              "startTime": "2026-07-03T18:00:00+09:00",
              "endTime": "2026-07-03T20:00:00+09:00",
              "visit": {
                "topCandidate": {
                  "placeId": "seoul_tower",
                  "placeName": "N Seoul Tower",
                  "probability": 0.95,
                  "placeLocation": { "latLng": "37.551170°, 126.988228°" }
                }
              }
            }
          ]
        }
        """.trimIndent()

        val useCase = CreateTripUseCase(
            locationHistorySource = GoogleTimelineJsonParser(),
            mediaRepository = mediaRepo,
            transportClassifier = RuleBasedTransportClassifier(),
            tripRepository = tripRepository,
            timezoneResolver = fakeMultiZoneResolver
        )

        val createdTrip = useCase.execute(
            timelineStream = ByteArrayInputStream(timelineJson.toByteArray(Charsets.UTF_8)),
            startDate = LocalDate.of(2026, 7, 2),
            endDate = LocalDate.of(2026, 7, 3),
            customTitle = "Global Multi-Zone Trip"
        )

        // P0-01: Must NOT be forced into an arbitrary day. Must be in uncertainDateMedia.
        assertEquals(1, createdTrip.uncertainDateMedia.size)
        val uncertPhoto = createdTrip.uncertainDateMedia[0]
        assertEquals("ambiguous_photo_1", uncertPhoto.id)
        assertNull(uncertPhoto.assignedDayIso)
        assertEquals(DayAssignmentConfidence.AMBIGUOUS, uncertPhoto.dayAssignmentConfidence)

        // Days should have zero unassigned photos
        for (day in createdTrip.days) {
            assertEquals("Day ${day.dateIso} should not contain ambiguous photo", 0, day.unassignedPhotos.size)
        }

        // Reload from Room database
        val reloadedTrip = tripRepository.getTripById(createdTrip.id)
        assertNotNull(reloadedTrip)
        assertEquals("Must preserve uncertainDateMedia across Room reload", 1, reloadedTrip!!.uncertainDateMedia.size)
        val reloadedPhoto = reloadedTrip.uncertainDateMedia[0]
        assertEquals("ambiguous_photo_1", reloadedPhoto.id)
        assertNull(reloadedPhoto.assignedDayIso)
        assertEquals(DayAssignmentConfidence.AMBIGUOUS, reloadedPhoto.dayAssignmentConfidence)

        for (day in reloadedTrip.days) {
            assertEquals("Day ${day.dateIso} should not contain ambiguous photo after reload", 0, day.unassignedPhotos.size)
        }
    }
}
