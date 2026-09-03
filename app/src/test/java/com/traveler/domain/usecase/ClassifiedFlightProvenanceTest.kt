package com.traveler.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.traveler.core.classifier.TransportClassifier
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.time.TimezoneResolution
import com.traveler.core.common.time.TimezoneResolver
import com.traveler.core.database.TravelerDatabase
import com.traveler.core.media.MediaRepository
import com.traveler.core.model.*
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
class ClassifiedFlightProvenanceTest {

    private lateinit var database: TravelerDatabase
    private lateinit var tripRepository: TripRepositoryImpl

    private val fakeZoneResolver = object : TimezoneResolver {
        override suspend fun resolve(point: GeoPoint?): TimezoneResolution = TimezoneResolution.Unavailable
        override fun resolveSync(point: GeoPoint?): TimezoneResolution = TimezoneResolution.Unavailable
    }

    private val fakeFlightClassifier = object : TransportClassifier {
        override fun classify(segment: MovementSegment): TransportPrediction {
            return TransportPrediction(TransportMode.AIRPLANE, 0.99f, "High speed long distance flight", isUserOverride = false)
        }
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
    fun endpointOnlySegmentClassifiedAsAirplane_setsEstimatedGeodesicProvenance() = runTest {
        // Timeline JSON with generic activity / unknown mode between NY and London
        val timelineJson = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-02T10:00:00Z",
              "endTime": "2026-07-02T17:00:00Z",
              "activity": {
                "topCandidate": {
                  "type": "UNKNOWN_ACTIVITY",
                  "probability": 0.3
                },
                "start": { "latLng": "40.6413°, -73.7781°" },
                "end": { "latLng": "51.4700°, -0.4543°" },
                "distanceMeters": 5550000.0
              }
            }
          ]
        }
        """.trimIndent()

        val useCase = CreateTripUseCase(
            locationHistorySource = GoogleTimelineJsonParser(),
            mediaRepository = emptyMediaRepo,
            transportClassifier = fakeFlightClassifier,
            tripRepository = tripRepository,
            timezoneResolver = fakeZoneResolver
        )

        val trip = useCase.execute(
            timelineStream = ByteArrayInputStream(timelineJson.toByteArray(Charsets.UTF_8)),
            startDate = LocalDate.of(2026, 7, 2),
            endDate = LocalDate.of(2026, 7, 2),
            customTitle = "Transatlantic Flight"
        )

        val movementItem = trip.days.flatMap { it.items }.filterIsInstance<TripDayItem.MovementItem>().firstOrNull()
        assertNotNull(movementItem)
        assertEquals(TransportMode.AIRPLANE, movementItem!!.segment.effectiveMode)
        // P1-06: Provenance must match classified geodesic rendering
        assertEquals(GeometryProvenance.ESTIMATED_GEODESIC, movementItem.segment.geometryProvenance)
    }
}
