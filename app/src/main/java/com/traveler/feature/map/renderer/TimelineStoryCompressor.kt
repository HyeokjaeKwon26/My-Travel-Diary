package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.common.geo.WebMercator
import com.traveler.core.model.MediaItem
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.TransportMode
import com.traveler.core.model.Visit
import com.traveler.core.timeline.CanonicalTimelineValidator
import com.traveler.core.timeline.CanonicalVisitTimelineValidator
import com.traveler.core.timeline.MovementTimelineCanonicalizer
import com.traveler.core.timeline.VisitCandidateCanonicalizer
import kotlin.math.*

data class MovementPhotoMoment(
    val photo: MediaItem,
    val progressFraction: Float
)

sealed interface StoryNode {
    val durationStorySeconds: Float
    val startTimestampEpochMs: Long
    val endTimestampEpochMs: Long

    data class VisitNode(
        val visit: Visit,
        val photos: List<MediaItem>,
        override val durationStorySeconds: Float = 3.0f,
        override val startTimestampEpochMs: Long = visit.startTimestampEpochMs,
        override val endTimestampEpochMs: Long = visit.endTimestampEpochMs
    ) : StoryNode {
        val heroPhoto: MediaItem? get() = photos.firstOrNull { it.isRepresentative } ?: photos.firstOrNull()
    }

    data class MovementNode(
        val segment: MovementSegment,
        val pathPoints: List<GeoPoint>,
        val cumulativeDistances: List<Double>,
        val totalDistanceMeters: Double,
        val headingTrack: PrecomputedHeadingTrack,
        val photoMoments: List<MovementPhotoMoment> = emptyList(),
        override val durationStorySeconds: Float,
        override val startTimestampEpochMs: Long = segment.startTimestampEpochMs,
        override val endTimestampEpochMs: Long = segment.endTimestampEpochMs
    ) : StoryNode
}

/**
 * Pure domain timeline story compressor that constructs a strictly monotonic,
 * temporally ordered, and continuous playback story timeline (P0-01 ~ P0-09).
 *
 * Invariants:
 * 1. Monotonicity: Story time and node order are strictly non-decreasing across playback progress.
 * 2. Passive Photos: Photo moments are passive overlays attached to playback time. They NEVER
 *    mutate the route marker coordinates, playback timestamp, or rewind playback position.
 * 3. Spatial Coherence: Route marker position is always derived from the currently active StoryNode.
 */
class TimelineStoryCompressor(
    renderModel: TravelMapRenderModel,
    targetStoryDurationSeconds: Float? = null
) {
    val nodes: List<StoryNode>
    val totalStoryDurationSeconds: Float
    private val nodeStartTimes: FloatArray

    init {
        // P1-11: Consume canonical segments and visits directly without overlap violations
        val canonicalSegments = CanonicalTimelineValidator.requireNonOverlapping(
            if (CanonicalTimelineValidator.countOverlapViolations(renderModel.segments) == 0) {
                renderModel.segments
            } else {
                MovementTimelineCanonicalizer.canonicalize(renderModel.segments).canonicalSegments
            }
        ).sortedBy { it.startTimestampEpochMs }

        val canonicalVisits = CanonicalVisitTimelineValidator.requireNonOverlapping(
            if (CanonicalVisitTimelineValidator.countOverlapViolations(renderModel.visits) == 0) {
                renderModel.visits
            } else {
                VisitCandidateCanonicalizer.deduplicate(renderModel.visits)
            }
        ).sortedBy { it.startTimestampEpochMs }
        val sortedVisits = canonicalVisits

        val chronologicalItems = ArrayList<Pair<Long, Any>>()
        for (v in canonicalVisits) {
            chronologicalItems.add(v.startTimestampEpochMs to v)
        }
        for (s in canonicalSegments) {
            chronologicalItems.add(s.startTimestampEpochMs to s)
        }
        chronologicalItems.sortBy { it.first }

        val photosByVisit = renderModel.photos.filter { it.matchedVisitId != null }
            .groupBy { it.matchedVisitId!! }
            .mapValues { (_, list) -> list.sortedBy { it.timestampEpochMs ?: 0L } }

        val photosBySegment = renderModel.photos.filter { it.matchedSegmentId != null }
            .groupBy { it.matchedSegmentId!! }
            .mapValues { (_, list) -> list.sortedBy { it.timestampEpochMs ?: 0L } }

        val totalVisitsCount = sortedVisits.size
        val totalSegmentsCount = canonicalSegments.size
        val firstVisitId = sortedVisits.firstOrNull()?.id
        val lastVisitId = sortedVisits.lastOrNull()?.id

        val dayCount = if (sortedVisits.isNotEmpty() || canonicalSegments.isNotEmpty()) {
            val startMs = sortedVisits.firstOrNull()?.startTimestampEpochMs
                ?: canonicalSegments.firstOrNull()?.startTimestampEpochMs ?: 0L
            val endMs = sortedVisits.lastOrNull()?.endTimestampEpochMs
                ?: canonicalSegments.lastOrNull()?.endTimestampEpochMs ?: startMs
            maxOf(1, ((endMs - startMs) / 86400000L).toInt() + 1)
        } else 1

        val totalDistanceKm = canonicalSegments.sumOf { it.distanceMeters } / 1000.0
        val baseDayBudget = dayCount * 14.0f
        val visitBudget = minOf(40, totalVisitsCount) * 3.2f
        val movementBudget = minOf(40, totalSegmentsCount) * 3.8f
        val distanceBudget = (totalDistanceKm / 300.0).toFloat().coerceIn(0f, 40f)

        val dynamicTarget = if (dayCount >= 3) {
            (baseDayBudget * 0.45f + visitBudget * 0.35f + movementBudget * 0.30f + distanceBudget)
                .coerceIn(90.0f, 150.0f)
        } else {
            (baseDayBudget * 0.40f + visitBudget * 0.35f + movementBudget * 0.25f + distanceBudget)
                .coerceIn(30.0f, 90.0f)
        }
        val effectiveTarget = targetStoryDurationSeconds ?: dynamicTarget

        val maxKeyVisits = if (targetStoryDurationSeconds != null) {
            minOf(10, maxOf(4, (effectiveTarget / 3.0f).toInt()))
        } else {
            minOf(40, maxOf(6, (effectiveTarget / 3.0f).toInt()))
        }

        val maxKeySegments = if (targetStoryDurationSeconds != null) {
            minOf(12, maxOf(4, (effectiveTarget / 2.5f).toInt()))
        } else {
            minOf(45, maxOf(6, (effectiveTarget / 2.5f).toInt()))
        }

        val keyVisitIds = HashSet<String>()
        if (firstVisitId != null) keyVisitIds.add(firstVisitId)
        if (lastVisitId != null) keyVisitIds.add(lastVisitId)

        sortedVisits.forEach { v ->
            val vPhotos = photosByVisit[v.id] ?: emptyList()
            if (vPhotos.any { it.isRepresentative } || v.isUserOverride) {
                if (keyVisitIds.size < maxKeyVisits) keyVisitIds.add(v.id)
            }
        }

        sortedVisits.forEach { v ->
            val vPhotos = photosByVisit[v.id] ?: emptyList()
            if (vPhotos.isNotEmpty()) {
                if (keyVisitIds.size < maxKeyVisits) keyVisitIds.add(v.id)
            }
        }

        val sortedVisitsByDwell = sortedVisits.sortedByDescending { it.endTimestampEpochMs - it.startTimestampEpochMs }
        for (v in sortedVisitsByDwell) {
            if (keyVisitIds.size >= maxKeyVisits) break
            keyVisitIds.add(v.id)
        }

        val keySegmentIds = HashSet<String>()
        if (canonicalSegments.isNotEmpty()) {
            keySegmentIds.add(canonicalSegments.first().id)
            keySegmentIds.add(canonicalSegments.last().id)
        }

        val sortedSegmentsByImportance = canonicalSegments.sortedWith(
            compareByDescending<MovementSegment> { it.effectiveMode == TransportMode.AIRPLANE }
                .thenByDescending { it.isUserOverride }
                .thenByDescending { it.distanceMeters }
        )
        for (s in sortedSegmentsByImportance) {
            if (keySegmentIds.size >= maxKeySegments) break
            keySegmentIds.add(s.id)
        }

        val rawNodes = ArrayList<Pair<StoryNode, Float>>()

        for ((_, item) in chronologicalItems) {
            when (item) {
                is Visit -> {
                    if (totalVisitsCount <= 10 || item.id in keyVisitIds) {
                        val vPhotos = photosByVisit[item.id] ?: emptyList()
                        val hasPhotos = vPhotos.isNotEmpty()
                        val dwellMs = maxOf(0L, item.endTimestampEpochMs - item.startTimestampEpochMs)

                        val (baseDuration, minDuration) = when {
                            hasPhotos && vPhotos.size > 1 -> 3.5f to 2.0f
                            hasPhotos -> 3.0f to 1.5f
                            dwellMs >= 3600_000L -> 2.5f to 1.2f
                            item.isUserOverride -> 2.5f to 1.2f
                            else -> 1.5f to 0.8f
                        }

                        rawNodes.add(
                            StoryNode.VisitNode(
                                visit = item,
                                photos = vPhotos,
                                durationStorySeconds = baseDuration
                            ) to minDuration
                        )
                    }
                }
                is MovementSegment -> {
                    if (totalSegmentsCount <= 12 || item.id in keySegmentIds) {
                        val sPhotos = photosBySegment[item.id] ?: emptyList()
                        val path = if (item.effectiveMode == TransportMode.AIRPLANE && item.simplifiedPoints.size <= 2) {
                            WebMercator.generateGreatCirclePath(item.startPoint, item.endPoint, steps = 32)
                        } else if (item.simplifiedPoints.isNotEmpty()) {
                            item.simplifiedPoints
                        } else {
                            listOf(item.startPoint, item.endPoint)
                        }

                        val cumDist = ArrayList<Double>(path.size)
                        var runningDist = 0.0
                        cumDist.add(0.0)
                        for (i in 1 until path.size) {
                            runningDist += GeodesicUtils.distanceMeters(path[i - 1], path[i])
                            cumDist.add(runningDist)
                        }

                        val headingTrack = VehicleHeadingCalculator.buildPrecomputedHeadingTrack(
                            path = path,
                            cumulativeDistances = cumDist,
                            totalDistanceMeters = runningDist,
                            mode = item.effectiveMode
                        )

                        val segDurationMs = maxOf(1L, item.endTimestampEpochMs - item.startTimestampEpochMs)
                        val photoMoments = sPhotos.map { photo ->
                            val photoTs = photo.timestampEpochMs ?: item.startTimestampEpochMs
                            val frac = ((photoTs - item.startTimestampEpochMs).toDouble() / segDurationMs.toDouble()).toFloat().coerceIn(0.05f, 0.95f)
                            MovementPhotoMoment(photo, frac)
                        }

                        val (baseDuration, minDuration) = when (item.effectiveMode) {
                            TransportMode.AIRPLANE -> 5.5f to 3.5f
                            TransportMode.TRAIN, TransportMode.SUBWAY -> 4.0f to 2.5f
                            TransportMode.CAR, TransportMode.BUS, TransportMode.FERRY -> 3.5f to 2.0f
                            TransportMode.BICYCLE -> 2.8f to 1.8f
                            TransportMode.RUN, TransportMode.WALK -> 2.2f to 1.4f
                            else -> 2.2f to 1.4f
                        }

                        rawNodes.add(
                            StoryNode.MovementNode(
                                segment = item,
                                pathPoints = path,
                                cumulativeDistances = cumDist,
                                totalDistanceMeters = runningDist,
                                headingTrack = headingTrack,
                                photoMoments = photoMoments,
                                durationStorySeconds = baseDuration
                            ) to minDuration
                        )
                    }
                }
            }
        }

        val totalRaw = rawNodes.sumOf { it.first.durationStorySeconds.toDouble() }.toFloat()
        val rawScale = if (totalRaw > 0f) effectiveTarget / totalRaw else 1.0f

        val scaledNodes = rawNodes.map { (node, minDur) ->
            val scaledDur = maxOf(minDur * minOf(1.0f, rawScale), node.durationStorySeconds * rawScale)
            when (node) {
                is StoryNode.VisitNode -> node.copy(durationStorySeconds = scaledDur)
                is StoryNode.MovementNode -> node.copy(durationStorySeconds = scaledDur)
            }
        }

        // Enforce strictly non-decreasing story timestamps across consecutive nodes (P0-07)
        var runningEndMs = Long.MIN_VALUE
        val monotonicNodes = scaledNodes.map { node ->
            val effStart = if (runningEndMs == Long.MIN_VALUE) node.startTimestampEpochMs else maxOf(node.startTimestampEpochMs, runningEndMs)
            val effEnd = maxOf(effStart + 1000L, maxOf(node.endTimestampEpochMs, effStart))
            runningEndMs = effEnd
            when (node) {
                is StoryNode.VisitNode -> node.copy(startTimestampEpochMs = effStart, endTimestampEpochMs = effEnd)
                is StoryNode.MovementNode -> node.copy(startTimestampEpochMs = effStart, endTimestampEpochMs = effEnd)
            }
        }

        nodes = monotonicNodes
        val actualTotal = monotonicNodes.sumOf { it.durationStorySeconds.toDouble() }.toFloat()
        totalStoryDurationSeconds = actualTotal

        val starts = FloatArray(monotonicNodes.size)
        var acc = 0.0f
        for (i in monotonicNodes.indices) {
            starts[i] = acc
            acc += monotonicNodes[i].durationStorySeconds
        }
        nodeStartTimes = starts
    }

    /**
     * Evaluates playback state at progress in [0.0 .. 1.0] using O(log N) binary searches and smooth CameraTrack.
     */
    fun evaluate(progress: Float): TravelPlaybackState {
        val clampedProgress = progress.coerceIn(0.0f, 1.0f)
        val currentStoryTime = clampedProgress * totalStoryDurationSeconds

        if (nodes.isEmpty()) {
            val defaultPoint = GeoPoint(0.0, 0.0)
            return TravelPlaybackState(
                progress = clampedProgress,
                storyTimeMs = 0L,
                currentPosition = defaultPoint,
                currentTransportMode = TransportMode.UNKNOWN,
                currentHeadingDegrees = 0.0f,
                currentVisit = null,
                activePhoto = null,
                cameraCenter = defaultPoint,
                cameraSpanLat = 0.1,
                cameraSpanLng = 0.1
            )
        }

        var nodeIndex = nodeStartTimes.binarySearch(currentStoryTime)
        if (nodeIndex < 0) {
            nodeIndex = -(nodeIndex + 1) - 1
        }
        nodeIndex = nodeIndex.coerceIn(0, nodes.size - 1)

        val activeNode = nodes[nodeIndex]
        val nodeStart = nodeStartTimes[nodeIndex]
        val nodeProgress = if (activeNode.durationStorySeconds > 0f) {
            ((currentStoryTime - nodeStart) / activeNode.durationStorySeconds).coerceIn(0.0f, 1.0f)
        } else {
            1.0f
        }

        return when (activeNode) {
            is StoryNode.VisitNode -> {
                val loc = activeNode.visit.location
                val startTs = activeNode.visit.startTimestampEpochMs
                val endTs = activeNode.visit.endTimestampEpochMs
                val currentTs = (startTs + (endTs - startTs) * nodeProgress).toLong()

                val activePhoto = if (activeNode.photos.isNotEmpty() && nodeProgress in 0.10f..0.90f) {
                    if (activeNode.photos.size == 1) {
                        activeNode.heroPhoto
                    } else {
                        val photoProgress = ((nodeProgress - 0.10f) / 0.80f).coerceIn(0.0f, 0.999f)
                        val pIdx = (photoProgress * activeNode.photos.size).toInt().coerceIn(0, activeNode.photos.size - 1)
                        activeNode.photos[pIdx]
                    }
                } else null

                // Broadened geographic context for visits (span 0.08 deg)
                TravelPlaybackState(
                    progress = clampedProgress,
                    storyTimeMs = currentTs,
                    currentPosition = loc,
                    currentTransportMode = TransportMode.WALK,
                    currentHeadingDegrees = 0.0f,
                    currentVisit = activeNode.visit,
                    activePhoto = activePhoto,
                    cameraCenter = loc,
                    cameraSpanLat = 0.08,
                    cameraSpanLng = 0.08
                )
            }
            is StoryNode.MovementNode -> {
                val seg = activeNode.segment
                val path = activeNode.pathPoints
                val cumDist = activeNode.cumulativeDistances
                val totalDist = activeNode.totalDistanceMeters

                val targetDist = totalDist * nodeProgress
                val currentPos = VehicleHeadingCalculator.interpolatePointAtDistance(path, cumDist, targetDist)
                val heading = activeNode.headingTrack.evaluate(targetDist)

                val startTs = seg.startTimestampEpochMs
                val endTs = seg.endTimestampEpochMs
                val currentTs = (startTs + (endTs - startTs) * nodeProgress).toLong()

                val activePhoto = activeNode.photoMoments.firstOrNull { moment ->
                    abs(nodeProgress - moment.progressFraction) <= 0.08f
                }?.photo

                val isFlight = seg.effectiveMode == TransportMode.AIRPLANE
                val isFerry = seg.effectiveMode == TransportMode.FERRY

                val (camCenter, spanDegrees) = when {
                    isFlight -> {
                        val midPoint = GeodesicUtils.interpolate(seg.startPoint, seg.endPoint, 0.5)
                        val flightDistanceKm = seg.distanceMeters / 1000.0
                        val maxSpanDeg = maxOf(6.0, minOf(60.0, flightDistanceKm / 40.0))
                        val arcHeightFactor = sin(nodeProgress * PI)
                        val cameraCenter = GeodesicUtils.interpolate(currentPos, midPoint, arcHeightFactor * 0.35)
                        val dynamicSpan = 0.10 + maxSpanDeg * (0.20 + 0.80 * arcHeightFactor)
                        cameraCenter to dynamicSpan
                    }
                    isFerry -> {
                        // P1-03: Mode-sensitive widened camera context for Ferry travel across harbor/water
                        val lookaheadPos = VehicleHeadingCalculator.interpolatePointAtDistance(path, cumDist, minOf(totalDist, targetDist + 150.0))
                        lookaheadPos to 0.12
                    }
                    else -> {
                        val lookaheadMeters = VehicleHeadingCalculator.getLookaheadMeters(seg.effectiveMode)
                        val camLookaheadDist = (targetDist + lookaheadMeters * 0.6).coerceIn(0.0, totalDist)
                        val lookaheadPos = VehicleHeadingCalculator.interpolatePointAtDistance(path, cumDist, camLookaheadDist)

                        val speedKmh = if (seg.averageSpeedKmh > 0.0) seg.averageSpeedKmh else 40.0
                        val speedFactor = (speedKmh / 100.0).coerceIn(0.3, 2.2)

                        val localSpan = when (seg.effectiveMode) {
                            TransportMode.TRAIN, TransportMode.SUBWAY -> (0.80 * speedFactor).coerceIn(0.50, 2.5)
                            TransportMode.CAR, TransportMode.BUS -> (0.70 * speedFactor).coerceIn(0.35, 1.60)
                            TransportMode.BICYCLE -> 0.20
                            TransportMode.WALK, TransportMode.RUN -> 0.10
                            else -> 0.20
                        }
                        lookaheadPos to localSpan
                    }
                }

                TravelPlaybackState(
                    progress = clampedProgress,
                    storyTimeMs = currentTs,
                    currentPosition = currentPos,
                    currentTransportMode = seg.effectiveMode,
                    currentHeadingDegrees = heading,
                    currentVisit = null,
                    activePhoto = activePhoto,
                    cameraCenter = camCenter,
                    cameraSpanLat = spanDegrees,
                    cameraSpanLng = spanDegrees
                )
            }
        }
    }

    /**
     * Validates that the constructed story timeline is strictly monotonic across all progress steps (P0-02).
     */
    fun validateMonotonicity(sampleSteps: Int = 500): Boolean {
        var prevTimeMs = Long.MIN_VALUE
        for (i in 0..sampleSteps) {
            val p = i.toFloat() / sampleSteps.toFloat()
            val state = evaluate(p)
            if (state.storyTimeMs < prevTimeMs) {
                return false
            }
            prevTimeMs = state.storyTimeMs
        }
        return true
    }
}
