package com.traveler.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import com.traveler.data.repository.TripRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class CrossTripDataCorruptionTest {

    private lateinit var db: TravelerDatabase
    private lateinit var repository: TripRepositoryImpl

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TravelerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TripRepositoryImpl(db)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testSharedSourceIds_DoNotCorruptOrEraseAcrossTrips() = runBlocking {
        val sharedVisit = Visit(
            id = "visit-shared-123",
            placeName = "Shared City Center",
            location = GeoPoint(37.5665, 126.9780),
            startTimestampEpochMs = 1783150000000L,
            endTimestampEpochMs = 1783160000000L,
            confidence = 0.95f
        )

        val sharedSegment = MovementSegment(
            id = "seg-shared-456",
            startTimestampEpochMs = 1783160000000L,
            endTimestampEpochMs = 1783170000000L,
            startPoint = GeoPoint(37.5665, 126.9780),
            endPoint = GeoPoint(37.5700, 126.9800),
            distanceMeters = 1500.0,
            durationMillis = 10 * 60_000L,
            transport = TransportPrediction(TransportMode.WALK, 0.9f, "Walked")
        )

        val sharedMedia = MediaItem(
            id = "IMG_999",
            contentUriString = "content://media/999",
            fileName = "IMG_999.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = 1783155000000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            location = GeoPoint(37.5665, 126.9780),
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            matchedVisitId = sharedVisit.id
        )

        val tripA = Trip(
            id = "trip-A",
            title = "Trip A",
            startDateIso = "2026-07-05",
            endDateIso = "2026-07-05",
            totalDistanceMeters = 1500.0,
            cities = listOf("Seoul"),
            countries = listOf("South Korea"),
            days = listOf(
                TripDay(
                    dayIndex = 1,
                    dateIso = "2026-07-05",
                    timezoneId = "Asia/Seoul",
                    items = listOf(
                        TripDayItem.VisitItem(sharedVisit, listOf(sharedMedia)),
                        TripDayItem.MovementItem(sharedSegment, emptyList())
                    ),
                    totalDistanceMeters = 1500.0,
                    photoCount = 1
                )
            ),
            totalMediaCount = 1,
            createdAtEpochMs = 1000L
        )

        val tripB = Trip(
            id = "trip-B",
            title = "Trip B",
            startDateIso = "2026-07-05",
            endDateIso = "2026-07-05",
            totalDistanceMeters = 1500.0,
            cities = listOf("Seoul"),
            countries = listOf("South Korea"),
            days = listOf(
                TripDay(
                    dayIndex = 1,
                    dateIso = "2026-07-05",
                    timezoneId = "Asia/Seoul",
                    items = listOf(
                        TripDayItem.VisitItem(sharedVisit, listOf(sharedMedia)),
                        TripDayItem.MovementItem(sharedSegment, emptyList())
                    ),
                    totalDistanceMeters = 1500.0,
                    photoCount = 1
                )
            ),
            totalMediaCount = 1,
            createdAtEpochMs = 2000L
        )

        // 1. Save Trip A
        repository.saveTrip(tripA)

        // 2. Save Trip B with overlapping items
        repository.saveTrip(tripB)

        // 3. Reload Trip A
        val reloadedTripA = repository.getTripById("trip-A")
        assertNotNull("Trip A must exist after Trip B is saved", reloadedTripA)
        assertEquals(1, reloadedTripA!!.days.size)
        assertEquals(2, reloadedTripA.days[0].items.size)
        val visitA = reloadedTripA.days[0].items[0] as TripDayItem.VisitItem
        assertEquals("visit-shared-123", visitA.visit.id)
        assertEquals(1, visitA.photos.size)
        assertEquals("IMG_999", visitA.photos[0].id)

        // 4. Reload Trip B
        val reloadedTripB = repository.getTripById("trip-B")
        assertNotNull("Trip B must exist", reloadedTripB)
        assertEquals(1, reloadedTripB!!.days.size)
        assertEquals(2, reloadedTripB.days[0].items.size)

        // 5. Delete Trip B
        repository.deleteTrip("trip-B")

        // 6. Reload Trip A again: must be completely intact!
        val tripAAfterDeleteB = repository.getTripById("trip-A")
        assertNotNull("Trip A must still exist after Trip B deletion", tripAAfterDeleteB)
        assertEquals(1, tripAAfterDeleteB!!.days.size)
        assertEquals(2, tripAAfterDeleteB.days[0].items.size)
        val visitAAfter = tripAAfterDeleteB.days[0].items[0] as TripDayItem.VisitItem
        assertEquals("visit-shared-123", visitAAfter.visit.id)
        assertEquals(1, visitAAfter.photos.size)
    }

    @Test
    fun testImageAndVideoSameCursorId_DoNotCollide() = runBlocking {
        // Both image and video having cursor id 123
        val photo = MediaItem(
            id = "IMG_123",
            contentUriString = "content://media/external/images/media/123",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = 1000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT
        )

        val video = MediaItem(
            id = "VID_123",
            contentUriString = "content://media/external/video/media/123",
            fileName = "video.mp4",
            mimeType = "video/mp4",
            timestampEpochMs = 2000L,
            timestampConfidence = TimestampConfidence.MEDIASTORE
        )

        val trip = Trip(
            id = "trip-media-test",
            title = "Media Test",
            startDateIso = "2026-07-01",
            endDateIso = "2026-07-02",
            totalDistanceMeters = 0.0,
            cities = emptyList(),
            countries = emptyList(),
            days = listOf(
                TripDay(
                    dayIndex = 1,
                    dateIso = "2026-07-01",
                    timezoneId = "Asia/Seoul",
                    items = listOf(
                        TripDayItem.VisitItem(
                            Visit("v1", "Place", null, null, GeoPoint(37.0, 127.0), 500L, 3000L, 0.9f),
                            listOf(photo, video)
                        )
                    ),
                    totalDistanceMeters = 0.0,
                    photoCount = 2
                )
            ),
            totalMediaCount = 2,
            createdAtEpochMs = 1000L
        )

        repository.saveTrip(trip)

        val loaded = repository.getTripById("trip-media-test")
        assertNotNull(loaded)
        val loadedVisit = loaded!!.days[0].items[0] as TripDayItem.VisitItem
        assertEquals(2, loadedVisit.photos.size)
        val ids = loadedVisit.photos.map { it.id }.toSet()
        assertTrue(ids.contains("IMG_123"))
        assertTrue(ids.contains("VID_123"))
    }

    @Test
    fun testUserOverride_SurvivesAcrossTripReImportAndMultipleTrips() = runBlocking {
        val segment = MovementSegment(
            id = "seg-source-override",
            startTimestampEpochMs = 1000L,
            endTimestampEpochMs = 2000L,
            startPoint = GeoPoint(37.5, 127.0),
            endPoint = GeoPoint(37.6, 127.1),
            distanceMeters = 15000.0,
            durationMillis = 1000L,
            transport = TransportPrediction(TransportMode.CAR, 0.7f, "Predicted Car")
        )

        val trip1 = Trip(
            id = "trip-1",
            title = "Trip 1",
            startDateIso = "2026-07-01",
            endDateIso = "2026-07-02",
            totalDistanceMeters = 15000.0,
            cities = emptyList(),
            countries = emptyList(),
            days = listOf(
                TripDay(
                    dayIndex = 1,
                    dateIso = "2026-07-01",
                    timezoneId = "Asia/Seoul",
                    items = listOf(TripDayItem.MovementItem(segment, emptyList())),
                    totalDistanceMeters = 15000.0,
                    photoCount = 0
                )
            ),
            totalMediaCount = 0,
            createdAtEpochMs = 1000L
        )

        repository.saveTrip(trip1)

        // User overrides transport mode in Trip 1 to BUS
        repository.updateTransportMode("trip-1", "seg-source-override", TransportMode.BUS)

        val reloadedTrip1 = repository.getTripById("trip-1")
        assertNotNull(reloadedTrip1)
        val segTrip1 = (reloadedTrip1!!.days[0].items[0] as TripDayItem.MovementItem).segment
        assertEquals(TransportMode.BUS, segTrip1.effectiveMode)
        assertTrue(segTrip1.isUserOverride)

        // Now, Trip 2 is imported from the same Timeline source (sharing seg-source-override)
        val trip2 = Trip(
            id = "trip-2",
            title = "Trip 2",
            startDateIso = "2026-07-01",
            endDateIso = "2026-07-03",
            totalDistanceMeters = 15000.0,
            cities = emptyList(),
            countries = emptyList(),
            days = listOf(
                TripDay(
                    dayIndex = 1,
                    dateIso = "2026-07-01",
                    timezoneId = "Asia/Seoul",
                    items = listOf(TripDayItem.MovementItem(segment, emptyList())),
                    totalDistanceMeters = 15000.0,
                    photoCount = 0
                )
            ),
            totalMediaCount = 0,
            createdAtEpochMs = 2000L
        )

        repository.saveTrip(trip2)

        // Reload Trip 2: the durable source override (BUS) is automatically applied!
        val reloadedTrip2 = repository.getTripById("trip-2")
        assertNotNull(reloadedTrip2)
        val segTrip2 = (reloadedTrip2!!.days[0].items[0] as TripDayItem.MovementItem).segment
        assertEquals(TransportMode.BUS, segTrip2.effectiveMode)
        assertTrue(segTrip2.isUserOverride)
    }

    @Test
    fun testMidnightBoundaryPhoto_PreservesCaptureTimezoneAndLocalDayAcrossRoomPersistence() = runBlocking {
        // 2026-07-11 00:30:00 KST is 2026-07-10 15:30:00 UTC
        val kstZone = java.time.ZoneId.of("Asia/Seoul")
        val localMidnightTime = java.time.LocalDateTime.of(2026, 7, 11, 0, 30, 0)
        val kstInstant = localMidnightTime.atZone(kstZone).toInstant()
        val epochMs = kstInstant.toEpochMilli()

        // GPS-less photo resolved to Asia/Seoul
        val midnightPhoto = MediaItem(
            id = "IMG_MIDNIGHT_01",
            contentUriString = "content://media/photos/101",
            fileName = "IMG_20260711_003000.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = epochMs,
            timestampConfidence = TimestampConfidence.EXIF_LOCAL,
            captureTimezoneId = "Asia/Seoul",
            location = null
        )

        val day1Visit = Visit(
            id = "visit-day-1",
            placeName = "Seoul Day 1 Afternoon Cafe",
            location = GeoPoint(37.5665, 126.9780),
            startTimestampEpochMs = java.time.LocalDateTime.of(2026, 7, 10, 14, 0, 0).atZone(kstZone).toInstant().toEpochMilli(),
            endTimestampEpochMs = java.time.LocalDateTime.of(2026, 7, 10, 15, 0, 0).atZone(kstZone).toInstant().toEpochMilli(),
            confidence = 0.95f
        )

        val day2Visit = Visit(
            id = "visit-day-2",
            placeName = "Seoul Day 2 Morning Bakery",
            location = GeoPoint(37.5665, 126.9780),
            startTimestampEpochMs = java.time.LocalDateTime.of(2026, 7, 11, 9, 0, 0).atZone(kstZone).toInstant().toEpochMilli(),
            endTimestampEpochMs = java.time.LocalDateTime.of(2026, 7, 11, 10, 0, 0).atZone(kstZone).toInstant().toEpochMilli(),
            confidence = 0.95f
        )

        val trip = Trip(
            id = "trip-midnight-test",
            title = "Midnight Photo Test",
            startDateIso = "2026-07-10",
            endDateIso = "2026-07-11",
            totalDistanceMeters = 0.0,
            cities = listOf("Seoul"),
            countries = listOf("South Korea"),
            days = listOf(
                TripDay(
                    dayIndex = 1,
                    dateIso = "2026-07-10",
                    timezoneId = "Asia/Seoul",
                    items = listOf(TripDayItem.VisitItem(day1Visit, emptyList())),
                    totalDistanceMeters = 0.0,
                    photoCount = 0
                ),
                TripDay(
                    dayIndex = 2,
                    dateIso = "2026-07-11",
                    timezoneId = "Asia/Seoul",
                    items = listOf(
                        TripDayItem.VisitItem(day2Visit, emptyList()),
                        TripDayItem.UnassignedPhotosItem(listOf(midnightPhoto))
                    ),
                    unassignedPhotos = listOf(midnightPhoto),
                    totalDistanceMeters = 0.0,
                    photoCount = 1
                )
            ),
            totalMediaCount = 1,
            createdAtEpochMs = 1000L
        )

        // 1. Save trip to database
        repository.saveTrip(trip)

        // 2. Create a brand new Repository instance to simulate fresh app restart
        val freshRepo = TripRepositoryImpl(db)
        val reloadedTrip = freshRepo.getTripById("trip-midnight-test")

        assertNotNull("Trip must exist after reload", reloadedTrip)
        assertEquals("Trip should have 2 days", 2, reloadedTrip!!.days.size)

        val day1 = reloadedTrip.days.find { it.dateIso == "2026-07-10" }
        val day2 = reloadedTrip.days.find { it.dateIso == "2026-07-11" }

        assertNotNull("Day 1 (2026-07-10) must exist", day1)
        assertNotNull("Day 2 (2026-07-11) must exist", day2)

        // Crucial assertions: photo MUST be in Day 2 (2026-07-11), NEVER shifted to Day 1 (2026-07-10)
        assertEquals("Day 1 should have 0 unassigned photos", 0, day1!!.unassignedPhotos.size)
        assertEquals("Day 2 must contain the midnight photo", 1, day2!!.unassignedPhotos.size)

        val loadedPhoto = day2.unassignedPhotos[0]
        assertEquals("IMG_MIDNIGHT_01", loadedPhoto.id)
        assertEquals("Asia/Seoul", loadedPhoto.captureTimezoneId)

        // Formatted time in capture timezone must be exactly 00:30
        val photoZone = java.time.ZoneId.of(loadedPhoto.captureTimezoneId!!)
        val formattedTime = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            .withZone(photoZone)
            .format(java.time.Instant.ofEpochMilli(loadedPhoto.timestampEpochMs!!))
        assertEquals("Photo capture time must remain 00:30 across restarts", "00:30", formattedTime)
    }
}
