package com.traveler.core.common.time

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import com.traveler.data.repository.TripRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class ColdStartSavedTripTimeShapeTest {

    @Test
    fun testSavedTripViewing_DoesNotInitializeTimeShapeEngine() = runBlocking {
        // Reset TimeShape factory and count factory invocations
        val factoryInvocationCount = AtomicInteger(0)
        GeoTimezoneEngine.resetForTesting()
        GeoTimezoneEngine.engineFactory = {
            factoryInvocationCount.incrementAndGet()
            null
        }

        val t1 = Instant.parse("2026-07-01T14:00:00Z").toEpochMilli() // 10:00 EDT
        val t2 = Instant.parse("2026-07-02T14:00:00Z").toEpochMilli() // 10:00 EDT

        // Reconstruct saved trip from domain objects with pre-persisted timezones across 2 days
        val v1 = Visit(
            id = "v1",
            placeName = "Boston Back Bay",
            placeAddress = null,
            placeId = null,
            location = GeoPoint(42.3503, -71.0810),
            startTimestampEpochMs = t1,
            endTimestampEpochMs = t1 + 3600000L,
            confidence = 0.95f,
            timezoneId = "America/New_York"
        )
        val v2 = Visit(
            id = "v2",
            placeName = "Toronto CN Tower",
            placeAddress = null,
            placeId = null,
            location = GeoPoint(43.6426, -79.3871),
            startTimestampEpochMs = t2,
            endTimestampEpochMs = t2 + 3600000L,
            confidence = 0.95f,
            timezoneId = "America/Toronto"
        )
        val seg = MovementSegment(
            id = "s1",
            startTimestampEpochMs = t1 + 3600000L,
            endTimestampEpochMs = t1 + 7200000L,
            startPoint = GeoPoint(42.3503, -71.0810),
            endPoint = GeoPoint(43.0896, -79.0849),
            simplifiedPoints = listOf(GeoPoint(42.3503, -71.0810), GeoPoint(43.0896, -79.0849)),
            distanceMeters = 690000.0,
            durationMillis = 3600000L,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.95f, "Flight"),
            startTimezoneId = "America/New_York",
            endTimezoneId = "America/Toronto"
        )
        val photo = MediaItem(
            id = "m1",
            contentUriString = "content://media/1",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = t1 + 1000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = "America/New_York",
            location = GeoPoint(42.3503, -71.0810),
            locationConfidence = LocationConfidenceLevel.GPS_EXACT,
            confidenceScore = 0.98f,
            matchedVisitId = "v1",
            assignedDayIso = "2026-07-01",
            dayAssignmentConfidence = DayAssignmentConfidence.CONTEXTUAL,
            dayAssignmentProvenance = "Matched to visit Boston Back Bay"
        )

        val days = TripRepositoryImpl.reconstructDays(
            visits = listOf(v1, v2),
            segments = listOf(seg),
            mediaItems = listOf(photo),
            tripStartDateIso = "2026-07-01",
            tripEndDateIso = "2026-07-02"
        )

        assertNotNull("Reconstructed days must not be null", days)
        assertEquals(2, days.size)
        assertEquals(0, factoryInvocationCount.get()) // TimeShape was NEVER initialized!
    }
}
