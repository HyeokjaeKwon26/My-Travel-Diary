package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.WebMercator
import kotlin.math.*

data class SafeContentInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
)

data class ViewportReference(
    val referenceLng: Double,
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double
)

class TravelViewportCalculator(
    val width: Int,
    val height: Int,
    val insets: SafeContentInsets = SafeContentInsets(),
    val referenceLng: Double,
    val viewportMinX: Double,
    val viewportMinY: Double,
    val spanX: Double,
    val spanY: Double
) {
    val contentW: Float = maxOf(1f, width - insets.left - insets.right)
    val contentH: Float = maxOf(1f, height - insets.top - insets.bottom)

    constructor(
        width: Int,
        height: Int,
        insets: SafeContentInsets = SafeContentInsets(),
        allPoints: List<GeoPoint>,
        playbackCameraCenter: GeoPoint? = null,
        playbackCameraSpanDegrees: Double? = null
    ) : this(
        width = width,
        height = height,
        insets = insets,
        ref = computeReference(allPoints),
        playbackCameraCenter = playbackCameraCenter,
        playbackCameraSpanDegrees = playbackCameraSpanDegrees
    )

    constructor(
        width: Int,
        height: Int,
        insets: SafeContentInsets = SafeContentInsets(),
        ref: ViewportReference,
        playbackCameraCenter: GeoPoint? = null,
        playbackCameraSpanDegrees: Double? = null
    ) : this(
        width = width,
        height = height,
        insets = insets,
        referenceLng = ref.referenceLng,
        viewportMinX = calculateViewportMinX(width, height, insets, ref, playbackCameraCenter, playbackCameraSpanDegrees).first,
        viewportMinY = calculateViewportMinX(width, height, insets, ref, playbackCameraCenter, playbackCameraSpanDegrees).second,
        spanX = calculateViewportMinX(width, height, insets, ref, playbackCameraCenter, playbackCameraSpanDegrees).third,
        spanY = calculateViewportMinX(width, height, insets, ref, playbackCameraCenter, playbackCameraSpanDegrees).fourth
    )

    companion object {
        fun computeReference(allPoints: List<GeoPoint>): ViewportReference {
            val points = if (allPoints.isEmpty()) listOf(GeoPoint(42.3503, -71.0810)) else allPoints

            // 1. Antimeridian circular longitude optimization: find the largest gap around the circle [0, 360)
            val normalizedLngs = points.map { ((it.longitude % 360.0 + 360.0) % 360.0) }.distinct().sorted()

            var maxGap = 0.0
            var bestRefLng = if (points.isNotEmpty()) points[0].longitude else 0.0

            if (normalizedLngs.size > 1) {
                for (i in normalizedLngs.indices) {
                    val current = normalizedLngs[i]
                    val next = if (i == normalizedLngs.size - 1) normalizedLngs[0] + 360.0 else normalizedLngs[i + 1]
                    val gap = next - current
                    if (gap > maxGap) {
                        maxGap = gap
                        val refNorm = (next % 360.0)
                        bestRefLng = if (refNorm > 180.0) refNorm - 360.0 else refNorm
                    }
                }
            }

            var minX = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE

            for (pt in points) {
                val unwrappedLng = WebMercator.unwrapLongitude(bestRefLng, pt.longitude)
                val wx = (unwrappedLng + 180.0) / 360.0
                val wy = WebMercator.project(pt.latitude, 0.0).y

                if (wx < minX) minX = wx
                if (wx > maxX) maxX = wx
                if (wy < minY) minY = wy
                if (wy > maxY) maxY = wy
            }

            return ViewportReference(bestRefLng, minX, maxX, minY, maxY)
        }

        private data class Quad(val first: Double, val second: Double, val third: Double, val fourth: Double)

        private fun calculateViewportMinX(
            width: Int,
            height: Int,
            insets: SafeContentInsets,
            ref: ViewportReference,
            playbackCameraCenter: GeoPoint?,
            playbackCameraSpanDegrees: Double?
        ): Quad {
            val contentW = maxOf(1f, width - insets.left - insets.right)
            val contentH = maxOf(1f, height - insets.top - insets.bottom)

            if (playbackCameraCenter != null && playbackCameraSpanDegrees != null) {
                val centerUnwrappedLng = WebMercator.unwrapLongitude(ref.referenceLng, playbackCameraCenter.longitude)
                val centerWx = (centerUnwrappedLng + 180.0) / 360.0
                val centerWy = WebMercator.project(playbackCameraCenter.latitude, 0.0).y

                val dynamicSpanY = maxOf(0.000001, playbackCameraSpanDegrees / 180.0)
                val canvasAspect = contentW / contentH
                val dynamicSpanX = dynamicSpanY * canvasAspect

                val spanX = dynamicSpanX
                val spanY = dynamicSpanY
                val viewportMinX = centerWx - spanX / 2.0
                val viewportMinY = centerWy - spanY / 2.0
                return Quad(viewportMinX, viewportMinY, spanX, spanY)
            } else {
                val rawSpanX = maxOf(0.00002, ref.maxX - ref.minX)
                val rawSpanY = maxOf(0.00002, ref.maxY - ref.minY)

                val centerX = (ref.minX + ref.maxX) / 2.0
                val centerY = (ref.minY + ref.maxY) / 2.0

                // Apply 25% margin padding
                var calcSpanX = rawSpanX * 1.25
                var calcSpanY = rawSpanY * 1.25

                val canvasAspect = (contentW / contentH).toDouble()
                val currentAspect = calcSpanX / calcSpanY

                if (currentAspect < canvasAspect) {
                    calcSpanX = calcSpanY * canvasAspect
                } else {
                    calcSpanY = calcSpanX / canvasAspect
                }

                val spanX = calcSpanX
                val spanY = calcSpanY
                val viewportMinX = centerX - spanX / 2.0
                val viewportMinY = centerY - spanY / 2.0
                return Quad(viewportMinX, viewportMinY, spanX, spanY)
            }
        }
    }

    /**
     * Converts a geographic coordinate (lat, lng) to Canvas screen pixel coordinates.
     * Writes result to the provided 2-element FloatArray [px, py].
     */
    fun toScreen(lat: Double, lng: Double, out: FloatArray) {
        val unwrappedLng = WebMercator.unwrapLongitude(referenceLng, lng)
        toScreenUnwrapped(lat, unwrappedLng, out)
    }

    /**
     * Converts an already continuously unwrapped longitude [unwrappedLng] and [lat] to screen pixel coordinates (P0-06).
     */
    fun toScreenUnwrapped(lat: Double, unwrappedLng: Double, out: FloatArray) {
        val wx = (unwrappedLng + 180.0) / 360.0
        val wy = WebMercator.project(lat, 0.0).y

        out[0] = insets.left + (((wx - viewportMinX) / spanX) * contentW).toFloat()
        out[1] = insets.top + (((wy - viewportMinY) / spanY) * contentH).toFloat()
    }

    /**
     * Continuously unwraps a sequence of coordinates (lat, lng) to eliminate artificial 360-degree wrap cuts (P0-06).
     * 1. The first vertex is unwrapped relative to [referenceLng].
     * 2. Every subsequent vertex is unwrapped relative to the PREVIOUS unwrapped vertex.
     * 3. Shifting: Shifts the entire continuous sequence by multiples of 360 degrees so that the feature's
     *    average longitude is closest to the viewport reference longitude.
     */
    fun unwrapContinuousSequence(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (points.isEmpty()) return emptyList()

        val unwrapped = ArrayList<Pair<Double, Double>>(points.size)
        var prevLng = WebMercator.unwrapLongitude(referenceLng, points[0].second)
        unwrapped.add(Pair(points[0].first, prevLng))

        for (i in 1 until points.size) {
            val lat = points[i].first
            val rawLng = points[i].second
            val contLng = WebMercator.unwrapLongitude(prevLng, rawLng)
            unwrapped.add(Pair(lat, contLng))
            prevLng = contLng
        }

        val avgLng = unwrapped.map { it.second }.average()
        val shift = kotlin.math.round((referenceLng - avgLng) / 360.0) * 360.0
        if (shift != 0.0) {
            return unwrapped.map { Pair(it.first, it.second + shift) }
        }
        return unwrapped
    }
}
