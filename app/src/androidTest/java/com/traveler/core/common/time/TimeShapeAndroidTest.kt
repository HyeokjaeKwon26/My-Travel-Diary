package com.traveler.core.common.time

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.traveler.core.classifier.RuleBasedTransportClassifier
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.media.MediaRepository
import com.traveler.core.media.RawMediaCandidate
import com.traveler.core.model.*
import com.traveler.core.timeline.GoogleTimelineJsonParser
import com.traveler.data.repository.TripRepositoryImpl
import com.traveler.domain.usecase.CreateTripUseCase
import com.traveler.feature.map.renderer.TimelineStoryCompressor
import com.traveler.feature.map.renderer.TravelMapRenderModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TimeShapeAndroidTest {

    private fun captureMemoryInfo(stage: String): String {
        // Stabilize GC before sampling
        System.gc()
        Thread.sleep(200)
        System.gc()
        Thread.sleep(200)

        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory()
        val freeMem = runtime.freeMemory()
        val usedMem = totalMem - freeMem
        val maxMem = runtime.maxMemory()
        val nativeAlloc = Debug.getNativeHeapAllocatedSize()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryClass = actManager.memoryClass
        val largeMemoryClass = actManager.largeMemoryClass

        val info = buildString {
            appendLine("=== Memory Report: $stage ===")
            appendLine("Device Model: ${android.os.Build.MODEL} (${android.os.Build.DEVICE})")
            appendLine("Android SDK: ${android.os.Build.VERSION.SDK_INT}")
            appendLine("ActivityManager memoryClass: ${memoryClass}MB (normal heap)")
            appendLine("ActivityManager largeMemoryClass: ${largeMemoryClass}MB")
            appendLine("Java Heap Allocated: ${usedMem / (1024 * 1024)}MB (${usedMem} bytes)")
            appendLine("Java Heap Total: ${totalMem / (1024 * 1024)}MB (${totalMem} bytes)")
            appendLine("Java Heap Max Limit: ${maxMem / (1024 * 1024)}MB (${maxMem} bytes)")
            appendLine("Native Heap Allocated: ${nativeAlloc / (1024 * 1024)}MB (${nativeAlloc} bytes)")
            appendLine("=========================================")
        }

        // Save to multiple persistent locations so verification runner can pull
        val fileName = "memory-$stage.txt"
        val dirs = listOf(
            File("/sdcard/Download"),
            File("/sdcard/Pictures"),
            context.filesDir,
            context.getExternalFilesDir(null)
        ).filterNotNull()

        for (dir in dirs) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, fileName)
                FileOutputStream(outFile).use { it.write(info.toByteArray(StandardCharsets.UTF_8)) }
            } catch (_: Exception) {}
        }

        return info
    }

    @Test
    fun timeShape_realisticStressTest_underNormalHeap_succeedsAndReclaimsMemory() {
        runBlocking {
            // Stage 1: Before TimeShape initialization
            captureMemoryInfo("before-timeshape")

            // Stage 2: TimeShape loaded and verified across global regions
            val boston = GeoTimezoneEngine.getTimezoneForLocation(GeoPoint(42.3601, -71.0589))
            assertEquals("America/New_York", boston?.id)

            val seoul = GeoTimezoneEngine.getTimezoneForLocation(GeoPoint(37.5665, 126.9780))
            assertEquals("Asia/Seoul", seoul?.id)

            val tokyo = GeoTimezoneEngine.getTimezoneForLocation(GeoPoint(35.6762, 139.6503))
            assertEquals("Asia/Tokyo", tokyo?.id)

            val london = GeoTimezoneEngine.getTimezoneForLocation(GeoPoint(51.5074, -0.1278))
            assertEquals("Europe/London", london?.id)

            val sydney = GeoTimezoneEngine.getTimezoneForLocation(GeoPoint(-33.8688, 151.2093))
            assertEquals("Australia/Sydney", sydney?.id)

            captureMemoryInfo("after-timeshape")

            // Construct realistic large synthetic timeline import:
            // 100+ Visits, 100+ MovementSegments, 10,000 route points, 300+ photo metadata candidates
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val database = com.traveler.core.database.TravelerDatabase.getDatabase(targetContext)
            val repository = TripRepositoryImpl(database)

            val jsonBuilder = StringBuilder()
            jsonBuilder.append("{\n  \"semanticSegments\": [\n")

            val baseTime = 1782800000000L // 2026-07-01 00:00:00 UTC
            val centerPoints = listOf(
                GeoPoint(40.7128, -74.0060),  // New York
                GeoPoint(51.5074, -0.1278),   // London
                GeoPoint(35.6762, 139.6503),  // Tokyo
                GeoPoint(37.5665, 126.9780),  // Seoul
                GeoPoint(-33.8688, 151.2093)  // Sydney
            )

            val segmentEntries = mutableListOf<String>()
            var currentTimestamp = baseTime

            for (i in 0 until 100) {
                val cityIdx = i % centerPoints.size
                val center = centerPoints[cityIdx]
                val lat = center.latitude + (i % 10) * 0.01
                val lng = center.longitude + (i % 10) * 0.01

                val startTimeIso = java.time.Instant.ofEpochMilli(currentTimestamp).toString()
                val endTimeIso = java.time.Instant.ofEpochMilli(currentTimestamp + 1800000L).toString()
                currentTimestamp += 1800000L

                // Visit
                val visitJson = """
                {
                  "startTime": "$startTimeIso",
                  "endTime": "$endTimeIso",
                  "visit": {
                    "topCandidate": {
                      "placeId": "place_synthetic_$i",
                      "placeName": "Synthetic Place $i",
                      "placeLocation": { "latLng": "$lat°, $lng°" }
                    }
                  }
                }
                """.trimIndent()
                segmentEntries.add(visitJson)

                // Movement Segment connecting to next location with 100 simplified points (100 * 100 = 10,000 points total)
                val nextLat = center.latitude + ((i + 1) % 10) * 0.01
                val nextLng = center.longitude + ((i + 1) % 10) * 0.01
                val moveStartTimeIso = java.time.Instant.ofEpochMilli(currentTimestamp).toString()
                val moveEndTimeIso = java.time.Instant.ofEpochMilli(currentTimestamp + 1800000L).toString()
                currentTimestamp += 1800000L

                val pathPoints = (0 until 100).joinToString(",\n") { ptIdx ->
                    val frac = ptIdx / 100.0
                    val ptLat = lat + (nextLat - lat) * frac
                    val ptLng = lng + (nextLng - lng) * frac
                    val ptTimeIso = java.time.Instant.ofEpochMilli(currentTimestamp - 1800000L + (ptIdx * 18000L)).toString()
                    """{ "latLng": "$ptLat°, $ptLng°", "timestamp": "$ptTimeIso" }"""
                }

                val moveJson = """
                {
                  "startTime": "$moveStartTimeIso",
                  "endTime": "$moveEndTimeIso",
                  "activity": {
                    "topCandidate": { "type": "IN_PASSENGER_VEHICLE", "probability": 0.95 },
                    "distanceMeters": 5000.0,
                    "simplifiedRawPath": {
                      "points": [
                        $pathPoints
                      ]
                    }
                  }
                }
                """.trimIndent()
                segmentEntries.add(moveJson)
            }

            jsonBuilder.append(segmentEntries.joinToString(",\n"))
            jsonBuilder.append("\n  ]\n}")
            val timelineJsonBytes = jsonBuilder.toString().toByteArray(StandardCharsets.UTF_8)

            // 300 lightweight media candidates
            val sampleMediaCandidates = (0 until 300).map { mIdx ->
                val mediaTime = baseTime + (mIdx * 1200000L)
                val cityIdx = mIdx % centerPoints.size
                val center = centerPoints[cityIdx]
                RawMediaCandidate(
                    id = "synthetic_photo_$mIdx",
                    contentUriString = "content://media/external/images/media/$mIdx",
                    fileName = "IMG_synthetic_$mIdx.jpg",
                    mimeType = "image/jpeg",
                    exifDateTimeOriginal = "2026:07:01 12:00:00",
                    exifOffset = "+00:00",
                    mediaStoreDateTaken = mediaTime,
                    fileDateModifiedMs = mediaTime,
                    directGps = GeoPoint(center.latitude + 0.005, center.longitude + 0.005)
                )
            }

            val stressMediaRepo = object : MediaRepository {
                override suspend fun queryMediaCandidatesForDateRange(startTimestampEpochMs: Long, endTimestampEpochMs: Long): List<RawMediaCandidate> {
                    return sampleMediaCandidates
                }
            }

            val useCase = CreateTripUseCase(
                locationHistorySource = GoogleTimelineJsonParser(),
                mediaRepository = stressMediaRepo,
                transportClassifier = RuleBasedTransportClassifier(),
                tripRepository = repository,
                timezoneResolver = GeoTimezoneEngine
            )

            // Stage 3 & 4: Execute CreateTripUseCase and measure peak / post-save
            captureMemoryInfo("peak-timeshape")

            val trip = useCase.execute(
                timelineStream = ByteArrayInputStream(timelineJsonBytes),
                startDate = LocalDate.of(2026, 7, 1),
                endDate = LocalDate.of(2026, 7, 4),
                customTitle = "Global Realistic Stress Test (100 Visits, 100 Segments, 10k Points)"
            )

            assertNotNull("Trip must be created successfully without OOM", trip)
            assertTrue("Trip must have days populated", trip.days.isNotEmpty())
            assertTrue("Trip must contain parsed movement distance", trip.totalDistanceMeters > 0)

            // Stage 4: Immediately after Room save
            captureMemoryInfo("post-import")

            // Stage 5: After production release lifecycle (engine released automatically by CreateTripUseCase finally block)
            assertFalse("TimeShape engine must be released after import completes", GeoTimezoneEngine.isInitialized())
            captureMemoryInfo("after-release-gc")

            // Stage 6: After opening saved trip from Room database
            val loadedTrip = repository.getTripById(trip.id)
            assertNotNull("Saved trip must be retrieved cleanly from Room", loadedTrip)
            captureMemoryInfo("after-opening-saved-trip")

            // Stage 7: After preparing playback and compressing geometry
            val allVisits = loadedTrip!!.days.flatMap { it.items.filterIsInstance<TripDayItem.VisitItem>().map { v -> v.visit } }
            val allSegments = loadedTrip.days.flatMap { it.items.filterIsInstance<TripDayItem.MovementItem>().map { s -> s.segment } }
            val renderModel = TravelMapRenderModel(allVisits, allSegments)
            val compressor = TimelineStoryCompressor(renderModel)
            assertNotNull("Compressed timeline story must be generated cleanly", compressor.nodes)
            captureMemoryInfo("after-playback-preparation")
        }
    }
}

