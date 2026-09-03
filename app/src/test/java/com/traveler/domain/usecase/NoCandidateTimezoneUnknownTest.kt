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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class NoCandidateTimezoneUnknownTest {

    private lateinit var database: TravelerDatabase
    private lateinit var tripRepository: TripRepositoryImpl

    private val fakeNullZoneResolver = object : TimezoneResolver {
        override suspend fun resolve(point: GeoPoint?): TimezoneResolution = TimezoneResolution.Unavailable
        override fun resolveSync(point: GeoPoint?): TimezoneResolution = TimezoneResolution.Unavailable
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
    fun zeroCandidateTripZones_yieldsUnknownConfidence_notUtcConsensus() = runTest {
        val photoEpochMs = Instant.parse("2026-07-02T15:00:00Z").toEpochMilli()

        val mediaRepo = object : MediaRepository {
            override suspend fun queryMediaCandidatesForDateRange(startTimestampEpochMs: Long, endTimestampEpochMs: Long): List<RawMediaCandidate> {
                return listOf(
                    RawMediaCandidate(
                        id = "no_candidate_photo",
                        contentUriString = "content://media/external/images/media/2001",
                        fileName = "IMG_no_zone.jpg",
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

        // Timeline with visit whose location has unresolved timezone
        val timelineJson = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-02T10:00:00Z",
              "endTime": "2026-07-02T12:00:00Z",
              "visit": {
                "topCandidate": {
                  "placeId": "ocean_boat_visit",
                  "placeName": "Mid Ocean Station",
                  "probability": 0.95,
                  "placeLocation": { "latLng": "10.5°, 20.5°" }
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
            timezoneResolver = fakeNullZoneResolver
        )

        val createdTrip = useCase.execute(
            timelineStream = ByteArrayInputStream(timelineJson.toByteArray(Charsets.UTF_8)),
            startDate = LocalDate.of(2026, 7, 2),
            endDate = LocalDate.of(2026, 7, 2),
            customTitle = "Empty Candidate Trip"
        )

        // P0-02: Zero candidate timezones means DayAssignmentConfidence.UNKNOWN and assignedDayIso = null
        assertEquals(1, createdTrip.uncertainDateMedia.size)
        val photo = createdTrip.uncertainDateMedia[0]
        assertEquals("no_candidate_photo", photo.id)
        assertNull(photo.assignedDayIso)
        assertEquals(DayAssignmentConfidence.UNKNOWN, photo.dayAssignmentConfidence)
    }
}
