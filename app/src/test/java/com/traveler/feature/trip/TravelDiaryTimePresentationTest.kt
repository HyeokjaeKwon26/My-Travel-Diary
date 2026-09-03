package com.traveler.feature.trip

import com.traveler.core.common.time.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

class TravelDiaryTimePresentationTest {

    @Test
    fun testTravelDiaryTimeDisplay_UsesTravelLocalTimezone_NotDeviceTimezone() {
        val originalTz = TimeZone.getDefault()
        try {
            // Set device timezone to America/New_York
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            // Historical travel event occurred in Seoul at 15:00 KST
            val seoulEventInstant = Instant.parse("2026-07-10T06:00:00Z")
            val travelDayZoneId = ZoneId.of("Asia/Seoul")

            // Format using the travel day timezone
            val formattedTime = TimeUtils.formatTime(seoulEventInstant, travelDayZoneId)
            val formattedDate = TimeUtils.formatDate(seoulEventInstant, travelDayZoneId)

            assertEquals("15:00", formattedTime)
            assertEquals("2026-07-10", formattedDate)

            // If it had used device default (New York), it would have been 02:00 EDT
            val nyTime = TimeUtils.formatTime(seoulEventInstant, ZoneId.of("America/New_York"))
            assertEquals("02:00", nyTime)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun testCrossTimezoneMovement_BostonToChicago() {
        // Boston (EDT UTC-4) 08:00 -> Chicago (CDT UTC-5) 09:30 (2h30m flight)
        val departure = Instant.parse("2026-07-01T12:00:00Z") // 08:00 EDT
        val arrival = Instant.parse("2026-07-01T14:30:00Z")   // 09:30 CDT
        val bostonZone = ZoneId.of("America/New_York")
        val chicagoZone = ZoneId.of("America/Chicago")

        val result = TimeUtils.formatMovementTimeSpan(departure, bostonZone, arrival, chicagoZone)
        assertTrue("Should contain departure in EDT and arrival in CDT", result.contains("08:00") && result.contains("09:30"))
        assertTrue("Should not have day offset", !result.contains("+1 day"))
    }

    @Test
    fun testCrossTimezoneMovement_NewYorkToSeoul_PlusOneDay() {
        // New York (EDT UTC-4) 19:30 July 1 -> Seoul (KST UTC+9) 23:30 July 2 (15h flight)
        val departure = Instant.parse("2026-07-01T23:30:00Z") // 19:30 EDT July 1
        val arrival = Instant.parse("2026-07-02T14:30:00Z")   // 23:30 KST July 2
        val nyZone = ZoneId.of("America/New_York")
        val seoulZone = ZoneId.of("Asia/Seoul")

        val result = TimeUtils.formatMovementTimeSpan(departure, nyZone, arrival, seoulZone)
        assertTrue("Should contain departure 19:30 and arrival 23:30", result.contains("19:30") && result.contains("23:30"))
        assertTrue("Must indicate (+1 day) across dateline", result.contains("(+1 day)"))
    }

    @Test
    fun testCrossTimezoneMovement_LosAngelesToNewYork() {
        // Los Angeles (PDT UTC-7) 22:00 -> New York (EDT UTC-4) 06:15 next day (5h15m red-eye)
        val departure = Instant.parse("2026-07-01T05:00:00Z") // 22:00 PDT July 0
        val arrival = Instant.parse("2026-07-01T10:15:00Z")   // 06:15 EDT July 1
        val laZone = ZoneId.of("America/Los_Angeles")
        val nyZone = ZoneId.of("America/New_York")

        val result = TimeUtils.formatMovementTimeSpan(departure, laZone, arrival, nyZone)
        assertTrue("Should contain 22:00 and 06:15", result.contains("22:00") && result.contains("06:15"))
        assertTrue("Must indicate (+1 day) for overnight flight", result.contains("(+1 day)"))
    }

    @Test
    fun testCrossTimezoneMovement_LondonToParis() {
        // London (BST UTC+1) 10:00 -> Paris (CEST UTC+2) 13:20 (Eurostar 2h20m)
        val departure = Instant.parse("2026-07-01T09:00:00Z") // 10:00 BST
        val arrival = Instant.parse("2026-07-01T11:20:00Z")   // 13:20 CEST
        val londonZone = ZoneId.of("Europe/London")
        val parisZone = ZoneId.of("Europe/Paris")

        val result = TimeUtils.formatMovementTimeSpan(departure, londonZone, arrival, parisZone)
        assertTrue("Should contain 10:00 and 13:20", result.contains("10:00") && result.contains("13:20"))
    }

    @Test
    fun testVisitWithNullTimezone_DisplaysUnavailableInsteadOfFakeUtc() {
        val startTs = Instant.parse("2026-07-02T10:00:00Z").toEpochMilli()
        val endTs = Instant.parse("2026-07-02T12:00:00Z").toEpochMilli()
        val visitZone: ZoneId? = null

        val formatted = if (visitZone != null) {
            val startTime = TimeUtils.formatTime(Instant.ofEpochMilli(startTs), visitZone)
            val endTime = TimeUtils.formatTime(Instant.ofEpochMilli(endTs), visitZone)
            "$startTime ~ $endTime"
        } else {
            val durationMs = maxOf(0L, endTs - startTs)
            val durationText = TimeUtils.formatDuration(durationMs)
            "Local timezone unavailable ($durationText)"
        }

        assertEquals("Local timezone unavailable (2h 0m)", formatted)
    }

    @Test
    fun testPhotoWithNullTimezoneAndAssignedDay_DisplaysAssignedDayAndUnknownZone() {
        val photoTs = Instant.parse("2026-07-02T15:30:00Z").toEpochMilli()
        val photoZone: ZoneId? = null
        val assignedDayIso: String? = "2026-07-02"

        val captureTimeStr = when {
            photoZone != null -> {
                val inst = Instant.ofEpochMilli(photoTs)
                "${TimeUtils.formatDate(inst, photoZone)} · ${TimeUtils.formatTime(inst, photoZone)}"
            }
            assignedDayIso != null -> {
                "$assignedDayIso · Capture timezone unknown"
            }
            else -> {
                "Capture date/time uncertain"
            }
        }

        assertEquals("2026-07-02 · Capture timezone unknown", captureTimeStr)
    }

    @Test
    fun testPhotoWithNullTimezoneAndNullAssignedDay_DisplaysCaptureTimeUncertain() {
        val photoTs: Long? = null
        val photoZone: ZoneId? = null
        val assignedDayIso: String? = null

        val captureTimeStr = when {
            photoTs != null && photoZone != null -> {
                val inst = Instant.ofEpochMilli(photoTs)
                "${TimeUtils.formatDate(inst, photoZone)} · ${TimeUtils.formatTime(inst, photoZone)}"
            }
            photoTs != null && assignedDayIso != null -> {
                "$assignedDayIso · Capture timezone unknown"
            }
            else -> {
                "Capture date/time uncertain"
            }
        }

        assertEquals("Capture date/time uncertain", captureTimeStr)
    }
}

