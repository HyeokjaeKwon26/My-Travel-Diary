package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.TransportMode
import com.traveler.core.model.TransportPrediction
import com.traveler.core.model.Visit
import org.junit.Assert.*
import org.junit.Test

class TimelineStoryCompressorTest {

    @Test
    fun largeTripWith100Visits_strictlyAdheresToTargetStoryDuration() {
        val visits = mutableListOf<Visit>()
        val segments = mutableListOf<MovementSegment>()

        val baseTs = 1782800000000L
        for (i in 0 until 100) {
            val vStart = baseTs + i * 3600_000L
            val vEnd = vStart + 1800_000L
            visits.add(
                Visit(
                    id = "v_$i",
                    placeName = "Stop $i",
                    location = GeoPoint(42.0 + i * 0.01, -71.0 + i * 0.01),
                    startTimestampEpochMs = vStart,
                    endTimestampEpochMs = vEnd,
                    confidence = 0.9f
                )
            )

            if (i < 99) {
                val sStart = vEnd
                val sEnd = sStart + 1800_000L
                segments.add(
                    MovementSegment(
                        id = "s_$i",
                        startTimestampEpochMs = sStart,
                        endTimestampEpochMs = sEnd,
                        startPoint = GeoPoint(42.0 + i * 0.01, -71.0 + i * 0.01),
                        endPoint = GeoPoint(42.0 + (i + 1) * 0.01, -71.0 + (i + 1) * 0.01),
                        distanceMeters = 1500.0,
                        durationMillis = 1800_000L,
                        transport = TransportPrediction(TransportMode.WALK, 0.9f, "walk")
                    )
                )
            }
        }

        val renderModel = TravelMapRenderModel(visits = visits, segments = segments)
        val compressor = TimelineStoryCompressor(renderModel, targetStoryDurationSeconds = 30.0f)

        // P1-06: Verify duration strictly adheres to target 30s (+/-10% tolerance: 27s - 33s)
        assertTrue(
            "Large trip story duration must strictly adhere to target (expected 27-33s, actual: ${compressor.totalStoryDurationSeconds})",
            compressor.totalStoryDurationSeconds in 27.0f..33.0f
        )

        // Evaluate at various progress points using binary search
        val state0 = compressor.evaluate(0.0f)
        val stateMid = compressor.evaluate(0.5f)
        val stateEnd = compressor.evaluate(1.0f)

        assertNotNull(state0)
        assertNotNull(stateMid)
        assertNotNull(stateEnd)

        assertEquals(0.0f, state0.progress, 0.001f)
        assertEquals(0.5f, stateMid.progress, 0.001f)
        assertEquals(1.0f, stateEnd.progress, 0.001f)
    }

    @Test
    fun seedTestTripDuration_isProperlyCalculated() {
        val nyCoord = GeoPoint(40.7128, -74.0060)
        val seoulCoord = GeoPoint(37.5665, 126.9780)
        val baseEpochMs = 1782800000000L
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
            geometryProvenance = com.traveler.core.model.GeometryProvenance.ESTIMATED_GEODESIC
        )
        val model = TravelMapRenderModel(visits = listOf(sampleVisit1, sampleVisit2), segments = listOf(sampleFlight))
        val comp = TimelineStoryCompressor(model)
        assertEquals(3, comp.nodes.size)
        assertTrue(comp.totalStoryDurationSeconds > 10.0f)
    }

    @Test
    fun testPass203_caseA_erieNiagaraProgression_noRewindToErieAfterNiagara() {
        // CASE A: Erie -> Niagara progression.
        // Erie stop with photo at T1, Movement to Niagara at T2, Niagara stop with photo at T3.
        val baseTs = 1783153800000L
        val pErie = GeoPoint(42.1292, -80.0851)
        val pNiagara = GeoPoint(43.0962, -79.0377)

        val photoErie = com.traveler.core.model.MediaItem(
            id = "photo_erie",
            contentUriString = "content://media/erie.jpg",
            fileName = "erie.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = baseTs + 1800_000L,
            location = pErie,
            matchedVisitId = "v_erie"
        )

        val photoNiagara = com.traveler.core.model.MediaItem(
            id = "photo_niagara",
            contentUriString = "content://media/niagara.jpg",
            fileName = "niagara.jpg",
            mimeType = "image/jpeg",
            timestampEpochMs = baseTs + 9000_000L,
            location = pNiagara,
            matchedVisitId = "v_niagara"
        )

        val vErie = Visit(
            id = "v_erie",
            placeName = "Erie Stop",
            location = pErie,
            startTimestampEpochMs = baseTs,
            endTimestampEpochMs = baseTs + 3600_000L,
            confidence = 0.9f
        )

        val segErieToNiagara = MovementSegment(
            id = "seg_erie_niagara",
            startTimestampEpochMs = baseTs + 3600_000L,
            endTimestampEpochMs = baseTs + 7200_000L,
            startPoint = pErie,
            endPoint = pNiagara,
            simplifiedPoints = listOf(pErie, pNiagara),
            distanceMeters = 160000.0,
            durationMillis = 3600_000L,
            transport = TransportPrediction(TransportMode.CAR, 0.9f, "Drive")
        )

        val vNiagara = Visit(
            id = "v_niagara",
            placeName = "Niagara Falls",
            location = pNiagara,
            startTimestampEpochMs = baseTs + 7200_000L,
            endTimestampEpochMs = baseTs + 10800_000L,
            confidence = 0.95f
        )

        val renderModel = TravelMapRenderModel(
            visits = listOf(vErie, vNiagara),
            segments = listOf(segErieToNiagara),
            photos = listOf(photoErie, photoNiagara)
        )

        val compressor = TimelineStoryCompressor(renderModel)

        // 1. Monotonicity validation must pass
        assertTrue("Playback story timeline must be strictly monotonic", compressor.validateMonotonicity())

        // 2. Sample 100 points: verify that after reaching Niagara, route marker never rewinds to Erie
        var reachedNiagara = false
        var prevTimeMs = Long.MIN_VALUE

        for (step in 0..100) {
            val p = step / 100.0f
            val state = compressor.evaluate(p)

            assertTrue("Story time must be monotonic", state.storyTimeMs >= prevTimeMs)
            prevTimeMs = state.storyTimeMs

            if (state.currentVisit?.id == "v_niagara") {
                reachedNiagara = true
            }

            if (reachedNiagara) {
                // Must not jump back to Erie photo or Erie coordinates
                assertNotEquals("Erie photo must not appear after reaching Niagara", "photo_erie", state.activePhoto?.id)
                val distToErie = com.traveler.core.common.geo.GeodesicUtils.distanceMeters(state.currentPosition, pErie)
                assertTrue("Position must remain around Niagara/corridor after reaching Niagara (was ${distToErie / 1000}km from Erie)", distToErie > 50000.0)
            }
        }
    }

    @Test
    fun testPass203_caseB_syracuseProgression_strictlyMonotonicStoryTimeline() {
        // CASE B: Multi-stop journey around Syracuse with intermediate movements
        val baseTs = 1783153800000L
        val pNiagara = GeoPoint(43.0962, -79.0377)
        val pSyracuse = GeoPoint(43.0481, -76.1474)
        val pAlbany = GeoPoint(42.6526, -73.7562)

        val v1 = Visit(id = "v_niagara", placeName = "Niagara", location = pNiagara, startTimestampEpochMs = baseTs, endTimestampEpochMs = baseTs + 3600_000L, confidence = 0.9f)
        val s1 = MovementSegment(id = "s_n_s", startTimestampEpochMs = baseTs + 3600_000L, endTimestampEpochMs = baseTs + 7200_000L, startPoint = pNiagara, endPoint = pSyracuse, simplifiedPoints = listOf(pNiagara, pSyracuse), distanceMeters = 240000.0, durationMillis = 3600_000L, transport = TransportPrediction(TransportMode.CAR, 0.9f, "Drive"))
        val v2 = Visit(id = "v_syracuse", placeName = "Syracuse", location = pSyracuse, startTimestampEpochMs = baseTs + 7200_000L, endTimestampEpochMs = baseTs + 10800_000L, confidence = 0.9f)
        val s2 = MovementSegment(id = "s_s_a", startTimestampEpochMs = baseTs + 10800_000L, endTimestampEpochMs = baseTs + 14400_000L, startPoint = pSyracuse, endPoint = pAlbany, simplifiedPoints = listOf(pSyracuse, pAlbany), distanceMeters = 220000.0, durationMillis = 3600_000L, transport = TransportPrediction(TransportMode.CAR, 0.9f, "Drive"))
        val v3 = Visit(id = "v_albany", placeName = "Albany", location = pAlbany, startTimestampEpochMs = baseTs + 14400_000L, endTimestampEpochMs = baseTs + 18000_000L, confidence = 0.9f)

        val renderModel = TravelMapRenderModel(
            visits = listOf(v1, v2, v3),
            segments = listOf(s1, s2)
        )

        val compressor = TimelineStoryCompressor(renderModel)
        assertTrue("Syracuse progression story must be strictly monotonic", compressor.validateMonotonicity())
    }

    @Test
    fun testPass203_caseC_multiPhotoVisitAnchor_markerRemainsStationaryDuringDwell() {
        // CASE C: Stop with multiple photos having off-centroid coordinates.
        // Route marker must remain strictly anchored at the Visit location.
        val baseTs = 1783153800000L
        val pVisit = GeoPoint(43.0962, -79.0377)
        val pPhoto1 = GeoPoint(43.0970, -79.0380) // 100m away
        val pPhoto2 = GeoPoint(43.0955, -79.0360) // 150m away

        val photo1 = com.traveler.core.model.MediaItem(id = "ph_1", contentUriString = "uri1", fileName = "ph1.jpg", mimeType = "image/jpeg", timestampEpochMs = baseTs + 600_000L, location = pPhoto1, matchedVisitId = "v_stop")
        val photo2 = com.traveler.core.model.MediaItem(id = "ph_2", contentUriString = "uri2", fileName = "ph2.jpg", mimeType = "image/jpeg", timestampEpochMs = baseTs + 1200_000L, location = pPhoto2, matchedVisitId = "v_stop")

        val visit = Visit(id = "v_stop", placeName = "Scenic Stop", location = pVisit, startTimestampEpochMs = baseTs, endTimestampEpochMs = baseTs + 3600_000L, confidence = 0.95f)

        val renderModel = TravelMapRenderModel(
            visits = listOf(visit),
            segments = emptyList(),
            photos = listOf(photo1, photo2)
        )

        val compressor = TimelineStoryCompressor(renderModel)

        // During the entire playback, currentPosition must remain pVisit
        for (step in 0..50) {
            val p = step / 50.0f
            val state = compressor.evaluate(p)
            assertEquals("Route marker must remain stationary at visit location", pVisit.latitude, state.currentPosition.latitude, 0.0001)
            assertEquals("Route marker must remain stationary at visit location", pVisit.longitude, state.currentPosition.longitude, 0.0001)
        }
    }
}
