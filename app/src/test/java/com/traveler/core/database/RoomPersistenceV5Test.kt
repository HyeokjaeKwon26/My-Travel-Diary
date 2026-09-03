package com.traveler.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import com.traveler.data.repository.TripRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class RoomPersistenceV5Test {

    private lateinit var database: TravelerDatabase
    private lateinit var repository: TripRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TravelerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TripRepositoryImpl(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveAndReloadTrip_preservesGeometryProvenanceAndMediaDayAssignment() = runTest {
        val nyPoint = GeoPoint(40.7128, -74.0060)
        val seoulPoint = GeoPoint(37.5665, 126.9780)

        val baseEpochMs = 1783123200000L // 2026-07-04 00:00:00 UTC

        val visit = Visit(
            id = "v_seoul",
            placeName = "Gyeongbokgung Palace",
            placeAddress = "Seoul, South Korea",
            placeId = "place_seoul_1",
            location = seoulPoint,
            startTimestampEpochMs = baseEpochMs + 8 * 3600_000L,
            endTimestampEpochMs = baseEpochMs + 10 * 3600_000L,
            confidence = 0.95f,
            isUserOverride = false,
            timezoneId = "Asia/Seoul"
        )

        val segment = MovementSegment(
            id = "s_flight",
            startTimestampEpochMs = baseEpochMs + 1 * 3600_000L,
            endTimestampEpochMs = baseEpochMs + 7 * 3600_000L,
            startPoint = nyPoint,
            endPoint = seoulPoint,
            simplifiedPoints = listOf(nyPoint, seoulPoint),
            distanceMeters = 11000000.0,
            durationMillis = 6 * 3600_000L,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Flight"),
            isUserOverride = false,
            startTimezoneId = "America/New_York",
            endTimezoneId = "Asia/Seoul",
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC
        )

        val photo1 = MediaItem(
            id = "photo_visit",
            contentUriString = "content://media/visit_photo",
            fileName = "palace.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = baseEpochMs + 9 * 3600_000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = "Asia/Seoul",
            location = seoulPoint,
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.99f,
            matchedVisitId = "v_seoul",
            assignedDayIso = "2026-07-04",
            dayAssignmentConfidence = DayAssignmentConfidence.EXACT,
            dayAssignmentProvenance = "EXACT_TIMEZONE"
        )

        val photo2 = MediaItem(
            id = "photo_unassigned",
            contentUriString = "content://media/unassigned_photo",
            fileName = "lunch.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = baseEpochMs + 9 * 3600_000L + 1800_000L,
            timestampConfidence = TimestampConfidence.MEDIASTORE,
            captureTimezoneId = null, // No fabricated timezone!
            location = null,
            locationConfidence = LocationConfidenceLevel.UNKNOWN,
            confidenceScore = 0.0f,
            matchedVisitId = null,
            assignedDayIso = "2026-07-04",
            dayAssignmentConfidence = DayAssignmentConfidence.CONSENSUS,
            dayAssignmentProvenance = "CONSENSUS_ZONES"
        )

        val day = TripDay(
            dayIndex = 1,
            dateIso = "2026-07-04",
            timezoneId = "Asia/Seoul",
            items = listOf(
                TripDayItem.MovementItem(segment, listOf(photo1)),
                TripDayItem.VisitItem(visit, emptyList()),
                TripDayItem.UnassignedPhotosItem(listOf(photo2))
            ),
            unassignedPhotos = listOf(photo2),
            totalDistanceMeters = 11000000.0,
            photoCount = 2
        )

        val trip = Trip(
            id = "trip_v5",
            title = "NYC to Seoul Odyssey",
            startDateIso = "2026-07-04",
            endDateIso = "2026-07-04",
            days = listOf(day),
            totalDistanceMeters = 11000000.0,
            cities = listOf("New York", "Seoul"),
            countries = listOf("United States", "South Korea"),
            totalMediaCount = 2,
            createdAtEpochMs = 1782820000000L
        )

        // Save to Room v5 database
        repository.saveTrip(trip)

        // Reload and verify
        val reloaded = repository.getTripById("trip_v5")
        assertNotNull("Reloaded trip must not be null", reloaded)
        assertEquals("NYC to Seoul Odyssey", reloaded!!.title)
        assertEquals(1, reloaded.days.size)

        val reloadedDay = reloaded.days.first()
        val movementItem = reloadedDay.items.filterIsInstance<TripDayItem.MovementItem>().firstOrNull()
        assertNotNull(movementItem)
        val reloadedSeg = movementItem!!.segment
        assertEquals("s_flight", reloadedSeg.id)
        assertEquals("America/New_York", reloadedSeg.startTimezoneId)
        assertEquals("Asia/Seoul", reloadedSeg.endTimezoneId)
        assertEquals("geometryProvenance must be preserved as ESTIMATED_GEODESIC", GeometryProvenance.ESTIMATED_GEODESIC, reloadedSeg.geometryProvenance)

        assertEquals(1, reloadedDay.unassignedPhotos.size)
        val reloadedUnassigned = reloadedDay.unassignedPhotos.first()
        assertEquals("photo_unassigned", reloadedUnassigned.id)
        assertNull("captureTimezoneId must remain null and not be fabricated", reloadedUnassigned.captureTimezoneId)
        assertEquals("2026-07-04", reloadedUnassigned.assignedDayIso)
        assertEquals(DayAssignmentConfidence.CONSENSUS, reloadedUnassigned.dayAssignmentConfidence)
        assertEquals("CONSENSUS_ZONES", reloadedUnassigned.dayAssignmentProvenance)
    }

    @Test
    fun deleteTrip_atomicallyDeletesTripAndAllChildRows_andPreservesOtherTrips() = runTest {
        val baseEpochMs = 1783123200000L
        val nyPoint = GeoPoint(40.7128, -74.0060)
        val seoulPoint = GeoPoint(37.5665, 126.9780)

        // Trip A
        val visitA = Visit(
            id = "v_A",
            placeName = "Place A",
            placeAddress = null,
            placeId = null,
            location = nyPoint,
            startTimestampEpochMs = baseEpochMs,
            endTimestampEpochMs = baseEpochMs + 3600000L,
            confidence = 0.9f
        )
        val segA = MovementSegment(
            id = "s_A",
            startTimestampEpochMs = baseEpochMs + 3600000L,
            endTimestampEpochMs = baseEpochMs + 7200000L,
            startPoint = nyPoint,
            endPoint = seoulPoint,
            simplifiedPoints = listOf(nyPoint, seoulPoint),
            distanceMeters = 1000.0,
            durationMillis = 3600000L,
            transport = TransportPrediction(TransportMode.WALK, 1.0f, "Walk")
        )
        val photoA = MediaItem(
            id = "p_A",
            contentUriString = "content://media/A",
            fileName = "a.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = baseEpochMs,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = null,
            location = nyPoint,
            matchedVisitId = "v_A",
            assignedDayIso = "2026-07-04"
        )
        val tripA = Trip(
            id = "trip_A",
            title = "Trip A",
            startDateIso = "2026-07-04",
            endDateIso = "2026-07-04",
            totalDistanceMeters = 1000.0,
            days = listOf(
                TripDay(
                    dayIndex = 1,
                    dateIso = "2026-07-04",
                    timezoneId = null,
                    items = listOf(TripDayItem.VisitItem(visitA, listOf(photoA)), TripDayItem.MovementItem(segA, emptyList())),
                    unassignedPhotos = emptyList(),
                    totalDistanceMeters = 1000.0,
                    photoCount = 1
                )
            ),
            totalMediaCount = 1,
            createdAtEpochMs = baseEpochMs
        )

        // Trip B
        val visitB = Visit(
            id = "v_B",
            placeName = "Place B",
            placeAddress = null,
            placeId = null,
            location = seoulPoint,
            startTimestampEpochMs = baseEpochMs + 10000000L,
            endTimestampEpochMs = baseEpochMs + 13600000L,
            confidence = 0.9f
        )
        val segB = MovementSegment(
            id = "s_B",
            startTimestampEpochMs = baseEpochMs + 13600000L,
            endTimestampEpochMs = baseEpochMs + 17200000L,
            startPoint = seoulPoint,
            endPoint = nyPoint,
            simplifiedPoints = listOf(seoulPoint, nyPoint),
            distanceMeters = 2000.0,
            durationMillis = 3600000L,
            transport = TransportPrediction(TransportMode.BUS, 1.0f, "Bus")
        )
        val photoB = MediaItem(
            id = "p_B",
            contentUriString = "content://media/B",
            fileName = "b.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = baseEpochMs + 10000000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = null,
            location = seoulPoint,
            matchedVisitId = "v_B",
            assignedDayIso = "2026-07-04"
        )
        val tripB = Trip(
            id = "trip_B",
            title = "Trip B",
            startDateIso = "2026-07-04",
            endDateIso = "2026-07-04",
            totalDistanceMeters = 2000.0,
            days = listOf(
                TripDay(
                    dayIndex = 1,
                    dateIso = "2026-07-04",
                    timezoneId = null,
                    items = listOf(TripDayItem.VisitItem(visitB, listOf(photoB)), TripDayItem.MovementItem(segB, emptyList())),
                    unassignedPhotos = emptyList(),
                    totalDistanceMeters = 2000.0,
                    photoCount = 1
                )
            ),
            totalMediaCount = 1,
            createdAtEpochMs = baseEpochMs
        )

        repository.saveTrip(tripA)
        repository.saveTrip(tripB)

        // Verify both exist
        assertNotNull(repository.getTripById("trip_A"))
        assertNotNull(repository.getTripById("trip_B"))
        assertEquals(1, database.visitDao().getVisitsForTrip("trip_A").size)
        assertEquals(1, database.movementSegmentDao().getSegmentsForTrip("trip_A").size)
        assertEquals(1, database.tripMediaDao().getMediaForTrip("trip_A").size)
        assertEquals(1, database.visitDao().getVisitsForTrip("trip_B").size)
        assertEquals(1, database.movementSegmentDao().getSegmentsForTrip("trip_B").size)
        assertEquals(1, database.tripMediaDao().getMediaForTrip("trip_B").size)

        // Delete Trip B
        repository.deleteTrip("trip_B")

        // Verify Trip B is completely wiped from all tables
        assertNull(repository.getTripById("trip_B"))
        assertEquals(0, database.visitDao().getVisitsForTrip("trip_B").size)
        assertEquals(0, database.movementSegmentDao().getSegmentsForTrip("trip_B").size)
        assertEquals(0, database.tripMediaDao().getMediaForTrip("trip_B").size)

        // Verify Trip A remains 100% intact
        assertNotNull(repository.getTripById("trip_A"))
        assertEquals(1, database.visitDao().getVisitsForTrip("trip_A").size)
        assertEquals(1, database.movementSegmentDao().getSegmentsForTrip("trip_A").size)
        assertEquals(1, database.tripMediaDao().getMediaForTrip("trip_A").size)
    }

    @Test
    fun cleanOrphanTripData_removesOnlyOrphansAndPreservesValidData() = runTest {
        val baseEpochMs = 1783123200000L
        val nyPoint = GeoPoint(40.7128, -74.0060)

        // Save valid Trip A
        val visitA = Visit(
            id = "v_A",
            placeName = "Place A",
            placeAddress = null,
            placeId = null,
            location = nyPoint,
            startTimestampEpochMs = baseEpochMs,
            endTimestampEpochMs = baseEpochMs + 3600000L,
            confidence = 0.9f
        )
        val tripA = Trip(
            id = "trip_A",
            title = "Trip A",
            startDateIso = "2026-07-04",
            endDateIso = "2026-07-04",
            totalDistanceMeters = 0.0,
            days = listOf(
                TripDay(
                    dayIndex = 1,
                    dateIso = "2026-07-04",
                    timezoneId = null,
                    items = listOf(TripDayItem.VisitItem(visitA, emptyList())),
                    unassignedPhotos = emptyList(),
                    totalDistanceMeters = 0.0,
                    photoCount = 0
                )
            ),
            totalMediaCount = 0,
            createdAtEpochMs = baseEpochMs
        )
        repository.saveTrip(tripA)

        // Seed orphan child rows for non-existent trip "orphan_trip"
        database.visitDao().insertVisits(listOf(
            com.traveler.core.database.entity.VisitEntity(
                tripId = "orphan_trip",
                sourceId = "v_orphan",
                placeName = "Orphan Place",
                placeAddress = null,
                placeId = null,
                latitude = 40.0,
                longitude = -74.0,
                startTimestampEpochMs = baseEpochMs,
                endTimestampEpochMs = baseEpochMs + 3600000L,
                confidence = 0.8f,
                isUserOverride = false,
                timezoneId = null
            )
        ))
        database.movementSegmentDao().insertSegments(listOf(
            com.traveler.core.database.entity.MovementSegmentEntity(
                tripId = "orphan_trip",
                sourceId = "s_orphan",
                startTimestampEpochMs = baseEpochMs,
                endTimestampEpochMs = baseEpochMs + 3600000L,
                startLat = 40.0, startLng = -74.0, endLat = 40.1, endLng = -74.1,
                distanceMeters = 1000.0, durationMillis = 3600000L,
                predictedTransportMode = "WALK", predictedConfidence = 0.9f, predictedReason = "Test",
                userOverrideTransportMode = null, polylineJson = "", isUserOverride = false,
                startTimezoneId = null, endTimezoneId = null, geometryProvenance = "OBSERVED"
            )
        ))
        database.tripMediaDao().insertMediaItems(listOf(
            com.traveler.core.database.entity.TripMediaEntity(
                tripId = "orphan_trip",
                mediaKey = "p_orphan",
                contentUriString = "content://media/orphan",
                fileName = "orphan.jpg",
                mimeType = "image/jpeg",
                timestampEpochMs = baseEpochMs,
                timestampConfidence = "EXIF_EXACT",
                captureTimezoneId = null,
                latitude = 40.0, longitude = -74.0,
                locationConfidence = "GPS_EXACT", confidenceScore = 0.9f,
                matchedVisitId = "v_orphan", matchedSegmentId = null,
                isRepresentative = false, isUserLocationOverride = false,
                assignedDayIso = "2026-07-04", dayAssignmentConfidence = "EXACT", dayAssignmentProvenance = "TEST"
            )
        ))

        // Verify orphan rows exist
        assertEquals(1, database.visitDao().getVisitsForTrip("orphan_trip").size)
        assertEquals(1, database.movementSegmentDao().getSegmentsForTrip("orphan_trip").size)
        assertEquals(1, database.tripMediaDao().getMediaForTrip("orphan_trip").size)

        // Run orphan cleanup
        val summary = repository.cleanOrphanTripData()
        assertEquals(1, summary.deletedVisits)
        assertEquals(1, summary.deletedSegments)
        assertEquals(1, summary.deletedMedia)

        // Verify orphans are gone
        assertEquals(0, database.visitDao().getVisitsForTrip("orphan_trip").size)
        assertEquals(0, database.movementSegmentDao().getSegmentsForTrip("orphan_trip").size)
        assertEquals(0, database.tripMediaDao().getMediaForTrip("orphan_trip").size)

        // Verify Trip A data is fully intact
        assertNotNull(repository.getTripById("trip_A"))
        assertEquals(1, database.visitDao().getVisitsForTrip("trip_A").size)
    }

    @Test
    fun exclusiveMediaParent_updateMediaVisitAndReconstructionDefense() = runTest {
        val baseEpochMs = 1783123200000L
        val nyPoint = GeoPoint(40.7128, -74.0060)

        val visit = Visit(
            id = "v1",
            placeName = "Place 1",
            placeAddress = null,
            placeId = null,
            location = nyPoint,
            startTimestampEpochMs = baseEpochMs,
            endTimestampEpochMs = baseEpochMs + 3600000L,
            confidence = 0.9f
        )
        val seg = MovementSegment(
            id = "s1",
            startTimestampEpochMs = baseEpochMs + 3600000L,
            endTimestampEpochMs = baseEpochMs + 7200000L,
            startPoint = nyPoint,
            endPoint = nyPoint,
            simplifiedPoints = listOf(nyPoint),
            distanceMeters = 100.0,
            durationMillis = 3600000L,
            transport = TransportPrediction(TransportMode.WALK, 1.0f, "Walk")
        )

        // Media initially attached to segment
        val photo = MediaItem(
            id = "p1",
            contentUriString = "content://media/p1",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = baseEpochMs,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = null,
            location = nyPoint,
            matchedVisitId = null,
            matchedSegmentId = "s1",
            assignedDayIso = "2026-07-04"
        )
        val trip = Trip(
            id = "trip_excl",
            title = "Trip Exclusive",
            startDateIso = "2026-07-04",
            endDateIso = "2026-07-04",
            totalDistanceMeters = 100.0,
            days = listOf(
                TripDay(
                    dayIndex = 1,
                    dateIso = "2026-07-04",
                    timezoneId = null,
                    items = listOf(TripDayItem.VisitItem(visit, emptyList()), TripDayItem.MovementItem(seg, listOf(photo))),
                    unassignedPhotos = emptyList(),
                    totalDistanceMeters = 100.0,
                    photoCount = 1
                )
            ),
            totalMediaCount = 1,
            createdAtEpochMs = baseEpochMs
        )
        repository.saveTrip(trip)

        // Update media visit
        repository.updateMediaVisit("trip_excl", "p1", "v1")

        val mediaInDb = database.tripMediaDao().getMediaForTrip("trip_excl").first()
        assertEquals("v1", mediaInDb.matchedVisitId)
        assertNull("matchedSegmentId must be cleared to null when matchedVisitId is updated", mediaInDb.matchedSegmentId)

        // Defensively test reconstructDays with corrupted input having BOTH matchedVisitId and matchedSegmentId
        val corruptPhoto = photo.copy(matchedVisitId = "v1", matchedSegmentId = "s1")
        val reconstructedDays = TripRepositoryImpl.reconstructDays(
            visits = listOf(visit),
            segments = listOf(seg),
            mediaItems = listOf(corruptPhoto),
            tripStartDateIso = "2026-07-04",
            tripEndDateIso = "2026-07-04"
        )
        val day = reconstructedDays.first()
        val totalPhotosRendered = day.items.sumOf { item ->
            when (item) {
                is TripDayItem.VisitItem -> item.photos.size
                is TripDayItem.MovementItem -> item.photos.size
                is TripDayItem.ContextualPhotosItem -> item.photos.size
                is TripDayItem.UnassignedPhotosItem -> item.photos.size
            }
        }
        assertEquals("Photo with both parent IDs must appear exactly once in reconstructed day items", 1, totalPhotosRendered)
    }
}
