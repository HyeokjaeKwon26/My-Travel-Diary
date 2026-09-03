package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * Targeted performance benchmark verifying O(1) viewport culling and
 * pre-projected basemap throughput for Pass 21.5a.
 */
class RegionalBasemapPerformanceTest {

    private fun getRegionalBasemapFile(): File {
        val candidates = listOf(
            File("src/main/assets/basemap_regional.json"),
            File("app/src/main/assets/basemap_regional.json"),
            File("c:/Research/Traveler/app/src/main/assets/basemap_regional.json")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("basemap_regional.json not found in test search paths")
    }

    @Test
    fun testRegionalBasemapCullingAndThroughput() {
        val file = getRegionalBasemapFile()
        assertTrue("Regional basemap file must exist", file.exists())

        // 1. Parse into pre-projected structures
        val parseStart = System.currentTimeMillis()
        val prepared = FileInputStream(file).use { BasemapParser.parse(it) }
        val parseDuration = System.currentTimeMillis() - parseStart
        println("Parse & Pre-projection Duration: " + parseDuration + "ms for " + file.length() + " bytes")

        val totalPolygons = prepared.polygons.size
        val totalBoundaries = prepared.boundaries.size
        val totalPoints = prepared.totalPoints
        val totalFeats = totalPolygons + totalBoundaries
        println("Regional features total: $totalFeats ($totalPolygons polygons, $totalBoundaries boundaries)")
        println("Regional points total: $totalPoints")

        assertTrue("Must have loaded regional polygons", totalPolygons > 500)
        assertTrue("Must have loaded regional boundaries", totalBoundaries > 50)
        assertTrue("Must have ~300k+ total points", totalPoints > 200_000)

        // 2. Simulate Niagara Playback Viewport (lat 43.0858, lng -79.0627, span 0.08 deg)
        val niagaraCenter = GeoPoint(43.0858, -79.0627)
        val viewport = TravelViewportCalculator(
            width = 1080,
            height = 1920,
            allPoints = listOf(niagaraCenter),
            playbackCameraCenter = niagaraCenter,
            playbackCameraSpanDegrees = 0.08
        )

        val vpMinX = viewport.viewportMinX
        val vpMaxX = viewport.viewportMinX + viewport.spanX
        val vpMinY = viewport.viewportMinY
        val vpMaxY = viewport.viewportMinY + viewport.spanY

        // 3. Count intersecting features and points
        var intersectingPolys = 0
        var intersectingRings = 0
        var candidatePointsAfter = 0

        for (poly in prepared.polygons) {
            if (!poly.intersectsViewport(vpMinX, vpMaxX, vpMinY, vpMaxY)) continue
            intersectingPolys++
            for (ring in poly.rings) {
                if (ring.intersectsViewport(vpMinX, vpMaxX, vpMinY, vpMaxY)) {
                    intersectingRings++
                    candidatePointsAfter += ring.pointCount
                }
            }
        }

        var intersectingBoundaries = 0
        for (boundary in prepared.boundaries) {
            if (boundary.intersectsViewport(vpMinX, vpMaxX, vpMinY, vpMaxY)) {
                intersectingBoundaries++
                candidatePointsAfter += boundary.pointCount
            }
        }

        val totalIntersectingFeatures = intersectingPolys + intersectingBoundaries

        println("\n=== NIAGARA VIEWPORT PERFORMANCE ===")
        println("Features intersecting current viewport: $totalIntersectingFeatures")
        println("  (Polygons: $intersectingPolys, Rings: $intersectingRings, Boundaries: $intersectingBoundaries)")
        println("Per-frame candidate points BEFORE: $totalPoints")
        println("Per-frame candidate points AFTER culling: $candidatePointsAfter")
        val cullingEfficiency = (1.0 - (candidatePointsAfter.toDouble() / totalPoints.toDouble())) * 100.0
        println("Culling efficiency: ${String.format("%.2f", cullingEfficiency)}%")

        // Verification assertions
        assertTrue(
            "Culling must reject >80% of total points (culled $cullingEfficiency%)",
            cullingEfficiency >= 80.0
        )
        assertTrue(
            "Features intersecting must be < 50 (was $totalIntersectingFeatures)",
            totalIntersectingFeatures < 50
        )
        assertTrue(
            "Candidate points after culling must be < 60,000 (was $candidatePointsAfter)",
            candidatePointsAfter < 60_000
        )

        // 4. Benchmark 100 simulated frame renders
        val scaleX = (viewport.contentW / viewport.spanX).toFloat()
        val scaleY = (viewport.contentH / viewport.spanY).toFloat()
        val vMinX = viewport.viewportMinX.toFloat()
        val vMinY = viewport.viewportMinY.toFloat()
        val insetsLeft = viewport.insets.left
        val insetsTop = viewport.insets.top

        val renderStart = System.nanoTime()
        val frames = 100
        var dummySum = 0f

        for (f in 0 until frames) {
            for (poly in prepared.polygons) {
                if (!poly.intersectsViewport(vpMinX, vpMaxX, vpMinY, vpMaxY)) continue
                for (ring in poly.rings) {
                    if (!ring.intersectsViewport(vpMinX, vpMaxX, vpMinY, vpMaxY)) continue
                    val coords = ring.worldCoords
                    val len = coords.size
                    var i = 0
                    while (i < len) {
                        val px = insetsLeft + (coords[i] - vMinX) * scaleX
                        val py = insetsTop + (coords[i + 1] - vMinY) * scaleY
                        dummySum += px + py
                        i += 2
                    }
                }
            }
            for (boundary in prepared.boundaries) {
                if (!boundary.intersectsViewport(vpMinX, vpMaxX, vpMinY, vpMaxY)) continue
                val coords = boundary.worldCoords
                val len = coords.size
                var i = 0
                while (i < len) {
                    val px = insetsLeft + (coords[i] - vMinX) * scaleX
                    val py = insetsTop + (coords[i + 1] - vMinY) * scaleY
                    dummySum += px + py
                    i += 2
                }
            }
        }
        val renderDurationNs = System.nanoTime() - renderStart
        val perFrameMs = (renderDurationNs / frames) / 1_000_000.0
        println("Simulated frame render time: ${String.format("%.3f", perFrameMs)} ms/frame (dummySum=$dummySum)")
        assertTrue("Per frame render time must be under 5.0ms (got $perFrameMs ms)", perFrameMs < 5.0)
    }
}
