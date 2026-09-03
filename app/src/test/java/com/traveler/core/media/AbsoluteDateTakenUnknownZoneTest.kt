package com.traveler.core.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.traveler.core.classifier.RuleBasedTransportClassifier
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.time.TimezoneResolution
import com.traveler.core.common.time.TimezoneResolver
import com.traveler.core.database.TravelerDatabase
import com.traveler.core.model.DayAssignmentConfidence
import com.traveler.core.model.TimestampConfidence
import com.traveler.core.timeline.GoogleTimelineJsonParser
import com.traveler.data.repository.TripRepositoryImpl
import com.traveler.domain.usecase.CreateTripUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AbsoluteDateTakenUnknownZoneTest {

    private lateinit var database: TravelerDatabase
    private lateinit var tripRepository: TripRepositoryImpl

    private val fakeTimezoneResolver = object : TimezoneResolver {
        override suspend fun resolve(point: GeoPoint?): TimezoneResolution {
            return TimezoneResolution.Resolved(ZoneId.of("America/New_York"))
        }
        override fun resolveSync(point: GeoPoint?): TimezoneResolution {
            return TimezoneResolution.Resolved(ZoneId.of("America/New_York"))
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
    fun absoluteDateTaken_preservesTimestampAndNullZone_withConsensusDayAssignment() = runTest {
        // 2026-07-04 12:00:00 UTC = 1783166400000L
        // In America/New_York (UTC-4), it is 2026-07-04 08:00
        // Both timezones map to the SAME calendar date: 2026-07-04!
        val middayEpochMs = 1783166400000L

        val mediaRepo = object : MediaRepository {
            override suspend fun queryMediaCandidatesForDateRange(startTimestampEpochMs: Long, endTimestampEpochMs: Long): List<RawMediaCandidate> {
                return listOf(
                    RawMediaCandidate(
                        id = "photo_consensus",
                        contentUriString = "content://media/consensus",
                        fileName = "DATE_TAKEN_MIDDAY.jpg",
                        mimeType = "image/jpeg",
                        exifDateTimeOriginal = null,
                        exifOffset = null,
                        mediaStoreDateTaken = middayEpochMs,
                        fileDateModifiedMs = null,
                        directGps = null,
                        directGpsZoneId = null
                    )
                )
            }
        }

        val timelineJson = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-04T10:00:00-04:00",
              "endTime": "2026-07-04T12:00:00-04:00",
              "visit": {
                "topCandidate": {
                  "placeId": "boston_common",
                  "placeName": "Boston Common",
                  "probability": 0.95,
                  "placeLocation": { "latLng": "42.3550°, -71.0656°" }
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
            timezoneResolver = fakeTimezoneResolver
        )

        val trip = useCase.execute(
            timelineStream = ByteArrayInputStream(timelineJson.toByteArray(Charsets.UTF_8)),
            startDate = LocalDate.of(2026, 7, 4),
            endDate = LocalDate.of(2026, 7, 4),
            customTitle = "Consensus Test"
        )

        assertNotNull(trip)
        val day = trip.days.find { it.dateIso == "2026-07-04" }
        assertNotNull("Consensus photo must be placed on 2026-07-04", day)

        val unassignedPhoto = day!!.unassignedPhotos.firstOrNull()
        assertNotNull(unassignedPhoto)
        assertEquals(middayEpochMs, unassignedPhoto!!.timestampEpochMs)
        assertEquals(TimestampConfidence.MEDIASTORE, unassignedPhoto.timestampConfidence)
        assertNull("captureTimezoneId must remain null and not be fabricated", unassignedPhoto.captureTimezoneId)
        assertEquals("2026-07-04", unassignedPhoto.assignedDayIso)
        assertEquals(DayAssignmentConfidence.CONSENSUS, unassignedPhoto.dayAssignmentConfidence)
    }
}
