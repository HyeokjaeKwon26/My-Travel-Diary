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
import com.traveler.core.model.TripDayItem
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class DurableVisitNameOverrideTest {

    private lateinit var database: TravelerDatabase
    private lateinit var tripRepository: TripRepositoryImpl

    private val fakeZoneResolver = object : TimezoneResolver {
        override suspend fun resolve(point: GeoPoint?): TimezoneResolution = TimezoneResolution.Unavailable
        override fun resolveSync(point: GeoPoint?): TimezoneResolution = TimezoneResolution.Unavailable
    }

    private val emptyMediaRepo = object : MediaRepository {
        override suspend fun queryMediaCandidatesForDateRange(startTimestampEpochMs: Long, endTimestampEpochMs: Long) = emptyList<com.traveler.core.media.RawMediaCandidate>()
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
    fun reimportedTrip_appliesDurableVisitNameOverride() = runTest {
        val timelineJson = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-02T10:00:00Z",
              "endTime": "2026-07-02T12:00:00Z",
              "visit": {
                "topCandidate": {
                  "placeId": "hotel_room_101",
                  "placeName": "Generic Hotel",
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
            mediaRepository = emptyMediaRepo,
            transportClassifier = RuleBasedTransportClassifier(),
            tripRepository = tripRepository,
            timezoneResolver = fakeZoneResolver
        )

        // 1. Initial import
        val trip1 = useCase.execute(
            timelineStream = ByteArrayInputStream(timelineJson.toByteArray(Charsets.UTF_8)),
            startDate = LocalDate.of(2026, 7, 2),
            endDate = LocalDate.of(2026, 7, 2),
            customTitle = "Trip 1"
        )

        val visit1 = trip1.days.flatMap { it.items }.filterIsInstance<TripDayItem.VisitItem>().first().visit
        assertEquals("Generic Hotel", visit1.placeName)

        // 2. User edits visit name
        tripRepository.updateVisitName(trip1.id, visit1.id, "Grand Luxury Boston Suite")

        // Verify reloaded trip 1 has override
        val reloadedTrip1 = tripRepository.getTripById(trip1.id)
        val reloadedVisit1 = reloadedTrip1!!.days.flatMap { it.items }.filterIsInstance<TripDayItem.VisitItem>().first().visit
        assertEquals("Grand Luxury Boston Suite", reloadedVisit1.placeName)
        assertTrue(reloadedVisit1.isUserOverride)

        // 3. Re-import / recreate trip with same timeline
        val trip2 = useCase.execute(
            timelineStream = ByteArrayInputStream(timelineJson.toByteArray(Charsets.UTF_8)),
            startDate = LocalDate.of(2026, 7, 2),
            endDate = LocalDate.of(2026, 7, 2),
            customTitle = "Trip 2"
        )

        val visit2 = trip2.days.flatMap { it.items }.filterIsInstance<TripDayItem.VisitItem>().first().visit
        // P1-08: Durable override must be applied to newly created trip
        assertEquals("Grand Luxury Boston Suite", visit2.placeName)
        assertTrue(visit2.isUserOverride)
    }
}
