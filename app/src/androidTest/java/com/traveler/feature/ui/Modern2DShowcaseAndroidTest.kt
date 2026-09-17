package com.traveler.feature.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.traveler.MainActivity
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.database.TravelerDatabase
import com.traveler.core.model.*
import com.traveler.data.repository.TripRepositoryImpl
import com.traveler.feature.home.HomeScreen
import com.traveler.feature.home.HomeViewModel
import com.traveler.feature.home.TripSort
import com.traveler.feature.map.flat.CanyonDemo
import com.traveler.feature.trip.TravelDiaryScreen
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant

/** Real UI screenshots with clearly illustrative data, never user location history. */
class Modern2DShowcaseAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun capture(name: String) {
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        device.waitForIdle()
        Thread.sleep(500)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        File(context.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun showcaseSavedTripsAndFlatPlayback() {
        val start=Instant.parse("2026-09-12T14:00:00Z").toEpochMilli()
        val points=listOf(42.3601 to -71.0942,42.3552 to -71.0830,42.3530 to -71.0700,
            42.3528 to -71.0580,42.3565 to -71.0500,42.3625 to -71.0520,42.3670 to -71.0610,
            42.3660 to -71.0760,42.3601 to -71.0942).map { GeoPoint(it.first,it.second) }
        val distance=GeodesicUtils.pathDistanceMeters(points)
        val segment=MovementSegment("showcase-drive",start,start+3_600_000,points.first(),points.last(),
            simplifiedPoints=points,distanceMeters=distance,durationMillis=3_600_000,
            transport=TransportPrediction(TransportMode.CAR,1f,"Illustrative route"),
            startTimezoneId="America/New_York",endTimezoneId="America/New_York",
            geometryProvenance=GeometryProvenance.CONTINUITY_ESTIMATE)
        val home=Visit("showcase-home","Home · Cambridge",location=points.first(),
            startTimestampEpochMs=start-600_000,endTimestampEpochMs=start,timezoneId="America/New_York")
        val end=home.copy(id="showcase-return",startTimestampEpochMs=start+3_600_000,endTimestampEpochMs=start+4_200_000)
        val trip=Trip("modern-showcase-boston","Boston Harbor Weekend","2026-09-12","2026-09-12",distance,
            days=listOf(TripDay(1,"2026-09-12","America/New_York",listOf(
                TripDayItem.VisitItem(home),TripDayItem.MovementItem(segment),TripDayItem.VisitItem(end)),totalDistanceMeters=distance)))
        val repository=TripRepositoryImpl(TravelerDatabase.getDatabase(context))
        runBlocking {
            repository.saveTrip(trip)
            repository.saveTrip(CanyonDemo.trip().copy(id="modern-showcase-canyon",title="Canyon Road Trip"))
            repository.saveTrip(trip.copy(id="modern-showcase-coast",title="Coastal Escape"))
        }
        val prefs=context.getSharedPreferences("flat_map_settings",0)
        val prior=prefs.getBoolean("street_detail",false)
        prefs.edit().putBoolean("street_detail",true).commit()
        compose.mainClock.autoAdvance=false
        try {
            compose.activityRule.scenario.onActivity { activity ->
                val vm=HomeViewModel(activity.application);vm.setSort(TripSort.NAME,true)
                activity.setContent { MaterialTheme { HomeScreen(vm,{}, {}) } }
            }
            compose.waitUntil(30_000) { compose.mainClock.advanceTimeBy(32);compose.onAllNodesWithText(trip.title).fetchSemanticsNodes().isNotEmpty() }
            capture("home.png")
            compose.activityRule.scenario.onActivity { it.setContent { MaterialTheme { TravelDiaryScreen(trip.id,onNavigateBack={}) } } }
            compose.waitUntil(30_000) { compose.mainClock.advanceTimeBy(32);compose.onAllNodesWithContentDescription("Start Playback").fetchSemanticsNodes().isNotEmpty() }
            // Only the actual visible map is requested. No route prefetch or synthetic tile downloads.
            val until=System.currentTimeMillis()+35_000
            while(System.currentTimeMillis()<until) {
                compose.mainClock.advanceTimeBy(32);Thread.sleep(250)
            }
            compose.mainClock.advanceTimeBy(200)
            capture("diary.png")
            compose.onNodeWithContentDescription("Start Playback").performClick()
            compose.mainClock.advanceTimeBy(150)
            compose.onNodeWithContentDescription("Pause").performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("playback-seek").performSemanticsAction(SemanticsActions.SetProgress) { it(.48f) }
            compose.onNodeWithContentDescription("Fullscreen").performClick()
            compose.mainClock.advanceTimeBy(250)
            Thread.sleep(30_000)
            compose.mainClock.advanceTimeBy(200)
            compose.waitForIdle()
            capture("playback-portrait.png")
            compose.activityRule.scenario.onActivity { it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(15_000) { compose.mainClock.advanceTimeBy(32);context.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            compose.mainClock.advanceTimeBy(300)
            Thread.sleep(25_000)
            compose.mainClock.advanceTimeBy(200)
            compose.waitForIdle()
            capture("playback-landscape.png")
        } finally {
            prefs.edit().putBoolean("street_detail",prior).commit()
            compose.activityRule.scenario.onActivity { it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }
}
