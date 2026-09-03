package com.traveler.feature.map.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class GlobalMapRenderingAndroidTest {

    private fun saveAndVerifyBitmap(bitmap: Bitmap, filename: String): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val persistentDirs = listOf(
            File("/data/local/tmp"),
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
                FileOutputStream(f).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (f.exists() && f.length() > 0) {
                    if (savedFile == null) savedFile = f
                }
            } catch (_: Exception) {}
        }

        val file = savedFile ?: File(context.filesDir, filename)
        assertTrue("Generated image must exist on device", file.exists())
        assertTrue("Generated image must have non-trivial size (>10KB)", file.length() > 10240)

        // Pixel sanity check: ensure canvas is not completely blank / single-color
        val width = bitmap.width
        val height = bitmap.height
        val firstPixel = bitmap.getPixel(0, 0)
        var nonBackgroundCount = 0
        val sampleStep = 4

        for (x in 0 until width step sampleStep) {
            for (y in 0 until height step sampleStep) {
                if (bitmap.getPixel(x, y) != firstPixel) {
                    nonBackgroundCount++
                }
            }
        }

        assertTrue(
            "Rendered map must contain visible features (continents, paths, markers)",
            nonBackgroundCount > 100
        )

        // Compute SHA-256 hash of file
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
        return hash
    }

    @Test
    fun testRenderAndCaptureGlobalMapEvidenceOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val basemapStream = context.assets.open("basemap_world.json")
        val renderer = TravelMapRenderer(basemapStream)

        val nyCoord = GeoPoint(40.7128, -74.0060)
        val seoulCoord = GeoPoint(37.5665, 126.9780)
        val tokyoCoord = GeoPoint(35.6762, 139.6503)
        val sfCoord = GeoPoint(37.7749, -122.4194)

        val width = 1080
        val height = 720

        // 1. New York -> Seoul static overview
        val nySeoulFlight = MovementSegment(
            id = "flight_ny_seoul",
            startTimestampEpochMs = 1782800000000L,
            endTimestampEpochMs = 1782850000000L,
            startPoint = nyCoord,
            endPoint = seoulCoord,
            simplifiedPoints = listOf(nyCoord, seoulCoord),
            distanceMeters = 11000000.0,
            durationMillis = 50000000L,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Flight"),
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC,
            startTimezoneId = "America/New_York",
            endTimezoneId = "Asia/Seoul"
        )
        val nySeoulModel = TravelMapRenderModel(
            visits = listOf(
                Visit("v_ny", "New York", null, null, nyCoord, 1782790000000L, 1782800000000L, 0.95f, "America/New_York"),
                Visit("v_seoul", "Seoul", null, null, seoulCoord, 1782850000000L, 1782860000000L, 0.95f, "Asia/Seoul")
            ),
            segments = listOf(nySeoulFlight)
        )

        val bmpOverview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderer.render(Canvas(bmpOverview), width, height, nySeoulModel, playbackState = null)
        val hashOverview = saveAndVerifyBitmap(bmpOverview, "ny_seoul_overview.png")

        // 2. New York -> Seoul mid-flight playback
        val compressor = TimelineStoryCompressor(nySeoulModel, targetStoryDurationSeconds = 20.0f)
        val midFlightPlayback = compressor.evaluate(0.50f)
        val bmpFlight = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderer.render(Canvas(bmpFlight), width, height, nySeoulModel, playbackState = midFlightPlayback)
        val hashFlight = saveAndVerifyBitmap(bmpFlight, "ny_seoul_flight.png")

        // 3. Tokyo -> San Francisco antimeridian overview
        val transpacificFlight = MovementSegment(
            id = "flight_tokyo_sf",
            startTimestampEpochMs = 1782800000000L,
            endTimestampEpochMs = 1782840000000L,
            startPoint = tokyoCoord,
            endPoint = sfCoord,
            simplifiedPoints = listOf(tokyoCoord, sfCoord),
            distanceMeters = 8280000.0,
            durationMillis = 40000000L,
            transport = TransportPrediction(TransportMode.AIRPLANE, 0.99f, "Transpacific Flight"),
            geometryProvenance = GeometryProvenance.ESTIMATED_GEODESIC,
            startTimezoneId = "Asia/Tokyo",
            endTimezoneId = "America/Los_Angeles"
        )
        val tokyoSfModel = TravelMapRenderModel(
            visits = listOf(
                Visit("v_tokyo", "Tokyo Haneda", null, null, tokyoCoord, 1782790000000L, 1782800000000L, 0.95f, "Asia/Tokyo"),
                Visit("v_sf", "San Francisco SFO", null, null, sfCoord, 1782840000000L, 1782850000000L, 0.95f, "America/Los_Angeles")
            ),
            segments = listOf(transpacificFlight)
        )

        val bmpTokyoSf = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderer.render(Canvas(bmpTokyoSf), width, height, tokyoSfModel, playbackState = null)
        val hashTokyoSf = saveAndVerifyBitmap(bmpTokyoSf, "tokyo_sf_antimeridian.png")

        // 4. Princeton / Northeast Regional Basemap (P1-14)
        val princetonCoord = GeoPoint(40.3573, -74.6672)
        val phillyCoord = GeoPoint(39.9526, -75.1652)
        val princetonDrive = MovementSegment(
            id = "drive_ny_princeton_philly",
            startTimestampEpochMs = 1782800000000L,
            endTimestampEpochMs = 1782807200000L,
            startPoint = nyCoord,
            endPoint = phillyCoord,
            simplifiedPoints = listOf(nyCoord, princetonCoord, phillyCoord),
            distanceMeters = 150000.0,
            durationMillis = 7200000L,
            transport = TransportPrediction(TransportMode.CAR, 0.95f, "Road Trip"),
            geometryProvenance = GeometryProvenance.OBSERVED,
            startTimezoneId = "America/New_York",
            endTimezoneId = "America/New_York"
        )
        val princetonModel = TravelMapRenderModel(
            visits = listOf(
                Visit("v_ny", "New York City", null, null, nyCoord, 1782790000000L, 1782800000000L, 0.95f, "America/New_York"),
                Visit("v_princeton", "Princeton University", null, null, princetonCoord, 1782803600000L, 1782805400000L, 0.95f, "America/New_York")
            ),
            segments = listOf(princetonDrive)
        )

        val bmpPrinceton = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderer.render(Canvas(bmpPrinceton), width, height, princetonModel, playbackState = null)
        val hashPrinceton = saveAndVerifyBitmap(bmpPrinceton, "princeton_regional_basemap.png")

        // 5. NYC Ferry Regional Basemap (P1-12)
        val libertyDock = GeoPoint(40.6892, -74.0445)
        val harborChannelPoint = GeoPoint(40.6950, -74.0250)
        val batteryDock = GeoPoint(40.7020, -74.0150)
        val nycFerryMovement = MovementSegment(
            id = "ferry_liberty_battery",
            startTimestampEpochMs = 1786977205000L,
            endTimestampEpochMs = 1786979759000L,
            startPoint = libertyDock,
            endPoint = batteryDock,
            simplifiedPoints = listOf(libertyDock, harborChannelPoint, batteryDock),
            distanceMeters = 3800.0,
            durationMillis = 2554000L,
            transport = TransportPrediction(TransportMode.FERRY, 0.98f, "Statue Cruises Ferry"),
            geometryProvenance = GeometryProvenance.OBSERVED,
            startTimezoneId = "America/New_York",
            endTimezoneId = "America/New_York"
        )
        val nycFerryModel = TravelMapRenderModel(
            visits = listOf(
                Visit("v_liberty", "Liberty Island", null, null, libertyDock, 1786970000000L, 1786977205000L, 0.95f, "America/New_York"),
                Visit("v_battery", "The Battery", null, null, batteryDock, 1786979759000L, 1786985000000L, 0.95f, "America/New_York")
            ),
            segments = listOf(nycFerryMovement)
        )

        val bmpNycFerry = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderer.render(Canvas(bmpNycFerry), width, height, nycFerryModel, playbackState = null)
        val hashNycFerry = saveAndVerifyBitmap(bmpNycFerry, "nyc_ferry_regional_basemap.png")

        // Assert all generated screenshots have DISTINCT hashes
        assertNotEquals("Overview and flight playback must have distinct hashes", hashOverview, hashFlight)
        assertNotEquals("Overview and Tokyo-SF must have distinct hashes", hashOverview, hashTokyoSf)
        assertNotEquals("Overview and Princeton regional must have distinct hashes", hashOverview, hashPrinceton)
        assertNotEquals("Flight playback and Tokyo-SF must have distinct hashes", hashFlight, hashTokyoSf)
        assertNotEquals("Tokyo-SF and Princeton regional must have distinct hashes", hashTokyoSf, hashPrinceton)
        assertNotEquals("NYC Ferry and Princeton regional must have distinct hashes", hashNycFerry, hashPrinceton)
    }
}
