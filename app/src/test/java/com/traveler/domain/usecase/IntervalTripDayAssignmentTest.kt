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
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class IntervalTripDayAssignmentTest {

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

    private val emptyMediaRepo = object : MediaRepository {
        override suspend fun queryMediaCandidatesForDateRange(startEpochMs: Long, endEpochMs: Long): List<RawMediaCandidate> {
            return emptyList()
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
    fun multiDayEnclosingVisitAndOvernightMovement_retainedAndAssignedToClampedDate() = runTest {
        // Timeline containing:
        // 1. Multi-day Hotel Stay: July 1 22:00 -> July 3 08:00 (encloses July 2)
        // 2. Overnight Train: July 1 23:30 -> July 2 06:30 (crosses into July 2)
        val timelineJson = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-01T22:00:00-04:00",
              "endTime": "2026-07-03T08:00:00-04:00",
              "visit": {
                "topCandidate": {
                  "placeId": "hotel_niagara",
                  "placeName": "Niagara Grand Hotel",
                  "probability": 0.95,
                  "placeLocation": { "latLng": "43.0896°, -79.0849°" }
                }
              }
            },
            {
              "startTime": "2026-07-01T23:30:00-04:00",
              "endTime": "2026-07-02T06:30:00-04:00",
              "activity": {
                "topCandidate": { "type": "IN_PASSENGER_VEHICLE", "probability": 0.90 },
                "distanceMeters": 450000.0,
                "start": { "latLng": "42.3503°, -71.0810°" },
                "end": { "latLng": "43.0896°, -79.0849°" }
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
            timezoneResolver = fakeTimezoneResolver
        )

        // Trip selected ONLY for July 2
        val trip = useCase.execute(
            timelineStream = ByteArrayInputStream(timelineJson.toByteArray(Charsets.UTF_8)),
            startDate = LocalDate.of(2026, 7, 2),
            endDate = LocalDate.of(2026, 7, 2),
            customTitle = "July 2 Trip"
        )

        assertEquals("July 2 Trip must contain 1 TripDay", 1, trip.days.size)
        val day = trip.days[0]
        assertEquals("TripDay date must be 2026-07-02", "2026-07-02", day.dateIso)

        val visits = day.items.filterIsInstance<TripDayItem.VisitItem>()
        val movements = day.items.filterIsInstance<TripDayItem.MovementItem>()

        assertEquals("Enclosing multi-day visit must be retained", 1, visits.size)
        assertEquals("Niagara Grand Hotel", visits[0].visit.placeName)

        assertEquals("Overnight movement crossing into trip date must be retained", 1, movements.size)
        assertEquals(450000.0, movements[0].segment.distanceMeters, 0.1)

        // Verify durability across Room save and reload
        val reloadedTrip = tripRepository.getTripById(trip.id)
        assertNotNull(reloadedTrip)
        assertEquals(1, reloadedTrip!!.days.size)
        val reloadedDay = reloadedTrip.days[0]
        assertEquals("2026-07-02", reloadedDay.dateIso)
        assertEquals(1, reloadedDay.items.filterIsInstance<TripDayItem.VisitItem>().size)
        assertEquals(1, reloadedDay.items.filterIsInstance<TripDayItem.MovementItem>().size)
    }
}
