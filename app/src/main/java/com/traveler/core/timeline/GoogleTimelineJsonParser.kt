package com.traveler.core.timeline

import com.traveler.core.common.geo.DouglasPeucker
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.common.time.TimeUtils
import com.traveler.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Robust, memory-efficient streaming parser for Google Maps Timeline exports.
 *
 * Implements Pass 20 Timeline Data Model Fix (P0-01 ~ P0-08, P1-11, P1-15, P1-16, P1-17):
 * - For modern semantic exports, `timelinePath` blocks are observational location-track containers.
 * - Semantic Activities provide the primary Movement episode definitions (timing, mode, probability, semantic distance).
 * - `timelinePath` observations are pooled across all observation chunks and fused directly into matching Semantic Activities.
 * - `timelinePath` points occurring during Visits represent stationary/jitter observations and never generate phantom movements.
 * - Fallback Movement creation occurs ONLY when real movement is genuinely uncovered by any Activity or Visit.
 * - Alternative Visit candidates sharing identical or near-identical time intervals are canonicalized by [VisitCandidateCanonicalizer].
 */
class GoogleTimelineJsonParser : LocationHistorySource {

    override val sourceName: String = "Google Maps Timeline JSON"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    data class StreamParseSummary(
        val totalDispatched: Int,
        val timelineElementsCount: Int,
        val chunkFailuresCount: Int,
        val wasTruncated: Boolean,
        val parseException: Exception? = null
    )

    private data class SemanticActivityCandidate(
        val startEpochMs: Long,
        val endEpochMs: Long,
        val startPoint: GeoPoint?,
        val endPoint: GeoPoint?,
        val transportMode: TransportMode,
        val confidence: Float,
        val rawActivityType: String?,
        val distanceMeters: Double,
        val simplifiedRawPoints: List<LocationPoint>,
        val segmentTimelinePathPoints: List<LocationPoint>
    )

    override suspend fun parse(inputStream: InputStream, filter: DateRangeFilter?): TimelineParseResult =
        withContext(Dispatchers.IO) {
            val rawVisits = mutableListOf<Visit>()
            val rawActivities = mutableListOf<SemanticActivityCandidate>()
            val allObservedPathPoints = mutableListOf<LocationPoint>()
            val legacySegments = mutableListOf<MovementSegment>()
            val rawPoints = mutableListOf<LocationPoint>()
            val warnings = mutableListOf<String>()

            var summary = StreamParseSummary(0, 0, 0, false, null)

            try {
                val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 65536)
                summary = streamParseObjects(reader, { elem ->
                    try {
                        val recognized = dispatchJsonObject(
                            elem = elem,
                            filter = filter,
                            rawVisits = rawVisits,
                            rawActivities = rawActivities,
                            allObservedPathPoints = allObservedPathPoints,
                            legacySegments = legacySegments,
                            rawPoints = rawPoints,
                            warnings = warnings
                        )
                        recognized
                    } catch (e: Exception) {
                        warnings.add("Skipped corrupted segment: ${e.message}")
                        false
                    }
                }, { chunkError ->
                    warnings.add("Malformed chunk in timeline stream: ${chunkError.message}")
                })
            } catch (e: Exception) {
                summary = summary.copy(parseException = e, wasTruncated = true)
                warnings.add("Error while parsing timeline JSON: ${e.message}")
            }

            // Step 1: Canonicalize and deduplicate alternative Visit candidates (P1-11)
            val canonicalVisits = VisitCandidateCanonicalizer.deduplicate(rawVisits)

            // Step 2: Sort observed path points chronologically
            val sortedObservedPoints = allObservedPathPoints
                .distinctBy { Pair(it.timestampEpochMs, it.coordinate) }
                .sortedBy { it.timestampEpochMs }

            // Step 3: Fuse timelinePath points into Semantic Activities (P0-02, P0-03, P0-04, P1-15, P1-16, P1-17)
            val assignedPointIds = HashSet<String>()
            val fusedMovementSegments = mutableListOf<MovementSegment>()
            val toleranceMs = 30_000L // 30-second boundary tolerance

            for (act in rawActivities) {
                val actStart = act.startEpochMs
                val actEnd = act.endEpochMs

                // Select observational timelinePath points matching this activity interval (spanning multiple chunks seamlessly)
                val matchingPoints = sortedObservedPoints.filter {
                    it.timestampEpochMs in (actStart - toleranceMs)..(actEnd + toleranceMs)
                }
                matchingPoints.forEach { assignedPointIds.add(it.id) }

                // Assemble geometry: startPoint @ actStart + matchingPoints + endPoint @ actEnd
                val segmentLocationPoints = mutableListOf<LocationPoint>()
                val candidateGeoPoints = mutableListOf<GeoPoint>()

                if (act.startPoint != null) {
                    val startLp = LocationPoint(
                        generateDeterministicId("pt_start", actStart, actStart, act.startPoint, null),
                        actStart,
                        act.startPoint
                    )
                    segmentLocationPoints.add(startLp)
                    candidateGeoPoints.add(act.startPoint)
                }

                if (act.simplifiedRawPoints.isNotEmpty()) {
                    segmentLocationPoints.addAll(act.simplifiedRawPoints)
                    candidateGeoPoints.addAll(act.simplifiedRawPoints.map { it.coordinate })
                } else if (matchingPoints.isNotEmpty()) {
                    segmentLocationPoints.addAll(matchingPoints)
                    candidateGeoPoints.addAll(matchingPoints.map { it.coordinate })
                } else if (act.segmentTimelinePathPoints.isNotEmpty()) {
                    segmentLocationPoints.addAll(act.segmentTimelinePathPoints)
                    candidateGeoPoints.addAll(act.segmentTimelinePathPoints.map { it.coordinate })
                }

                if (act.endPoint != null) {
                    val endLp = LocationPoint(
                        generateDeterministicId("pt_end", actEnd, actEnd, act.endPoint, null),
                        actEnd,
                        act.endPoint
                    )
                    segmentLocationPoints.add(endLp)
                    candidateGeoPoints.add(act.endPoint)
                }

                val dedupGeo = deduplicateConsecutivePoints(candidateGeoPoints)
                val startPt = dedupGeo.firstOrNull() ?: act.startPoint
                val endPt = dedupGeo.lastOrNull() ?: act.endPoint ?: startPt

                if (startPt != null && endPt != null && dedupGeo.isNotEmpty()) {
                    // P0-08 & P1-17: Prefer semantic activity distanceMeters when present and plausible; do not double count
                    val calculatedDist = if (act.distanceMeters > 0.0) {
                        act.distanceMeters
                    } else {
                        GeodesicUtils.pathDistanceMeters(dedupGeo)
                    }

                    val simplified = if (dedupGeo.size > 2) DouglasPeucker.simplify(dedupGeo, 20.0) else dedupGeo
                    val provenance = when {
                        act.simplifiedRawPoints.isNotEmpty() -> GeometryProvenance.SIMPLIFIED_OBSERVED
                        matchingPoints.isNotEmpty() || act.segmentTimelinePathPoints.isNotEmpty() -> GeometryProvenance.OBSERVED
                        act.transportMode == TransportMode.AIRPLANE -> GeometryProvenance.ESTIMATED_GEODESIC
                        else -> GeometryProvenance.ENDPOINT_INTERPOLATED
                    }

                    val sortedRawPts = segmentLocationPoints
                        .distinctBy { Pair(it.timestampEpochMs, it.coordinate) }
                        .sortedBy { it.timestampEpochMs }

                    val duration = maxOf(0L, actEnd - actStart)
                    val stableId = generateDeterministicId("act", actStart, actEnd, startPt, act.rawActivityType)

                    fusedMovementSegments.add(
                        MovementSegment(
                            id = stableId,
                            startTimestampEpochMs = actStart,
                            endTimestampEpochMs = actEnd,
                            startPoint = startPt,
                            endPoint = endPt,
                            rawPoints = sortedRawPts,
                            simplifiedPoints = simplified,
                            distanceMeters = calculatedDist,
                            durationMillis = duration,
                            transport = TransportPrediction(
                                mode = act.transportMode,
                                confidence = act.confidence,
                                reason = "Google Timeline Semantic Activity: ${act.rawActivityType ?: "UNKNOWN"}"
                            ),
                            geometryProvenance = provenance
                        )
                    )
                } else {
                    warnings.add("Activity segment at $actStart has missing endpoints")
                }
            }

            // Step 4: Mark timelinePath points occurring during Visits (P0-05: stationary/place observations, NOT movements)
            for (visit in canonicalVisits) {
                val vStart = visit.startTimestampEpochMs
                val vEnd = visit.endTimestampEpochMs
                for (pt in sortedObservedPoints) {
                    if (pt.timestampEpochMs in vStart..vEnd) {
                        assignedPointIds.add(pt.id)
                    }
                }
            }

            // Step 5: Fallback Movement creation ONLY when genuine movement is uncovered by any Activity or Visit (P0-06)
            val uncoveredPoints = sortedObservedPoints.filter { !assignedPointIds.contains(it.id) }
            if (uncoveredPoints.isNotEmpty()) {
                val clusters = mutableListOf<MutableList<LocationPoint>>()
                for (pt in uncoveredPoints) {
                    if (clusters.isEmpty() || pt.timestampEpochMs - clusters.last().last().timestampEpochMs > 2 * 3600_000L) {
                        clusters.add(mutableListOf(pt))
                    } else {
                        clusters.last().add(pt)
                    }
                }

                for (cluster in clusters) {
                    val dedup = deduplicateConsecutivePoints(cluster.map { it.coordinate })
                    if (dedup.size >= 2) {
                        val dist = GeodesicUtils.pathDistanceMeters(dedup)
                        val cStart = cluster.first().timestampEpochMs
                        val cEnd = cluster.last().timestampEpochMs
                        val dur = maxOf(1000L, cEnd - cStart)
                        val speedKmh = (dist / 1000.0) / (dur / 3_600_000.0)

                        // Genuine displacement filter: >= 150m and >= 2.0 km/h
                        if (dist >= 150.0 && speedKmh >= 2.0) {
                            val startPt = dedup.first()
                            val endPt = dedup.last()
                            val simplified = if (dedup.size > 2) DouglasPeucker.simplify(dedup, 20.0) else dedup
                            val stableId = generateDeterministicId("fallback_path", cStart, cEnd, startPt, null)
                            fusedMovementSegments.add(
                                MovementSegment(
                                    id = stableId,
                                    startTimestampEpochMs = cStart,
                                    endTimestampEpochMs = cEnd,
                                    startPoint = startPt,
                                    endPoint = endPt,
                                    rawPoints = cluster,
                                    simplifiedPoints = simplified,
                                    distanceMeters = dist,
                                    durationMillis = dur,
                                    transport = TransportPrediction(
                                        mode = TransportMode.UNKNOWN,
                                        confidence = 0.5f,
                                        reason = "Uncovered Timeline Path Fallback"
                                    ),
                                    geometryProvenance = GeometryProvenance.OBSERVED
                                )
                            )
                        }
                    }
                }
            }

            // Include legacy segments
            fusedMovementSegments.addAll(legacySegments)

            // Step 6: If no semantic visits/segments exist, reconstruct them via dwell clustering from raw points
            val finalVisits = mutableListOf<Visit>().apply { addAll(canonicalVisits) }
            val finalSegments = mutableListOf<MovementSegment>().apply { addAll(fusedMovementSegments) }

            if (finalVisits.isEmpty() && finalSegments.isEmpty() && rawPoints.isNotEmpty()) {
                val sortedRaw = rawPoints.sortedBy { it.timestampEpochMs }
                reconstructFromRawPoints(sortedRaw, finalVisits, finalSegments)
            }

            // Sort everything chronologically
            val sortedVisits = finalVisits.sortedBy { it.startTimestampEpochMs }
            val sortedSegments = finalSegments.sortedBy { it.startTimestampEpochMs }
            val sortedPoints = rawPoints.sortedBy { it.timestampEpochMs }

            val totalRecovered = sortedVisits.size + sortedSegments.size + sortedPoints.size

            val status: TimelineParseStatus
            val errorMessage: String?

            when {
                totalRecovered == 0 && summary.timelineElementsCount == 0 -> {
                    if (summary.totalDispatched > 0) {
                        status = TimelineParseStatus.FATAL_UNSUPPORTED_FORMAT
                        errorMessage = "Unsupported JSON schema: no Timeline visits, activities, or raw signals found"
                    } else {
                        status = TimelineParseStatus.FATAL_MALFORMED_INPUT
                        errorMessage = summary.parseException?.message ?: "Malformed or non-JSON input file"
                    }
                    if (warnings.isEmpty()) {
                        warnings.add(errorMessage)
                    }
                }
                summary.wasTruncated || summary.chunkFailuresCount > 0 || warnings.isNotEmpty() -> {
                    status = TimelineParseStatus.PARTIAL_WITH_WARNINGS
                    if (summary.wasTruncated) {
                        warnings.add("Timeline file appears incomplete or truncated; $totalRecovered records recovered.")
                    }
                    errorMessage = null
                }
                else -> {
                    status = TimelineParseStatus.SUCCESS
                    errorMessage = null
                }
            }

            TimelineParseResult(
                visits = sortedVisits,
                movementSegments = sortedSegments,
                rawLocationPoints = sortedPoints,
                status = status,
                warnings = warnings,
                errorMessage = errorMessage
            )
        }

    /**
     * Incremental streaming object parser: buffers only one array element at a time.
     */
    private inline fun streamParseObjects(
        reader: BufferedReader,
        crossinline onObject: (JsonObject) -> Boolean,
        crossinline onChunkError: (Exception) -> Unit
    ): StreamParseSummary {
        val sb = StringBuilder(4096)
        var objectDepth = 0
        var arrayDepth = 0
        var itemDepth = 0
        var insideString = false
        var isEscaped = false
        var cInt: Int
        var totalDispatched = 0
        var timelineElementsCount = 0
        var chunkFailuresCount = 0
        var hasStarted = false

        while (reader.read().also { cInt = it } != -1) {
            val c = cInt.toChar()

            if (!hasStarted && !c.isWhitespace()) {
                hasStarted = true
                if (c != '{' && c != '[') {
                    return StreamParseSummary(0, 0, 0, wasTruncated = false, parseException = IllegalArgumentException("Input is not a valid JSON object or array"))
                }
            }

            if (c == '"' && !isEscaped) {
                insideString = !insideString
            }
            isEscaped = (c == '\\' && !isEscaped)

            if (insideString) {
                if (itemDepth > 0 || (objectDepth >= 1 && arrayDepth == 0)) {
                    sb.append(c)
                }
                continue
            }

            // Outside string literals
            when (c) {
                '{' -> {
                    objectDepth++
                    if (arrayDepth >= 1) {
                        if (itemDepth == 0) {
                            sb.setLength(0)
                        }
                        itemDepth++
                        sb.append(c)
                    } else if (objectDepth == 1 && arrayDepth == 0) {
                        sb.setLength(0)
                        sb.append(c)
                    } else if (objectDepth > 1 && arrayDepth == 0) {
                        sb.append(c)
                    }
                }
                '}' -> {
                    objectDepth--
                    if (itemDepth > 0) {
                        sb.append(c)
                        itemDepth--
                        if (itemDepth == 0) {
                            val chunk = sb.toString().trim()
                            sb.setLength(0)
                            if (chunk.startsWith("{") && chunk.endsWith("}")) {
                                try {
                                    val parsed = json.parseToJsonElement(chunk)
                                    if (parsed is JsonObject) {
                                        totalDispatched++
                                        if (onObject(parsed)) timelineElementsCount++
                                    }
                                } catch (e: Exception) {
                                    chunkFailuresCount++
                                    onChunkError(e)
                                }
                            }
                        }
                    } else if (objectDepth == 0 && arrayDepth == 0) {
                        sb.append(c)
                        val chunk = sb.toString().trim()
                        sb.setLength(0)
                        if (chunk.startsWith("{") && chunk.endsWith("}")) {
                            try {
                                val parsed = json.parseToJsonElement(chunk)
                                if (parsed is JsonObject) {
                                    totalDispatched++
                                    if (onObject(parsed)) timelineElementsCount++
                                }
                            } catch (e: Exception) {
                                chunkFailuresCount++
                                onChunkError(e)
                            }
                        }
                    } else if (objectDepth > 0 && arrayDepth == 0) {
                        sb.append(c)
                    }
                }
                '[' -> {
                    if (itemDepth > 0) {
                        sb.append(c)
                    }
                    arrayDepth++
                }
                ']' -> {
                    arrayDepth = maxOf(0, arrayDepth - 1)
                    if (itemDepth > 0) {
                        sb.append(c)
                    }
                }
                else -> {
                    if (itemDepth > 0 || (objectDepth >= 1 && arrayDepth == 0)) {
                        sb.append(c)
                    }
                }
            }
        }

        val wasTruncated = insideString || itemDepth > 0 || objectDepth != 0 || arrayDepth != 0 || !hasStarted
        return StreamParseSummary(totalDispatched, timelineElementsCount, chunkFailuresCount, wasTruncated)
    }

    private fun dispatchJsonObject(
        elem: JsonObject,
        filter: DateRangeFilter?,
        rawVisits: MutableList<Visit>,
        rawActivities: MutableList<SemanticActivityCandidate>,
        allObservedPathPoints: MutableList<LocationPoint>,
        legacySegments: MutableList<MovementSegment>,
        rawPoints: MutableList<LocationPoint>,
        warnings: MutableList<String>
    ): Boolean {
        // 1. Check if it is a container object with sub-arrays (e.g. root wrapper)
        (elem["semanticSegments"] as? JsonArray)?.let { arr ->
            var count = 0
            for (item in arr) {
                if (item is JsonObject) {
                    if (parseSingleSemanticSegment(item, filter, rawVisits, rawActivities, allObservedPathPoints, rawPoints, warnings)) count++
                }
            }
            return count > 0
        }
        (elem["timelineObjects"] as? JsonArray)?.let { arr ->
            var count = 0
            for (item in arr) {
                if (item is JsonObject) {
                    if (parseSingleTimelineObject(item, filter, rawVisits, legacySegments, rawPoints, warnings)) count++
                }
            }
            return count > 0
        }
        (elem["rawSignals"] as? JsonArray)?.let { arr ->
            for (item in arr) {
                if (item is JsonObject) parseSingleRawSignal(item, filter, rawPoints)
            }
            return true
        }
        (elem["locations"] as? JsonArray)?.let { arr ->
            for (item in arr) {
                if (item is JsonObject) parseSingleRawSignal(item, filter, rawPoints)
            }
            return true
        }

        // 2. Self-describing single segment items
        if (elem.containsKey("visit") || elem.containsKey("activity") || elem.containsKey("timelinePath")) {
            return parseSingleSemanticSegment(elem, filter, rawVisits, rawActivities, allObservedPathPoints, rawPoints, warnings)
        } else if (elem.containsKey("placeVisit") || elem.containsKey("activitySegment")) {
            return parseSingleTimelineObject(elem, filter, rawVisits, legacySegments, rawPoints, warnings)
        } else if (elem.containsKey("position") || elem.containsKey("latitudeE7") || elem.containsKey("latLng") || elem.containsKey("LatLng")) {
            parseSingleRawSignal(elem, filter, rawPoints)
            return true
        }
        return false
    }

    private fun parseSingleSemanticSegment(
        item: JsonObject,
        filter: DateRangeFilter?,
        rawVisits: MutableList<Visit>,
        rawActivities: MutableList<SemanticActivityCandidate>,
        allObservedPathPoints: MutableList<LocationPoint>,
        rawPoints: MutableList<LocationPoint>,
        warnings: MutableList<String>
    ): Boolean {
        val startTimeStr = (item["startTime"] as? JsonPrimitive)?.contentOrNull
        val endTimeStr = (item["endTime"] as? JsonPrimitive)?.contentOrNull
        val startEpoch = TimeUtils.parseToInstant(startTimeStr)?.toEpochMilli()

        if (startEpoch == null) {
            warnings.add("Skipped semantic segment with missing or invalid startTime: $startTimeStr")
            return false
        }

        val endEpoch = TimeUtils.parseToInstant(endTimeStr)?.toEpochMilli() ?: startEpoch

        if (filter != null && !filter.overlaps(startEpoch, endEpoch)) {
            return true // Filtered out by date range interval, but valid
        }

        var matched = false

        // 1. Check Visit
        (item["visit"] as? JsonObject)?.let { visitObj ->
            val topCandidate = (visitObj["topCandidate"] as? JsonObject) ?: visitObj
            val placeLoc = (topCandidate["placeLocation"] as? JsonObject)
                ?: (topCandidate["location"] as? JsonObject)
                ?: topCandidate
            val point = parseCoordinate(placeLoc)
            if (point != null) {
                val rawName = (topCandidate["placeName"] as? JsonPrimitive)?.contentOrNull
                    ?: (topCandidate["name"] as? JsonPrimitive)?.contentOrNull
                val semanticType = (topCandidate["semanticType"] as? JsonPrimitive)?.contentOrNull
                val address = (topCandidate["address"] as? JsonPrimitive)?.contentOrNull
                    ?: (topCandidate["placeAddress"] as? JsonPrimitive)?.contentOrNull

                val placeName = when {
                    !rawName.isNullOrBlank() && !rawName.equals("UNKNOWN", ignoreCase = true) -> rawName
                    semanticType.equals("INFERRED_HOME", ignoreCase = true) || semanticType.equals("HOME", ignoreCase = true) -> "Home"
                    semanticType.equals("INFERRED_WORK", ignoreCase = true) || semanticType.equals("WORK", ignoreCase = true) -> "Work"
                    else -> null
                }

                val placeId = (topCandidate["placeId"] as? JsonPrimitive)?.contentOrNull
                val prob = (topCandidate["probability"] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 0.95f

                val stableId = generateDeterministicId("visit", startEpoch, endEpoch, point, placeId ?: placeName)
                rawVisits.add(
                    Visit(
                        id = stableId,
                        placeName = placeName,
                        placeAddress = address,
                        placeId = placeId,
                        location = point,
                        startTimestampEpochMs = startEpoch,
                        endTimestampEpochMs = endEpoch,
                        confidence = prob
                    )
                )
                matched = true
            } else {
                warnings.add("Visit segment at $startTimeStr has missing or invalid coordinates")
            }
        }

        // 2. Parse Timeline Path points into observational track pool (P0-01, P0-03)
        val timelinePathArray = (item["timelinePath"] as? JsonArray)
            ?: ((item["activity"] as? JsonObject)?.get("timelinePath") as? JsonArray)
        val segmentPathPoints = mutableListOf<LocationPoint>()
        timelinePathArray?.forEach { pElement ->
            val pObj = pElement as? JsonObject
            val pt = parseCoordinate(pObj)
            if (pt != null) {
                val timeStr = (pObj?.get("time") as? JsonPrimitive)?.contentOrNull
                    ?: (pObj?.get("timestamp") as? JsonPrimitive)?.contentOrNull
                val durationOffsetMinutes = (pObj?.get("durationMinutesOffsetFromStartTime") as? JsonPrimitive)?.longOrNull
                val pTime = when {
                    timeStr != null -> TimeUtils.parseToInstant(timeStr)?.toEpochMilli() ?: startEpoch
                    durationOffsetMinutes != null -> startEpoch + (durationOffsetMinutes * 60_000L)
                    else -> startEpoch
                }
                val lp = LocationPoint(generateDeterministicId("path", pTime, pTime, pt, null), pTime, pt)
                segmentPathPoints.add(lp)
                allObservedPathPoints.add(lp)
                rawPoints.add(lp)
                matched = true
            }
        }

        // 3. Check Semantic Activity (Primary Movement Episode, P0-02)
        val actObj = item["activity"] as? JsonObject
        if (actObj != null) {
            val topCandidate = (actObj["topCandidate"] as? JsonObject) ?: actObj
            val actTypeStr = (topCandidate["type"] as? JsonPrimitive)?.contentOrNull
                ?: (topCandidate["activityType"] as? JsonPrimitive)?.contentOrNull
            val prob = (topCandidate["probability"] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 0.85f
            val dist = (actObj["distanceMeters"] as? JsonPrimitive)?.doubleOrNull
                ?: (actObj["distance"] as? JsonPrimitive)?.doubleOrNull ?: 0.0

            val startPtDirect = parseCoordinate(actObj["start"] as? JsonObject ?: actObj["startLocation"] as? JsonObject)
            val endPtDirect = parseCoordinate(actObj["end"] as? JsonObject ?: actObj["endLocation"] as? JsonObject)

            val simplifiedRawPoints = mutableListOf<LocationPoint>()
            val rawPath = actObj["simplifiedRawPath"] as? JsonObject
            (rawPath?.get("points") as? JsonArray)?.forEach { pElement ->
                val pObj = pElement as? JsonObject
                if (pObj != null) {
                    val pt = parseCoordinate(pObj)
                    val pTime = TimeUtils.parseToInstant((pObj["timestamp"] as? JsonPrimitive)?.contentOrNull)?.toEpochMilli()
                        ?: startEpoch
                    if (pt != null) {
                        val lp = LocationPoint(generateDeterministicId("point", pTime, pTime, pt, null), pTime, pt)
                        simplifiedRawPoints.add(lp)
                        rawPoints.add(lp)
                    }
                }
            }

            val mappedMode = mapActivityType(actTypeStr)

            rawActivities.add(
                SemanticActivityCandidate(
                    startEpochMs = startEpoch,
                    endEpochMs = endEpoch,
                    startPoint = startPtDirect,
                    endPoint = endPtDirect,
                    transportMode = mappedMode,
                    confidence = prob,
                    rawActivityType = actTypeStr,
                    distanceMeters = dist,
                    simplifiedRawPoints = simplifiedRawPoints,
                    segmentTimelinePathPoints = segmentPathPoints
                )
            )
            matched = true
        }

        return matched
    }

    private fun deduplicateConsecutivePoints(pts: List<GeoPoint>): List<GeoPoint> {
        if (pts.size <= 1) return pts
        val result = ArrayList<GeoPoint>(pts.size)
        result.add(pts[0])
        for (i in 1 until pts.size) {
            val prev = result.last()
            val curr = pts[i]
            if (GeodesicUtils.distanceMeters(prev, curr) >= 1.0) {
                result.add(curr)
            }
        }
        return result
    }

    private fun parseSingleTimelineObject(
        item: JsonObject,
        filter: DateRangeFilter?,
        rawVisits: MutableList<Visit>,
        legacySegments: MutableList<MovementSegment>,
        rawPoints: MutableList<LocationPoint>,
        warnings: MutableList<String>
    ): Boolean {
        var matched = false

        // Legacy placeVisit
        val placeVisit = item["placeVisit"] as? JsonObject
        if (placeVisit != null) {
            val duration = placeVisit["duration"] as? JsonObject
            val startEpoch = TimeUtils.parseToInstant((duration?.get("startTimestamp") as? JsonPrimitive)?.contentOrNull
                ?: (duration?.get("startTimestampMs") as? JsonPrimitive)?.contentOrNull)?.toEpochMilli()

            if (startEpoch != null) {
                val endEpoch = TimeUtils.parseToInstant((duration?.get("endTimestamp") as? JsonPrimitive)?.contentOrNull
                    ?: (duration?.get("endTimestampMs") as? JsonPrimitive)?.contentOrNull)?.toEpochMilli() ?: startEpoch

                if (filter == null || filter.overlaps(startEpoch, endEpoch)) {
                    val locationObj = placeVisit["location"] as? JsonObject
                    val point = parseCoordinate(locationObj)
                    if (point != null) {
                        val rawName = (locationObj?.get("name") as? JsonPrimitive)?.contentOrNull
                        val semanticType = (locationObj?.get("semanticType") as? JsonPrimitive)?.contentOrNull
                        val address = (locationObj?.get("address") as? JsonPrimitive)?.contentOrNull

                        val name = when {
                            !rawName.isNullOrBlank() && !rawName.equals("UNKNOWN", ignoreCase = true) -> rawName
                            semanticType.equals("INFERRED_HOME", ignoreCase = true) || semanticType.equals("HOME", ignoreCase = true) -> "Home"
                            semanticType.equals("INFERRED_WORK", ignoreCase = true) || semanticType.equals("WORK", ignoreCase = true) -> "Work"
                            else -> null
                        }
                        val placeId = (locationObj?.get("placeId") as? JsonPrimitive)?.contentOrNull
                        val prob = (placeVisit["placeConfidence"] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 0.90f

                        val stableId = generateDeterministicId("visit", startEpoch, endEpoch, point, placeId ?: name)
                        rawVisits.add(
                            Visit(
                                id = stableId,
                                placeName = name,
                                placeAddress = address,
                                placeId = placeId,
                                location = point,
                                startTimestampEpochMs = startEpoch,
                                endTimestampEpochMs = endEpoch,
                                confidence = prob
                            )
                        )
                        matched = true
                    }
                }
            }
        }

        // Legacy activitySegment
        val actSeg = item["activitySegment"] as? JsonObject
        if (actSeg != null) {
            val duration = actSeg["duration"] as? JsonObject
            val startEpoch = TimeUtils.parseToInstant((duration?.get("startTimestamp") as? JsonPrimitive)?.contentOrNull
                ?: (duration?.get("startTimestampMs") as? JsonPrimitive)?.contentOrNull)?.toEpochMilli()

            if (startEpoch != null) {
                val endEpoch = TimeUtils.parseToInstant((duration?.get("endTimestamp") as? JsonPrimitive)?.contentOrNull
                    ?: (duration?.get("endTimestampMs") as? JsonPrimitive)?.contentOrNull)?.toEpochMilli() ?: startEpoch

                if (filter == null || filter.overlaps(startEpoch, endEpoch)) {
                    val startLoc = parseCoordinate(actSeg["startLocation"] as? JsonObject)
                    val endLoc = parseCoordinate(actSeg["endLocation"] as? JsonObject)
                    val actType = (actSeg["activityType"] as? JsonPrimitive)?.contentOrNull
                    val dist = (actSeg["distance"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
                    val confidence = (actSeg["confidence"] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 0.85f

                    val segPoints = mutableListOf<LocationPoint>()
                    val rawPath = actSeg["simplifiedRawPath"] as? JsonObject
                    (rawPath?.get("points") as? JsonArray)?.forEach { pElement ->
                        val pObj = pElement as? JsonObject
                        if (pObj != null) {
                            val pt = parseCoordinate(pObj)
                            val pTime = TimeUtils.parseToInstant((pObj["timestamp"] as? JsonPrimitive)?.contentOrNull)?.toEpochMilli()
                                ?: startEpoch
                            if (pt != null) {
                                val lp = LocationPoint(generateDeterministicId("point", pTime, pTime, pt, null), pTime, pt)
                                segPoints.add(lp)
                                rawPoints.add(lp)
                            }
                        }
                    }

                    val startPoint = startLoc ?: segPoints.firstOrNull()?.coordinate
                    val endPoint = endLoc ?: segPoints.lastOrNull()?.coordinate ?: startPoint

                    if (startPoint != null && endPoint != null) {
                        val geoPoints = if (segPoints.isNotEmpty()) segPoints.map { it.coordinate } else listOf(startPoint, endPoint)
                        val calculatedDist = if (dist > 0.0) dist else GeodesicUtils.pathDistanceMeters(geoPoints)
                        val durationMs = maxOf(0L, endEpoch - startEpoch)
                        val mappedMode = mapActivityType(actType)
                        val provenance = if (segPoints.isNotEmpty()) GeometryProvenance.SIMPLIFIED_OBSERVED else if (mappedMode == TransportMode.AIRPLANE) GeometryProvenance.ESTIMATED_GEODESIC else GeometryProvenance.ENDPOINT_INTERPOLATED

                        val stableId = generateDeterministicId("act", startEpoch, endEpoch, startPoint, actType)
                        legacySegments.add(
                            MovementSegment(
                                id = stableId,
                                startTimestampEpochMs = startEpoch,
                                endTimestampEpochMs = endEpoch,
                                startPoint = startPoint,
                                endPoint = endPoint,
                                simplifiedPoints = if (geoPoints.size > 2) DouglasPeucker.simplify(geoPoints, 20.0) else geoPoints,
                                distanceMeters = calculatedDist,
                                durationMillis = durationMs,
                                transport = TransportPrediction(
                                    mode = mappedMode,
                                    confidence = confidence,
                                    reason = "Legacy Timeline Activity: ${actType ?: "UNKNOWN"}"
                                ),
                                geometryProvenance = provenance
                            )
                        )
                        matched = true
                    } else {
                        warnings.add("Legacy activity segment at $startEpoch has missing endpoints")
                    }
                }
            } else {
                warnings.add("Skipped legacy activity segment with missing startTimestamp")
            }
        }

        return matched
    }

    private fun parseSingleRawSignal(
        elem: JsonObject,
        filter: DateRangeFilter?,
        rawPoints: MutableList<LocationPoint>
    ) {
        val timeStr = (elem["timestamp"] as? JsonPrimitive)?.contentOrNull
            ?: (elem["timestampMs"] as? JsonPrimitive)?.contentOrNull
        val epochMs = TimeUtils.parseToInstant(timeStr)?.toEpochMilli() ?: return

        if (filter != null && !filter.contains(epochMs)) return

        val point = parseCoordinate(elem["position"] as? JsonObject ?: elem) ?: return
        val stableId = generateDeterministicId("raw", epochMs, epochMs, point, null)
        rawPoints.add(LocationPoint(stableId, epochMs, point))
    }

    /**
     * Dwell-clustering trajectory reconstruction for rawSignals-only timeline files.
     */
    private fun reconstructFromRawPoints(
        points: List<LocationPoint>,
        visits: MutableList<Visit>,
        segments: MutableList<MovementSegment>
    ) {
        if (points.isEmpty()) return

        var currentCluster = mutableListOf(points[0])

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val dist = GeodesicUtils.distanceMeters(prev.coordinate, curr.coordinate)
            val timeGapMs = curr.timestampEpochMs - prev.timestampEpochMs

            if (dist < 100.0 && timeGapMs < 30 * 60 * 1000L) {
                currentCluster.add(curr)
            } else {
                processCluster(currentCluster, visits, segments)
                currentCluster = mutableListOf(curr)
            }
        }
        processCluster(currentCluster, visits, segments)
    }

    private fun processCluster(
        cluster: List<LocationPoint>,
        visits: MutableList<Visit>,
        segments: MutableList<MovementSegment>
    ) {
        if (cluster.isEmpty()) return
        val startEpoch = cluster.first().timestampEpochMs
        val endEpoch = cluster.last().timestampEpochMs
        val duration = endEpoch - startEpoch

        val geoPoints = cluster.map { it.coordinate }
        val netDisplacement = GeodesicUtils.distanceMeters(geoPoints.first(), geoPoints.last())
        val pathDistance = GeodesicUtils.pathDistanceMeters(geoPoints)
        val avgLat = cluster.map { it.coordinate.latitude }.average()
        val avgLng = circularLongitudeAverage(cluster.map { it.coordinate.longitude })
        val centroid = GeoPoint(avgLat, avgLng)
        val maxDistanceFromCentroid = cluster.maxOf { GeodesicUtils.distanceMeters(it.coordinate, centroid) }
        val durationSeconds = if (duration > 0) duration / 1000.0 else 1.0
        val avgSpeedMps = pathDistance / durationSeconds

        val isStationaryDwell = duration >= 10 * 60 * 1000L &&
                maxDistanceFromCentroid <= 150.0 &&
                netDisplacement <= 100.0 &&
                (avgSpeedMps <= 0.6 || netDisplacement <= 60.0)

        if (isStationaryDwell) {
            val point = centroid
            val stableId = generateDeterministicId("clustered_visit", startEpoch, endEpoch, point, null)
            visits.add(
                Visit(
                    id = stableId,
                    placeName = String.format(java.util.Locale.US, "Visited Location (%.4f, %.4f)", avgLat, avgLng),
                    placeAddress = null,
                    placeId = null,
                    location = point,
                    startTimestampEpochMs = startEpoch,
                    endTimestampEpochMs = endEpoch,
                    confidence = 0.75f
                )
            )
        } else if (cluster.size >= 2 || pathDistance > 50.0) {
            val startPt = cluster.first().coordinate
            val endPt = cluster.last().coordinate
            val stableId = generateDeterministicId("clustered_act", startEpoch, endEpoch, startPt, null)
            val predictedMode = if (avgSpeedMps in 0.4..2.5 && pathDistance >= 100.0) TransportMode.WALK else TransportMode.UNKNOWN
            val reason = if (predictedMode == TransportMode.WALK) "Reconstructed walking path from continuous GPS movement" else "Reconstructed from GPS signals"
            segments.add(
                MovementSegment(
                    id = stableId,
                    startTimestampEpochMs = startEpoch,
                    endTimestampEpochMs = endEpoch,
                    startPoint = startPt,
                    endPoint = endPt,
                    simplifiedPoints = if (geoPoints.size > 2) DouglasPeucker.simplify(geoPoints, 20.0) else geoPoints,
                    distanceMeters = pathDistance,
                    durationMillis = duration,
                    transport = TransportPrediction(predictedMode, 0.6f, reason),
                    geometryProvenance = GeometryProvenance.OBSERVED
                )
            )
        }
    }

    private fun circularLongitudeAverage(longitudes: List<Double>): Double {
        if (longitudes.isEmpty()) return 0.0
        var sumSin = 0.0
        var sumCos = 0.0
        for (lng in longitudes) {
            val rad = Math.toRadians(lng)
            sumSin += Math.sin(rad)
            sumCos += Math.cos(rad)
        }
        val meanRad = Math.atan2(sumSin / longitudes.size, sumCos / longitudes.size)
        var deg = Math.toDegrees(meanRad)
        while (deg > 180.0) deg -= 360.0
        while (deg < -180.0) deg += 360.0
        return deg
    }

    private fun parseCoordinate(obj: JsonObject?): GeoPoint? {
        if (obj == null) return null

        val alt = (obj["altitudeMeters"] as? JsonPrimitive)?.doubleOrNull
            ?: (obj["altitude"] as? JsonPrimitive)?.doubleOrNull
        val acc = (obj["accuracyMeters"] as? JsonPrimitive)?.doubleOrNull?.toFloat()
            ?: (obj["accuracy"] as? JsonPrimitive)?.doubleOrNull?.toFloat()

        // Format 1: String "37.5665°, 126.9780°" or "geo:37.5665,126.9780"
        val latLngStr = (obj["latLng"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["LatLng"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["point"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["geo"] as? JsonPrimitive)?.contentOrNull
        if (latLngStr != null) {
            val parts = latLngStr.replace("°", "").replace("geo:", "").split(",")
            if (parts.size == 2) {
                val lat = parts[0].trim().toDoubleOrNull()
                val lng = parts[1].trim().toDoubleOrNull()
                if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) {
                    return GeoPoint(lat, lng, altitudeMeters = alt, accuracyMeters = acc)
                }
            }
        }

        // Format 2: E7 integers (latitudeE7, longitudeE7)
        val latE7 = (obj["latitudeE7"] as? JsonPrimitive)?.longOrNull
        val lngE7 = (obj["longitudeE7"] as? JsonPrimitive)?.longOrNull
        if (latE7 != null && lngE7 != null && !(latE7 == 0L && lngE7 == 0L)) {
            return GeoPoint(latE7 / 1e7, lngE7 / 1e7, altitudeMeters = alt, accuracyMeters = acc)
        }

        // Format 3: Double latitude / longitude
        val lat = (obj["latitude"] as? JsonPrimitive)?.doubleOrNull ?: (obj["lat"] as? JsonPrimitive)?.doubleOrNull
        val lng = (obj["longitude"] as? JsonPrimitive)?.doubleOrNull ?: (obj["lng"] as? JsonPrimitive)?.doubleOrNull
        if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) {
            return GeoPoint(lat, lng, altitudeMeters = alt, accuracyMeters = acc)
        }

        // Format 4: point object wrapper (e.g. { "point": { "latLng": "..." } })
        val pointObj = obj["point"] as? JsonObject
        if (pointObj != null) {
            return parseCoordinate(pointObj)
        }

        return null
    }

    private fun mapActivityType(actType: String?): TransportMode {
        if (actType.isNullOrBlank()) return TransportMode.UNKNOWN
        val upper = actType.uppercase()
        return when {
            upper.contains("FLYING") || upper.contains("AIRPLANE") || upper.contains("FLIGHT") -> TransportMode.AIRPLANE
            upper.contains("TRAIN") -> TransportMode.TRAIN
            upper.contains("SUBWAY") || upper.contains("METRO") || upper.contains("TRAM") -> TransportMode.SUBWAY
            upper.contains("BUS") -> TransportMode.BUS
            upper.contains("PASSENGER_VEHICLE") || upper.contains("IN_VEHICLE") || upper.contains("DRIVE") || upper.contains("CAR") -> TransportMode.CAR
            upper.contains("CYCLING") || upper.contains("BICYCLE") || upper.contains("ON_BICYCLE") -> TransportMode.BICYCLE
            upper.contains("RUNNING") || upper.contains("ON_FOOT_RUNNING") -> TransportMode.RUN
            upper.contains("WALKING") || upper.contains("ON_FOOT") -> TransportMode.WALK
            upper.contains("FERRY") || upper.contains("BOAT") -> TransportMode.FERRY
            else -> TransportMode.UNKNOWN
        }
    }

    private fun generateDeterministicId(
        prefix: String,
        startEpoch: Long,
        endEpoch: Long,
        point: GeoPoint,
        extra: String?
    ): String {
        val raw = "$prefix:$startEpoch:$endEpoch:${point.latitude}:${point.longitude}:${extra ?: ""}"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(raw.toByteArray(StandardCharsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }
}
