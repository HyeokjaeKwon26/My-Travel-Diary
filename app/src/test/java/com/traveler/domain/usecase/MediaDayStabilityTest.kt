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
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MediaDayStabilityTest {

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
    fun unassignedMediaWithGpsLessTimestamp_retainsExactCalendarDayAcrossRoomReload() = runTest {
        // Timestamp: 2026-07-02 00:30:00 EDT
        val ldt = java.time.LocalDateTime.parse("2026-07-02T00:30:00")
        val photoEpochMs = ldt.atOffset(java.time.ZoneOffset.of("-04:00")).toInstant().toEpochMilli()

        val mediaRepo = object : MediaRepository {
            override suspend fun queryMediaCandidatesForDateRange(startTimestampEpochMs: Long, endTimestampEpochMs: Long): List<RawMediaCandidate> {
                return listOf(
                    RawMediaCandidate(
                        id = "midnight_photo",
                        contentUriString = "content://media/external/images/media/999",
                        fileName = "IMG_20260702_003000.jpg",
                        mimeType = "image/jpeg",
                        exifDateTimeOriginal = "2026:07:02 00:30:00",
                        exifOffset = null, // No offset in EXIF
                        mediaStoreDateTaken = photoEpochMs,
                        fileDateModifiedMs = photoEpochMs,
                        directGps = null
                    )
                )
            }
        }

        val timelineJson = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-02T10:00:00-04:00",
              "endTime": "2026-07-02T12:00:00-04:00",
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

        val createdTrip = useCase.execute(
            timelineStream = ByteArrayInputStream(timelineJson.toByteArray(Charsets.UTF_8)),
            startDate = LocalDate.of(2026, 7, 2),
            endDate = LocalDate.of(2026, 7, 2),
            customTitle = "Day Stability Trip"
        )

        assertEquals(1, createdTrip.days.size)
        val createdDay = createdTrip.days[0]
        assertEquals("2026-07-02", createdDay.dateIso)
        assertEquals("Unassigned photo must be placed in 2026-07-02 day", 1, createdDay.unassignedPhotos.size)

        // Reload from Room Database
        val reloadedTrip = tripRepository.getTripById(createdTrip.id)
        assertNotNull(reloadedTrip)
        assertEquals(1, reloadedTrip!!.days.size)
        val reloadedDay = reloadedTrip.days[0]
        assertEquals("2026-07-02", reloadedDay.dateIso)
        assertEquals("Unassigned photo must remain stably in 2026-07-02 day after Room reload", 1, reloadedDay.unassignedPhotos.size)
        assertEquals("midnight_photo", reloadedDay.unassignedPhotos[0].id)
    }
}
