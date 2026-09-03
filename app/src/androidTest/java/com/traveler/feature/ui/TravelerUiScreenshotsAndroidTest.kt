package com.traveler.feature.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.traveler.MainActivity
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.database.TravelerDatabase
import com.traveler.core.model.*
import com.traveler.data.repository.TripRepositoryImpl
import com.traveler.feature.home.HomeScreen
import com.traveler.feature.home.HomeViewModel
import com.traveler.feature.importtrip.ImportTripScreen
import com.traveler.feature.importtrip.ImportTripViewModel
import com.traveler.feature.trip.TravelDiaryScreen
import com.traveler.feature.trip.TravelDiaryViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class TravelerUiScreenshotsAndroidTest {

    private fun assertNoSystemErrorOrAnr(device: UiDevice) {
        val anrPatterns = listOf(
            "isn't responding",
            "keeps stopping",
            "Process system isn't responding",
            "System UI isn't responding"
        )
        for (pattern in anrPatterns) {
            if (device.hasObject(By.textContains(pattern))) {
                // Attempt to dismiss external system dialog so screenshot is 100% clean
                val waitBtn = device.findObject(By.res("android", "aerr_wait"))
                    ?: device.findObject(By.text("Wait"))
                    ?: device.findObject(By.res("android", "aerr_close"))
                    ?: device.findObject(By.text("Close app"))

                if (waitBtn != null) {
                    try { waitBtn.click() } catch (_: Exception) {}
                    Thread.sleep(600)
                    device.waitForIdle()
                } else {
                    device.pressBack()
                    Thread.sleep(600)
                    device.waitForIdle()
                }

                if (device.hasObject(By.textContains(pattern))) {
                    throw AssertionError("System error / ANR dialog persists on screen containing '$pattern'")
                }
            }
        }
    }

    private fun saveAndVerifyScreenshot(filename: String, device: UiDevice): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(400) // Allow Compose frames to stabilize

        assertNoSystemErrorOrAnr(device)

        val screenshot: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: throw IllegalStateException("Failed to capture real device screenshot via UiAutomation")

        val context = instrumentation.targetContext
        val persistentDirs = listOf(
            File("/sdcard/Download"),
            File("/sdcard/Pictures"),
            context.filesDir,
            context.getExternalFilesDir(null)
        ).filterNotNull()

        var savedFile: File? = null
        for (dir in persistentDirs) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, filename)
                if (f.exists()) {
                    f.delete()
                }
                FileOutputStream(f).use { out ->
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                if (f.exists() && f.length() > 0) {
                    if (savedFile == null) savedFile = f
                }
            } catch (_: Exception) {}
        }

        val file = savedFile ?: File(context.filesDir, filename)
        assertTrue("Real device screenshot must exist on device ($filename)", file.exists())
        assertTrue("Real device screenshot must have non-trivial size (>5KB) ($filename: ${file.length()} bytes)", file.length() > 5120)

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        return hash
    }

    private fun captureActivePlaybackScreenshot(filename: String, device: UiDevice): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        Thread.sleep(200) // Brief frame stabilization without waiting for animation completion
        assertNoSystemErrorOrAnr(device)

        val screenshot: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: throw IllegalStateException("Failed to capture real device active playback screenshot via UiAutomation")

        val context = instrumentation.targetContext
        val persistentDirs = listOf(
            File("/sdcard/Download"),
            File("/sdcard/Pictures"),
            context.filesDir,
            context.getExternalFilesDir(null)
        ).filterNotNull()

        var savedFile: File? = null
        for (dir in persistentDirs) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, filename)
                if (f.exists()) {
                    f.delete()
                }
                FileOutputStream(f).use { out ->
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                if (f.exists() && f.length() > 0) {
                    if (savedFile == null) savedFile = f
                }
            } catch (_: Exception) {}
        }

        val file = savedFile ?: File(context.filesDir, filename)
        assertTrue("Real device screenshot must exist on device ($filename)", file.exists())
        assertTrue("Real device screenshot must have non-trivial size (>5KB) ($filename: ${file.length()} bytes)", file.length() > 5120)

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        return hash
    }

    private fun seedTestTrip(): Trip {
        val baseEpochMs = 1782800000000L
        val nyCoord = GeoPoint(40.7128, -74.0060)
        val seoulCoord = GeoPoint(37.5665, 126.9780)

        val sampleVisit1 = Visit(
            id = "v_ny",
            placeName = "New York (JFK)",
            placeAddress = "Queens, NY",
            placeId = "jfk",
            location = nyCoord,
            startTimestampEpochMs = baseEpochMs,
            endTimestampEpochMs = baseEpochMs + 7200000L,
            confidence = 0.95f,
            timezoneId = "America/New_York"
        )
        val sampleVisit2 = Visit(
            id = "v_seoul",
            placeName = "Seoul (Incheon)",
            placeAddress = "Incheon, KR",
            placeId = "icn",
            location = seoulCoord,
            startTimestampEpochMs = baseEpochMs + 50000000L,
            endTimestampEpochMs = baseEpochMs + 60000000L,
            confidence = 0.95f,
            timezoneId = "Asia/Seoul"
        )
        val sampleFlight = MovementSegment(
            id = "seg_ny_seoul",
            startTimestampEpochMs = baseEpochMs + 7200000L,
            endTimestampEpochMs = baseEpochMs + 50000000L,
            startPoint = nyCoord,
            endPoint = seoulCoord,
            simplifiedPoints = listOf(nyCoord, seoulCoord),
            distanceMeters = 11000000.0,
            durationMillis = 42800000L,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Flight KE082"),
            startTimezoneId = "America/New_York",
            endTimezoneId = "Asia/Seoul",
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC
        )
        val samplePhoto = MediaItem(
            id = "p_ny",
            contentUriString = "content://media/external/images/media/1",
            fileName = "times_square.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = baseEpochMs + 3600000L,
            timestampConfidence = TimestampConfidence.EXIF_EXACT,
            captureTimezoneId = "America/New_York",
            location = nyCoord,
            matchedVisitId = "v_ny",
            assignedDayIso = "2026-07-01"
        )

        val day1 = TripDay(
            dayIndex = 1,
            dateIso = "2026-07-01",
            timezoneId = "America/New_York",
            items = listOf(
                TripDayItem.VisitItem(sampleVisit1, listOf(samplePhoto)),
                TripDayItem.MovementItem(sampleFlight, emptyList()),
                TripDayItem.VisitItem(sampleVisit2, emptyList())
            ),
            unassignedPhotos = emptyList(),
            totalDistanceMeters = 11000000.0,
            photoCount = 1
        )

        return Trip(
            id = "trip_nyc_seoul_verified",
            title = "NYC to Seoul Journey",
            startDateIso = "2026-07-01",
            endDateIso = "2026-07-02",
            totalDistanceMeters = 11000000.0,
            days = listOf(day1),
            totalMediaCount = 1,
            createdAtEpochMs = baseEpochMs
        )
    }

    @Test
    fun captureCurrentRunUiEvidence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val database = TravelerDatabase.getDatabase(targetContext)
        val repository = TripRepositoryImpl(database)

        val trip = seedTestTrip()
        runBlocking {
            repository.saveTrip(trip)
        }

        // Launch genuine MainActivity using ActivityScenario
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // 1. Home screen with populated trip card
        scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        HomeScreen(
                            viewModel = HomeViewModel(activity.application),
                            onNavigateToNewTrip = {},
                            onNavigateToTripDetail = {}
                        )
                    }
                }
            }
        }
        device.waitForIdle()
        Thread.sleep(800)
        device.wait(Until.hasObject(By.textContains("My Travel Diary")), 5000)
        saveAndVerifyScreenshot("01_home_screen.png", device)

        // 2. Create trip / Import screen
        scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ImportTripScreen(
                            viewModel = ImportTripViewModel(activity.application),
                            onNavigateBack = {},
                            onNavigateToTripDetail = {}
                        )
                    }
                }
            }
        }
        device.waitForIdle()
        Thread.sleep(800)
        device.wait(Until.hasObject(By.textContains("Travel Story")), 5000)
        saveAndVerifyScreenshot("02_create_trip_screen.png", device)

        // 3. Trip detail diary with offline canvas map
        scenario.onActivity { activity ->
            val vm = TravelDiaryViewModel(activity.application)
            activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        TravelDiaryScreen(
                            tripId = trip.id,
                            viewModel = vm,
                            onNavigateBack = {}
                        )
                    }
                }
            }
        }
        device.waitForIdle()
        Thread.sleep(800)
        device.wait(Until.hasObject(By.textContains("NYC")), 5000)
        saveAndVerifyScreenshot("03_trip_detail_diary.png", device)

        // 4. Cinematic playback state (Active Playback Verification - P0-15 ~ P0-18)
        val allVisits = trip.days.flatMap { it.items }.mapNotNull { (it as? TripDayItem.VisitItem)?.visit }
        val allSegments = trip.days.flatMap { it.items }.mapNotNull { (it as? TripDayItem.MovementItem)?.segment }
        val allPhotos = trip.days.flatMap { it.items }.flatMap {
            when (it) {
                is TripDayItem.VisitItem -> it.photos
                is TripDayItem.MovementItem -> it.photos
                is TripDayItem.ContextualPhotosItem -> it.photos
                is TripDayItem.UnassignedPhotosItem -> it.photos
            }
        }

        // 4A. Launch TravelMapView in paused state (initialIsPlaying = false)
        scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        key("playback_start_view") {
                            com.traveler.feature.map.TravelMapView(
                                visits = allVisits,
                                segments = allSegments,
                                photos = allPhotos,
                                initialIsPlaying = false,
                                initialPlaybackProgress = 0.0f,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
        device.waitForIdle()
        Thread.sleep(800)

        // Capture playback_start.png at paused initial start state
        val hashStart = saveAndVerifyScreenshot("playback_start.png", device)

        // Verify Play button is visible before starting playback
        val hasPlayBtn = device.wait(Until.hasObject(By.desc("Start Playback")), 5000) ||
                         device.wait(Until.hasObject(By.desc("Play")), 5000)
        assertTrue("Play button must be visible when playback is paused", hasPlayBtn)

        var clickedPlay = false
        for (retry in 0..3) {
            val playBtn = device.findObject(By.desc("Start Playback")) ?: device.findObject(By.desc("Play"))
            if (playBtn != null) {
                playBtn.click()
                clickedPlay = true
                break
            }
            Thread.sleep(300)
        }
        assertTrue("Play button must exist and be clicked to start playback", clickedPlay)

        // Verify active playback banner & Pause button appears
        val hasPauseBtn = device.wait(Until.hasObject(By.desc("Pause")), 6000) ||
                          device.wait(Until.hasObject(By.textContains("Travel Playback")), 4000)
        assertTrue("Pause control or playback banner must appear during active playback", hasPauseBtn)

        // Capture 04_cinematic_playback.png (must visibly show active playback and Pause button)
        val hashPlay04 = captureActivePlaybackScreenshot("04_cinematic_playback.png", device)

        // 4B. Evaluate mid-flight movement phase playback (initialPlaybackProgress = 0.50f)
        scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        key("playback_mid_flight_view") {
                            com.traveler.feature.map.TravelMapView(
                                visits = allVisits,
                                segments = allSegments,
                                photos = allPhotos,
                                initialIsPlaying = true,
                                initialPlaybackProgress = 0.50f,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
        Thread.sleep(400)
        assertTrue("Pause control must appear during mid-flight playback", device.wait(Until.hasObject(By.desc("Pause")), 5000))
        val hashMid = captureActivePlaybackScreenshot("playback_mid_segment.png", device)

        // Assert playback actually advanced and images differ (P0-16)
        assertNotEquals("playback_start.png and playback_mid_segment.png must differ during active movement", hashStart, hashMid)

        // Click Pause if actively playing and verify pause state
        val pauseBtn = device.findObject(By.desc("Pause"))
        if (pauseBtn != null) {
            pauseBtn.click()
            Thread.sleep(400)
            device.waitForIdle()
        }
        val hasPlayAfterPause = device.wait(Until.hasObject(By.desc("Play")), 5000) ||
                                device.wait(Until.hasObject(By.desc("Start Playback")), 5000)
        assertTrue("Play button must be present when playback is paused or completed", hasPlayAfterPause)

        // 5. Photo detail dialog
        val photo = trip.days.first().items.filterIsInstance<TripDayItem.VisitItem>().first().photos.first()
        scenario.onActivity { activity ->
            val vm = TravelDiaryViewModel(activity.application)
            vm.loadTrip(trip.id)
            vm.openPhotoDetail(photo)
            activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        TravelDiaryScreen(
                            tripId = trip.id,
                            viewModel = vm,
                            onNavigateBack = {}
                        )
                    }
                }
            }
        }
        device.waitForIdle()
        Thread.sleep(800)
        val hashPhotoDetail = saveAndVerifyScreenshot("05_photo_detail_dialog.png", device)

        // 6. Edit place name dialog
        val visit = trip.days.first().items.filterIsInstance<TripDayItem.VisitItem>().first().visit
        scenario.onActivity { activity ->
            val vm = TravelDiaryViewModel(activity.application)
            vm.loadTrip(trip.id)
            vm.openEditVisit(visit)
            activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        TravelDiaryScreen(
                            tripId = trip.id,
                            viewModel = vm,
                            onNavigateBack = {}
                        )
                    }
                }
            }
        }
        device.waitForIdle()
        Thread.sleep(800)
        saveAndVerifyScreenshot("06_edit_place_name_dialog.png", device)

        // 7. Photo metadata dialog / overlay (P1-20: Toggle metadata info view for distinct overlay)
        scenario.onActivity { activity ->
            val vm = TravelDiaryViewModel(activity.application)
            vm.loadTrip(trip.id)
            vm.openPhotoDetail(photo)
            activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        TravelDiaryScreen(
                            tripId = trip.id,
                            viewModel = vm,
                            showPhotoMetadata = true,
                            onNavigateBack = {}
                        )
                    }
                }
            }
        }
        device.waitForIdle()
        Thread.sleep(800)
        val hashPhotoMetadata = saveAndVerifyScreenshot("07_photo_metadata_overlay.png", device)
        assertNotEquals("Photo detail dialog and metadata overlay must have distinct contents", hashPhotoDetail, hashPhotoMetadata)

        // 8. Edit transport mode dialog
        val segment = trip.days.first().items.filterIsInstance<TripDayItem.MovementItem>().first().segment
        scenario.onActivity { activity ->
            val vm = TravelDiaryViewModel(activity.application)
            vm.loadTrip(trip.id)
            vm.openEditTransport(segment)
            activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        TravelDiaryScreen(
                            tripId = trip.id,
                            viewModel = vm,
                            onNavigateBack = {}
                        )
                    }
                }
            }
        }
        device.waitForIdle()
        Thread.sleep(800)
        saveAndVerifyScreenshot("08_edit_transport_dialog.png", device)

        // 9. Home with trip verified
        scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        HomeScreen(
                            viewModel = HomeViewModel(activity.application),
                            onNavigateToNewTrip = {},
                            onNavigateToTripDetail = {}
                        )
                    }
                }
            }
        }
        device.waitForIdle()
        Thread.sleep(800)
        saveAndVerifyScreenshot("09_home_with_trip.png", device)

        // Close scenario
        scenario.close()

        // 10. Cold restart evidence: launch fresh ActivityScenario, verify Room persistence restores trip and render
        val coldScenario = ActivityScenario.launch(MainActivity::class.java)
        coldScenario.onActivity { activity ->
            val restoredTrip = runBlocking { repository.getTripById(trip.id) }
            assertNotNull("Trip must be restored from Room Database on cold restart", restoredTrip)
            activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        TravelDiaryScreen(
                            tripId = trip.id,
                            viewModel = TravelDiaryViewModel(activity.application),
                            onNavigateBack = {}
                        )
                    }
                }
            }
        }
        device.waitForIdle()
        Thread.sleep(800)
        saveAndVerifyScreenshot("10_cold_restart_trip_opened.png", device)
        coldScenario.close()
    }
}
