package com.traveler.domain.usecase

import com.traveler.core.classifier.TransportClassifier
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.time.TimezoneResolution
import com.traveler.core.common.time.TimezoneResolver
import com.traveler.core.media.MediaRepository
import com.traveler.core.media.RawMediaCandidate
import com.traveler.core.model.*
import com.traveler.core.timeline.DateRangeFilter
import com.traveler.core.timeline.LocationHistorySource
import com.traveler.core.timeline.TimelineParseResult
import com.traveler.core.timeline.TimelineParseStatus
import com.traveler.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MediaCaptureTimezonePrecedenceTest {

    private class FakeLocationHistorySource(val parseResult: TimelineParseResult) : LocationHistorySource {
        override val sourceName: String = "FakeLocationHistory"
        override suspend fun parse(inputStream: InputStream, filter: DateRangeFilter?): TimelineParseResult = parseResult
    }

    private class FakeMediaRepository(val items: List<RawMediaCandidate>) : MediaRepository {
        override suspend fun queryMediaCandidatesForDateRange(
            startTimestampEpochMs: Long,
            endTimestampEpochMs: Long
        ): List<RawMediaCandidate> = items
    }

    private class FakeTransportClassifier : TransportClassifier {
        override fun classify(segment: MovementSegment): TransportPrediction =
            TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Flight")
    }

    private class FakeTripRepository : TripRepository {
        override fun getAllTrips(): Flow<List<Trip>> = flowOf(emptyList())
        override suspend fun getTripById(id: String): Trip? = null
        override suspend fun saveTrip(trip: Trip) {}
        override suspend fun deleteTrip(id: String) {}
        override suspend fun updateVisitName(tripId: String, visitSourceId: String, name: String) {}
        override suspend fun updateTransportMode(tripId: String, segmentSourceId: String, mode: TransportMode) {}
        override suspend fun updateMediaVisit(tripId: String, mediaKey: String, visitSourceId: String?) {}
        override suspend fun setRepresentativeMedia(tripId: String, mediaKey: String, isRepresentative: Boolean) {}
        override suspend fun cleanOrphanTripData(): com.traveler.domain.repository.OrphanCleanupSummary = com.traveler.domain.repository.OrphanCleanupSummary(0, 0, 0)
        override suspend fun getDurableUserOverrides(): List<com.traveler.core.database.entity.UserOverrideEntity> = emptyList()
    }

    private class FakeTimezoneResolver(
        private val zoneResolver: (GeoPoint?) -> ZoneId?
    ) : TimezoneResolver {
        override suspend fun resolve(point: GeoPoint?): TimezoneResolution {
            val z = zoneResolver(point)
            return if (z != null) TimezoneResolution.Resolved(z) else TimezoneResolution.Unavailable
        }
        override fun resolveSync(point: GeoPoint?): TimezoneResolution {
            val z = zoneResolver(point)
            return if (z != null) TimezoneResolution.Resolved(z) else TimezoneResolution.Unavailable
        }
    }

    @Test
    fun testP0_01_NySeoulFlight_PhotoWithSeoulCaptureTimezone_UsesSeoulDate() = runBlocking {
        // Flight: JFK (America/New_York) -> ICN (Asia/Seoul)
        // Departure: 2026-07-01 13:00 UTC (09:00 NY, 22:00 Seoul on July 1)
        // Arrival: 2026-07-02 03:00 UTC (July 1 23:00 NY, July 2 12:00 Seoul)
        // Photo captured near arrival at 2026-07-02 02:30 UTC:
        // In NY: 2026-07-01 22:30 (Day: 2026-07-01)
        // In Seoul: 2026-07-02 11:30 (Day: 2026-07-02)
        val photoInstant = Instant.parse("2026-07-02T02:30:00Z")
        val flightStart = Instant.parse("2026-07-01T13:00:00Z").toEpochMilli()
        val flightEnd = Instant.parse("2026-07-02T03:00:00Z").toEpochMilli()

        val flightSegment = MovementSegment(
            id = "flight_ny_seoul",
            startTimestampEpochMs = flightStart,
            endTimestampEpochMs = flightEnd,
            startPoint = GeoPoint(40.6413, -73.7781), // JFK
            endPoint = GeoPoint(37.4602, 126.4407), // ICN
            simplifiedPoints = listOf(GeoPoint(40.6413, -73.7781), GeoPoint(37.4602, 126.4407)),
            distanceMeters = 11000000.0,
            durationMillis = flightEnd - flightStart,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Flight"),
            startTimezoneId = "America/New_York",
            endTimezoneId = "Asia/Seoul",
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC
        )

        val parseResult = TimelineParseResult(
            visits = emptyList(),
            movementSegments = listOf(flightSegment),
            rawLocationPoints = emptyList(),
            status = TimelineParseStatus.SUCCESS
        )

        // Photo has explicit capture timezone Asia/Seoul
        val photoCandidate = RawMediaCandidate(
            id = "photo_seoul_arrival",
            contentUriString = "content://media/1",
            fileName = "IMG_seoul.jpg",
            mimeType = "image/jpeg",
            exifDateTimeOriginal = "2026:07:02 11:30:00",
            exifOffset = "+09:00",
            mediaStoreDateTaken = photoInstant.toEpochMilli(),
            fileDateModifiedMs = null,
            directGps = GeoPoint(37.4602, 126.4407),
            directGpsZoneId = "Asia/Seoul"
        )

        val useCase = CreateTripUseCase(
            locationHistorySource = FakeLocationHistorySource(parseResult),
            mediaRepository = FakeMediaRepository(listOf(photoCandidate)),
            transportClassifier = FakeTransportClassifier(),
            tripRepository = FakeTripRepository(),
            timezoneResolver = FakeTimezoneResolver {
                if (it != null && it.longitude > 0) ZoneId.of("Asia/Seoul") else ZoneId.of("America/New_York")
            }
        )

        val trip = useCase.execute(
            timelineStream = "".byteInputStream(),
            startDate = LocalDate.parse("2026-07-01"),
            endDate = LocalDate.parse("2026-07-02")
        )

        val day2 = trip.days.find { it.dateIso == "2026-07-02" }
        assertNotNull("Day 2 (2026-07-02) must exist for Seoul date", day2)

        val allPhotos = trip.days.flatMap { it.items }.flatMap {
            when (it) {
                is TripDayItem.VisitItem -> it.photos
                is TripDayItem.MovementItem -> it.photos
                is TripDayItem.ContextualPhotosItem -> it.photos
                is TripDayItem.UnassignedPhotosItem -> it.photos
            }
        } + trip.uncertainDateMedia

        val photo = allPhotos.find { it.id == "photo_seoul_arrival" }
        assertNotNull("Photo must be present in trip", photo)
        assertEquals("Assigned day must use photo's own resolved timezone (Asia/Seoul -> 2026-07-02)", "2026-07-02", photo!!.assignedDayIso)
        assertEquals(DayAssignmentConfidence.EXACT, photo.dayAssignmentConfidence)
    }

    @Test
    fun testP0_01A_CrossTimezoneMovement_WithoutCaptureTimezone_ConflictingDates_BecomesAmbiguous() = runBlocking {
        // Flight: America/New_York -> Asia/Seoul
        // Photo instant: 2026-07-02T02:30:00Z (NY = 2026-07-01, Seoul = 2026-07-02)
        // Photo has NO capture timezone and NO resolvable location during mid-flight ocean crossing
        val photoInstant = Instant.parse("2026-07-02T02:30:00Z")
        val flightStart = Instant.parse("2026-07-01T13:00:00Z").toEpochMilli()
        val flightEnd = Instant.parse("2026-07-02T03:00:00Z").toEpochMilli()

        val flightSegment = MovementSegment(
            id = "flight_ny_seoul",
            startTimestampEpochMs = flightStart,
            endTimestampEpochMs = flightEnd,
            startPoint = GeoPoint(40.6413, -73.7781),
            endPoint = GeoPoint(37.4602, 126.4407),
            simplifiedPoints = listOf(GeoPoint(40.6413, -73.7781), GeoPoint(37.4602, 126.4407)),
            distanceMeters = 11000000.0,
            durationMillis = flightEnd - flightStart,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Flight"),
            startTimezoneId = "America/New_York",
            endTimezoneId = "Asia/Seoul",
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC
        )

        val parseResult = TimelineParseResult(
            visits = emptyList(),
            movementSegments = listOf(flightSegment),
            rawLocationPoints = emptyList(),
            status = TimelineParseStatus.SUCCESS
        )

        // Photo has NO capture timezone and NO GPS
        val photoCandidate = RawMediaCandidate(
            id = "photo_midflight_no_zone",
            contentUriString = "content://media/2",
            fileName = "IMG_midflight.jpg",
            mimeType = "image/jpeg",
            exifDateTimeOriginal = null,
            exifOffset = null,
            mediaStoreDateTaken = photoInstant.toEpochMilli(),
            fileDateModifiedMs = null,
            directGps = null,
            directGpsZoneId = null
        )

        val useCase = CreateTripUseCase(
            locationHistorySource = FakeLocationHistorySource(parseResult),
            mediaRepository = FakeMediaRepository(listOf(photoCandidate)),
            transportClassifier = FakeTransportClassifier(),
            tripRepository = FakeTripRepository(),
            timezoneResolver = FakeTimezoneResolver { null }
        )

        val trip = useCase.execute(
            timelineStream = "".byteInputStream(),
            startDate = LocalDate.parse("2026-07-01"),
            endDate = LocalDate.parse("2026-07-02")
        )

        val photo = trip.uncertainDateMedia.find { it.id == "photo_midflight_no_zone" }
        assertNotNull("Ambiguous date photo across timezones must be in uncertainDateMedia", photo)
        assertNull("assignedDayIso must be null for ambiguous cross-timezone photo", photo!!.assignedDayIso)
        assertEquals(DayAssignmentConfidence.AMBIGUOUS, photo.dayAssignmentConfidence)
    }

    @Test
    fun testP0_01A_CrossTimezoneMovement_WithoutCaptureTimezone_ConsensusDates_AssignedConsensus() = runBlocking {
        // Flight: America/New_York (EDT UTC-4) -> America/Toronto (EDT UTC-4)
        // Endpoint zones produce the SAME date for 2026-07-01T15:00:00Z -> 2026-07-01
        val photoInstant = Instant.parse("2026-07-01T15:00:00Z")
        val flightStart = Instant.parse("2026-07-01T14:00:00Z").toEpochMilli()
        val flightEnd = Instant.parse("2026-07-01T16:00:00Z").toEpochMilli()

        val flightSegment = MovementSegment(
            id = "flight_ny_toronto",
            startTimestampEpochMs = flightStart,
            endTimestampEpochMs = flightEnd,
            startPoint = GeoPoint(40.6413, -73.7781),
            endPoint = GeoPoint(43.6777, -79.6248),
            simplifiedPoints = listOf(GeoPoint(40.6413, -73.7781), GeoPoint(43.6777, -79.6248)),
            distanceMeters = 600000.0,
            durationMillis = flightEnd - flightStart,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Flight"),
            startTimezoneId = "America/New_York",
            endTimezoneId = "America/Toronto",
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC
        )

        val parseResult = TimelineParseResult(
            visits = emptyList(),
            movementSegments = listOf(flightSegment),
            rawLocationPoints = emptyList(),
            status = TimelineParseStatus.SUCCESS
        )

        val photoCandidate = RawMediaCandidate(
            id = "photo_toronto_flight",
            contentUriString = "content://media/3",
            fileName = "IMG_toronto.jpg",
            mimeType = "image/jpeg",
            exifDateTimeOriginal = null,
            exifOffset = null,
            mediaStoreDateTaken = photoInstant.toEpochMilli(),
            fileDateModifiedMs = null,
            directGps = null,
            directGpsZoneId = null
        )

        val useCase = CreateTripUseCase(
            locationHistorySource = FakeLocationHistorySource(parseResult),
            mediaRepository = FakeMediaRepository(listOf(photoCandidate)),
            transportClassifier = FakeTransportClassifier(),
            tripRepository = FakeTripRepository(),
            timezoneResolver = FakeTimezoneResolver { ZoneId.of("America/New_York") }
        )

        val trip = useCase.execute(
            timelineStream = "".byteInputStream(),
            startDate = LocalDate.parse("2026-07-01"),
            endDate = LocalDate.parse("2026-07-02")
        )

        val allPhotos = trip.days.flatMap { it.items }.flatMap {
            when (it) {
                is TripDayItem.VisitItem -> it.photos
                is TripDayItem.MovementItem -> it.photos
                is TripDayItem.ContextualPhotosItem -> it.photos
                is TripDayItem.UnassignedPhotosItem -> it.photos
            }
        }

        val photo = allPhotos.find { it.id == "photo_toronto_flight" }
        assertNotNull("Photo must be assigned to day", photo)
        assertEquals("2026-07-01", photo!!.assignedDayIso)
        assertTrue(photo.dayAssignmentConfidence == DayAssignmentConfidence.CONSENSUS || photo.dayAssignmentConfidence == DayAssignmentConfidence.CONTEXTUAL)
    }
}
