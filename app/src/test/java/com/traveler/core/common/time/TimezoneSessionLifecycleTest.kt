package com.traveler.core.common.time

import com.traveler.core.classifier.RuleBasedTransportClassifier
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.database.entity.UserOverrideEntity
import com.traveler.core.media.MediaRepository
import com.traveler.core.media.RawMediaCandidate
import com.traveler.core.model.*
import com.traveler.core.timeline.GoogleTimelineJsonParser
import com.traveler.domain.repository.OrphanCleanupSummary
import com.traveler.domain.repository.TripRepository
import com.traveler.domain.usecase.CreateTripUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate

class TimezoneSessionLifecycleTest {

    @Before
    fun setUp() {
        GeoTimezoneEngine.resetForTesting()
    }

    private class TrackingTimezoneResolver : TimezoneResolver {
        var releaseCallCount = 0
        var resolveCallCount = 0

        override suspend fun resolve(point: GeoPoint?): TimezoneResolution {
            resolveCallCount++
            return if (point != null) TimezoneResolution.Resolved(java.time.ZoneId.of("America/New_York")) else TimezoneResolution.Unavailable
        }

        override fun resolveSync(point: GeoPoint?): TimezoneResolution {
            resolveCallCount++
            return if (point != null) TimezoneResolution.Resolved(java.time.ZoneId.of("America/New_York")) else TimezoneResolution.Unavailable
        }

        override fun release() {
            releaseCallCount++
        }
    }

    private class StubMediaRepo : MediaRepository {
        override suspend fun queryMediaCandidatesForDateRange(startTimestampEpochMs: Long, endTimestampEpochMs: Long): List<RawMediaCandidate> = emptyList()
    }

    private class StubTripRepo : TripRepository {
        var saved: Trip? = null
        override fun getAllTrips(): Flow<List<Trip>> = flowOf(emptyList())
        override suspend fun getTripById(tripId: String): Trip? = saved
        override suspend fun saveTrip(trip: Trip) { saved = trip }
        override suspend fun deleteTrip(tripId: String) {}
        override suspend fun updateTransportMode(tripId: String, segmentSourceId: String, mode: TransportMode) {}
        override suspend fun updateVisitName(tripId: String, visitSourceId: String, name: String) {}
        override suspend fun updateMediaVisit(tripId: String, mediaKey: String, visitSourceId: String?) {}
        override suspend fun setRepresentativeMedia(tripId: String, mediaKey: String, isRepresentative: Boolean) {}
        override suspend fun cleanOrphanTripData(): OrphanCleanupSummary = OrphanCleanupSummary(0, 0, 0)
        override suspend fun getDurableUserOverrides(): List<UserOverrideEntity> = emptyList()
    }

    @Test
    fun testEngineNotInitializedByDefault() {
        assertFalse("Engine should not be initialized after reset", GeoTimezoneEngine.isInitialized())
    }

    @Test
    fun testReleaseCalledOnSuccessfulImport() = runBlocking {
        val trackingResolver = TrackingTimezoneResolver()
        val useCase = CreateTripUseCase(
            locationHistorySource = GoogleTimelineJsonParser(),
            mediaRepository = StubMediaRepo(),
            transportClassifier = RuleBasedTransportClassifier(),
            tripRepository = StubTripRepo(),
            timezoneResolver = trackingResolver
        )

        val timelineJson = """
        {
          "semanticSegments": [
            {
              "startTime": "2026-07-01T12:00:00.000Z",
              "endTime": "2026-07-01T14:00:00.000Z",
              "visit": {
                "topCandidate": {
                  "placeId": "p1",
                  "placeName": "Central Park",
                  "placeLocation": { "latLng": "40.785091°, -73.968285°" }
                }
              }
            }
          ]
        }
        """.trimIndent()

        val trip = useCase.execute(
            timelineStream = ByteArrayInputStream(timelineJson.toByteArray()),
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 1)
        )

        assertEquals("Trip to Central Park", trip.title)
        assertEquals("Release must be invoked exactly once on success", 1, trackingResolver.releaseCallCount)
    }

    @Test
    fun testReleaseCalledOnImportFailureOrMalformedJson() = runBlocking {
        val trackingResolver = TrackingTimezoneResolver()
        val useCase = CreateTripUseCase(
            locationHistorySource = GoogleTimelineJsonParser(),
            mediaRepository = StubMediaRepo(),
            transportClassifier = RuleBasedTransportClassifier(),
            tripRepository = StubTripRepo(),
            timezoneResolver = trackingResolver
        )

        val malformedJson = "{ invalid json content }"

        try {
            useCase.execute(
                timelineStream = ByteArrayInputStream(malformedJson.toByteArray()),
                startDate = LocalDate.of(2026, 7, 1),
                endDate = LocalDate.of(2026, 7, 1)
            )
            fail("Expected exception for malformed JSON")
        } catch (_: Exception) {
            // Expected
        }

        assertEquals("Release must be invoked even when import fails with exception", 1, trackingResolver.releaseCallCount)
    }

    @Test
    fun testConcurrentSessionAcquireAndRelease() {
        GeoTimezoneEngine.resetForTesting()
        assertFalse(GeoTimezoneEngine.isInitialized())

        // Acquire two sessions
        GeoTimezoneEngine.acquireSession()
        GeoTimezoneEngine.acquireSession()

        // Release first session (engine should NOT be released yet)
        GeoTimezoneEngine.releaseSession()

        // Release second session (engine should be released)
        GeoTimezoneEngine.releaseSession()
        assertFalse(GeoTimezoneEngine.isInitialized())
    }
}
