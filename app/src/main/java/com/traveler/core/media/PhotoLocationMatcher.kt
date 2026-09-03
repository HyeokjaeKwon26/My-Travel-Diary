package com.traveler.core.media

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.model.*

object PhotoLocationMatcher {

    private const val MAX_VISIT_PROXIMITY_METERS = 1500.0 // 1.5 km

    /**
     * Matches a collection of MediaItems to Visits and MovementSegments from Timeline data.
     * Uses binary search for interval matching and windowed spatial search.
     */
    fun matchPhotos(
        photos: List<MediaItem>,
        visits: List<Visit>,
        segments: List<MovementSegment>,
        rawPoints: List<LocationPoint> = emptyList()
    ): List<MediaItem> {
        val sortedVisits = visits.sortedBy { it.startTimestampEpochMs }
        val sortedSegments = segments.sortedBy { it.startTimestampEpochMs }
        val sortedRawPoints = rawPoints.sortedBy { it.timestampEpochMs }

        return photos.map { photo ->
            matchSinglePhoto(photo, sortedVisits, sortedSegments, sortedRawPoints)
        }
    }

    private fun matchSinglePhoto(
        photo: MediaItem,
        sortedVisits: List<Visit>,
        sortedSegments: List<MovementSegment>,
        sortedRawPoints: List<LocationPoint>
    ): MediaItem {
        // If user manually overrode location, preserve user's choice
        if (photo.isUserLocationOverride) {
            return photo
        }

        // P0-03: If timestamp is unresolved, preserve exact EXIF GPS and GPS_EXACT confidence
        val photoTime = photo.timestampEpochMs
        if (photoTime == null) {
            return if (photo.location != null && photo.locationConfidence == LocationConfidenceLevel.GPS_EXACT) {
                photo.copy(
                    confidenceScore = 0.98f,
                    matchedVisitId = null,
                    matchedSegmentId = null
                )
            } else {
                photo.copy(
                    location = null,
                    locationConfidence = LocationConfidenceLevel.UNKNOWN,
                    confidenceScore = 0.0f,
                    matchedVisitId = null,
                    matchedSegmentId = null
                )
            }
        }

        // Case 1: Photo has exact EXIF GPS (P0-05, P0-03)
        if (photo.location != null && photo.locationConfidence == LocationConfidenceLevel.GPS_EXACT) {
            // 1. Active visit at photo timestamp AND spatial compatibility
            val activeVisit = findIntervalVisit(photoTime, sortedVisits)
            if (activeVisit != null && GeodesicUtils.distanceMeters(photo.location, activeVisit.location) <= MAX_VISIT_PROXIMITY_METERS) {
                return photo.copy(
                    confidenceScore = 0.98f,
                    matchedVisitId = activeVisit.id,
                    matchedSegmentId = null
                )
            }

            // 2. Active MovementSegment at photo timestamp AND route spatial compatibility
            val activeSegment = findIntervalSegment(photoTime, sortedSegments)
            if (activeSegment != null) {
                val dist = minDistanceToSegment(photo.location, activeSegment)
                val threshold = if (activeSegment.transport.mode == TransportMode.AIRPLANE) 50000.0 else 3000.0
                if (dist <= threshold) {
                    return photo.copy(
                        confidenceScore = 0.98f,
                        matchedVisitId = null,
                        matchedSegmentId = activeSegment.id
                    )
                }
            }

            // 3. Nearby Visit only within a STRICT temporal neighborhood (+- 2 hours)
            val windowVisits = getVisitsInWindow(sortedVisits, photoTime - 2 * 3600_000L, photoTime + 2 * 3600_000L)
            if (windowVisits.isNotEmpty()) {
                val nearbyVisit = windowVisits.minByOrNull { GeodesicUtils.distanceMeters(photo.location, it.location) }
                    ?.takeIf { GeodesicUtils.distanceMeters(photo.location, it.location) <= 300.0 }
                if (nearbyVisit != null) {
                    return photo.copy(
                        confidenceScore = 0.98f,
                        matchedVisitId = nearbyVisit.id,
                        matchedSegmentId = null
                    )
                }
            }

            // 4. Otherwise remain unassigned without attaching to unrelated historical visits
            return photo.copy(
                confidenceScore = 0.98f,
                matchedVisitId = null,
                matchedSegmentId = null
            )
        }

        // Case 2: Photo has NO GPS -> Match with Active Visit via O(log V) interval search
        val activeVisit = findIntervalVisit(photoTime, sortedVisits)
        if (activeVisit != null) {
            return photo.copy(
                location = activeVisit.location,
                locationConfidence = LocationConfidenceLevel.VISIT_INFERRED,
                confidenceScore = 0.92f,
                matchedVisitId = activeVisit.id
            )
        }

        // Case 3: Match with MovementSegment via O(log S) interval search
        val activeSegment = findIntervalSegment(photoTime, sortedSegments)
        if (activeSegment != null) {
            val interpolatedLocation = interpolateAlongSegment(photoTime, activeSegment)
            val isFlight = activeSegment.transport.mode == TransportMode.AIRPLANE
            val confidence = when {
                isFlight -> 0.70f
                activeSegment.durationMillis < 30 * 60_000L -> 0.85f
                else -> 0.60f
            }

            return photo.copy(
                location = interpolatedLocation,
                locationConfidence = LocationConfidenceLevel.TIMELINE_INTERPOLATED,
                confidenceScore = confidence,
                matchedSegmentId = activeSegment.id
            )
        }

        // Case 4: Binary search nearest raw timeline point within 30 minutes O(log P)
        if (sortedRawPoints.isNotEmpty()) {
            val idx = sortedRawPoints.binarySearchBy(photoTime) { it.timestampEpochMs }
            val insertionPoint = if (idx >= 0) idx else -(idx + 1)

            val candidateIndices = listOf(insertionPoint - 1, insertionPoint, insertionPoint + 1)
                .filter { it in sortedRawPoints.indices }

            val nearest = candidateIndices.map { sortedRawPoints[it] }
                .minByOrNull { kotlin.math.abs(it.timestampEpochMs - photoTime) }

            if (nearest != null) {
                val diffMinutes = kotlin.math.abs(nearest.timestampEpochMs - photoTime) / 60_000L
                if (diffMinutes <= 30L) {
                    val conf = (1.0f - (diffMinutes / 40.0f)).coerceIn(0.4f, 0.75f)
                    return photo.copy(
                        location = nearest.coordinate,
                        locationConfidence = LocationConfidenceLevel.TIMELINE_INTERPOLATED,
                        confidenceScore = conf
                    )
                }
            }
        }

        // Case 5: Unresolved location
        return photo.copy(
            location = null,
            locationConfidence = LocationConfidenceLevel.TIME_ONLY,
            confidenceScore = 0.1f
        )
    }

    private fun getVisitsInWindow(sortedVisits: List<Visit>, startMs: Long, endMs: Long): List<Visit> {
        if (sortedVisits.isEmpty()) return emptyList()
        val startIdx = sortedVisits.binarySearchBy(startMs) { it.startTimestampEpochMs }
        val fromIdx = if (startIdx >= 0) startIdx else (-(startIdx + 1) - 1).coerceAtLeast(0)
        val result = mutableListOf<Visit>()
        for (i in fromIdx until sortedVisits.size) {
            val v = sortedVisits[i]
            if (v.startTimestampEpochMs > endMs && v.endTimestampEpochMs > endMs) break
            if (v.endTimestampEpochMs >= startMs && v.startTimestampEpochMs <= endMs) {
                result.add(v)
            }
        }
        return result
    }

    private fun findIntervalVisit(photoTime: Long, sortedVisits: List<Visit>): Visit? {
        if (sortedVisits.isEmpty()) return null
        val idx = sortedVisits.binarySearchBy(photoTime) { it.startTimestampEpochMs }
        val candidateIdx = if (idx >= 0) idx else (-(idx + 1) - 1).coerceAtLeast(0)
        for (i in (candidateIdx - 1).coerceAtLeast(0)..(candidateIdx + 1).coerceAtMost(sortedVisits.size - 1)) {
            val v = sortedVisits[i]
            if (photoTime in v.startTimestampEpochMs..v.endTimestampEpochMs) {
                return v
            }
        }
        return null
    }

    private fun findIntervalSegment(photoTime: Long, sortedSegments: List<MovementSegment>): MovementSegment? {
        if (sortedSegments.isEmpty()) return null
        val idx = sortedSegments.binarySearchBy(photoTime) { it.startTimestampEpochMs }
        val candidateIdx = if (idx >= 0) idx else (-(idx + 1) - 1).coerceAtLeast(0)
        for (i in (candidateIdx - 1).coerceAtLeast(0)..(candidateIdx + 1).coerceAtMost(sortedSegments.size - 1)) {
            val s = sortedSegments[i]
            if (photoTime in s.startTimestampEpochMs..s.endTimestampEpochMs) {
                return s
            }
        }
        return null
    }

    private fun interpolateAlongSegment(photoTime: Long, segment: MovementSegment): GeoPoint {
        // P0-04 1: Use timestamped rawPoints when valid
        if (segment.rawPoints.size >= 2) {
            for (i in 0 until segment.rawPoints.size - 1) {
                val p1 = segment.rawPoints[i]
                val p2 = segment.rawPoints[i + 1]
                if (photoTime in p1.timestampEpochMs..p2.timestampEpochMs) {
                    val span = p2.timestampEpochMs - p1.timestampEpochMs
                    val fraction = if (span > 0) (photoTime - p1.timestampEpochMs).toDouble() / span else 0.0
                    return GeodesicUtils.interpolate(p1.coordinate, p2.coordinate, fraction.coerceIn(0.0, 1.0))
                }
            }
        }

        // P0-04 2: If rawPoints do not exist but simplifiedPoints contain route geometry:
        // interpolate by normalized temporal fraction along cumulative path distance
        val totalSpan = segment.endTimestampEpochMs - segment.startTimestampEpochMs
        val fraction = if (totalSpan > 0) (photoTime - segment.startTimestampEpochMs).toDouble() / totalSpan else 0.0
        val clampedFraction = fraction.coerceIn(0.0, 1.0)

        if (segment.simplifiedPoints.size >= 2) {
            return interpolateAlongPathByFraction(segment.simplifiedPoints, clampedFraction)
        }

        // P0-04 3: Endpoint fallback
        return GeodesicUtils.interpolate(segment.startPoint, segment.endPoint, clampedFraction)
    }

    private fun interpolateAlongPathByFraction(points: List<GeoPoint>, fraction: Double): GeoPoint {
        if (points.isEmpty()) return GeoPoint(0.0, 0.0)
        if (points.size == 1 || fraction <= 0.0) return points.first()
        if (fraction >= 1.0) return points.last()

        val segmentDistances = DoubleArray(points.size - 1)
        var totalDist = 0.0
        for (i in 0 until points.size - 1) {
            val d = GeodesicUtils.distanceMeters(points[i], points[i + 1])
            segmentDistances[i] = d
            totalDist += d
        }

        if (totalDist <= 0.0) return points.first()

        val targetDist = fraction * totalDist
        var accumulated = 0.0
        for (i in 0 until points.size - 1) {
            val nextAccum = accumulated + segmentDistances[i]
            if (targetDist <= nextAccum) {
                val segSpan = segmentDistances[i]
                val segFraction = if (segSpan > 0) (targetDist - accumulated) / segSpan else 0.0
                return GeodesicUtils.interpolate(points[i], points[i + 1], segFraction.coerceIn(0.0, 1.0))
            }
            accumulated = nextAccum
        }
        return points.last()
    }

    private fun minDistanceToSegment(pt: GeoPoint, segment: MovementSegment): Double {
        // P0-02A: Prefer highest-fidelity observed route coordinates
        val points = if (segment.rawPoints.isNotEmpty()) segment.rawPoints.map { it.coordinate }
        else if (segment.simplifiedPoints.isNotEmpty()) segment.simplifiedPoints
        else listOf(segment.startPoint, segment.endPoint)

        if (points.isEmpty()) return Double.MAX_VALUE
        if (points.size == 1) return GeodesicUtils.distanceMeters(pt, points[0])

        var minDist = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val d = distanceToLineSegmentMeters(pt, p1, p2)
            if (d < minDist) minDist = d
        }
        return minDist
    }

    private fun distanceToLineSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        // P0-02: Geodesic/local segment distance calculation
        return GeodesicUtils.distancePointToSegmentMeters(p, a, b)
    }
}
