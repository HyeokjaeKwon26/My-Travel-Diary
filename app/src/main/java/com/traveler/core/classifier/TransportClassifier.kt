package com.traveler.core.classifier

import com.traveler.core.common.geo.GeodesicUtils
import com.traveler.core.model.MovementSegment
import com.traveler.core.model.TransportMode
import com.traveler.core.model.TransportPrediction

interface TransportClassifier {
    fun classify(segment: MovementSegment): TransportPrediction
}

data class ClassifierThresholds(
    val minStationaryDurationMs: Long = 10 * 60_000L, // 10 minutes
    val maxStationaryDriftMeters: Double = 60.0,
    val maxPedestrianSpeedKmh: Double = 6.5,
    val maxRunningSpeedKmh: Double = 14.0,
    val maxCyclingSpeedKmh: Double = 30.0,
    val maxHighwayCarSpeedKmh: Double = 140.0,
    val minHighSpeedRailKmh: Double = 145.0,
    val maxHighSpeedRailKmh: Double = 350.0,
    val minCruisingFlightSpeedKmh: Double = 360.0,
    val maxPlausibleFlightSpeedKmh: Double = 1150.0,
    val minFlightDistanceMeters: Double = 200_000.0, // 200 km
    val semanticConfidenceThreshold: Float = 0.70f
)

/**
 * Rule-based transport mode classifier.
 *
 * Implements Pass 20 Transport Policy (P0-10):
 * - If Google supplies a specific semantic transport mode (IN_BUS, IN_TRAIN, IN_FERRY, IN_PASSENGER_VEHICLE,
 *   WALKING, RUNNING, CYCLING, FLYING), preserve that mode by default even at lower confidence (e.g. 0.51).
 * - Heuristic classification is applied primarily when source mode is UNKNOWN or physically contradictory.
 */
class RuleBasedTransportClassifier(
    private val thresholds: ClassifierThresholds = ClassifierThresholds()
) : TransportClassifier {

    override fun classify(segment: MovementSegment): TransportPrediction {
        // 1. If user explicitly overrode it, preserve user override
        if (segment.isUserOverride) {
            return segment.transport
        }

        val durationHours = segment.durationMillis / 3_600_000.0
        val distanceKm = segment.distanceMeters / 1000.0
        val straightLineDistMeters = GeodesicUtils.distanceMeters(segment.startPoint, segment.endPoint)
        val straightLineDistKm = straightLineDistMeters / 1000.0
        val avgSpeedKmh = if (durationHours > 0.0) distanceKm / durationHours else 0.0
        val straightSpeedKmh = if (durationHours > 0.0) straightLineDistKm / durationHours else 0.0

        // 2. Check for Impossible GPS Spike / Outlier
        if (avgSpeedKmh > thresholds.maxPlausibleFlightSpeedKmh || straightSpeedKmh > thresholds.maxPlausibleFlightSpeedKmh) {
            return TransportPrediction(
                mode = TransportMode.UNKNOWN,
                confidence = 0.15f,
                reason = "Physically impossible speed (${avgSpeedKmh.toInt()} km/h) - suspected GPS glitch outlier"
            )
        }

        // 3. Specific Google Semantic Transport Mode Preservation (P0-10)
        if (segment.transport.mode != TransportMode.UNKNOWN) {
            val mode = segment.transport.mode

            // Check for physical contradiction
            val isPedestrianImpossible = (mode == TransportMode.WALK || mode == TransportMode.RUN) &&
                    avgSpeedKmh > 45.0 && segment.distanceMeters > 5000.0
            val isAviationRequired = (mode == TransportMode.CAR || mode == TransportMode.BUS || mode == TransportMode.WALK || mode == TransportMode.RUN || mode == TransportMode.BICYCLE) &&
                    straightLineDistMeters >= thresholds.minFlightDistanceMeters &&
                    straightSpeedKmh >= thresholds.minCruisingFlightSpeedKmh &&
                    straightSpeedKmh <= thresholds.maxPlausibleFlightSpeedKmh

            if (!isPedestrianImpossible && !isAviationRequired) {
                return segment.transport
            }
        }

        // 4. Check for Stationary GPS Jitter
        if (segment.distanceMeters < thresholds.maxStationaryDriftMeters &&
            segment.durationMillis >= thresholds.minStationaryDurationMs
        ) {
            return TransportPrediction(
                mode = TransportMode.UNKNOWN,
                confidence = 0.90f,
                reason = "Stationary dwell period with minor GPS fluctuation (${segment.distanceMeters.toInt()}m over ${segment.durationMillis / 60000}m)"
            )
        }

        // 5. Commercial Aviation Flight Detection (High speed > 360 km/h and long displacement > 200 km)
        if (straightLineDistMeters >= thresholds.minFlightDistanceMeters &&
            straightSpeedKmh >= thresholds.minCruisingFlightSpeedKmh &&
            straightSpeedKmh <= thresholds.maxPlausibleFlightSpeedKmh
        ) {
            val conf = if (straightLineDistKm > 500.0) 0.95f else 0.85f
            return TransportPrediction(
                mode = TransportMode.AIRPLANE,
                confidence = conf,
                reason = "Long displacement (${straightLineDistKm.toInt()} km) with aviation speed profile (${straightSpeedKmh.toInt()} km/h)"
            )
        }

        // 6. High-Speed Rail (145 km/h .. 350 km/h ground speed profile)
        if (avgSpeedKmh in thresholds.minHighSpeedRailKmh..thresholds.maxHighSpeedRailKmh) {
            return TransportPrediction(
                mode = TransportMode.TRAIN,
                confidence = 0.75f,
                reason = "High-speed ground transport speed profile (${avgSpeedKmh.toInt()} km/h)"
            )
        }

        // 7. Highway Vehicular Speed (45 .. 140 km/h)
        if (avgSpeedKmh in 45.0..thresholds.maxHighwayCarSpeedKmh) {
            return TransportPrediction(
                mode = TransportMode.CAR,
                confidence = 0.80f,
                reason = "Highway vehicular speed profile (${avgSpeedKmh.toInt()} km/h)"
            )
        }

        // 8. Urban Vehicular / Transit Speed (28 .. 45 km/h)
        if (avgSpeedKmh in 28.0..45.0) {
            return TransportPrediction(
                mode = TransportMode.CAR,
                confidence = 0.70f,
                reason = "City vehicular speed profile (${avgSpeedKmh.toInt()} km/h)"
            )
        }

        // 9. Moderate to Fast Cycling (14.0 .. 28.0 km/h)
        if (avgSpeedKmh in thresholds.maxRunningSpeedKmh..28.0) {
            return TransportPrediction(
                mode = TransportMode.BICYCLE,
                confidence = 0.75f,
                reason = "Speed (${String.format(java.util.Locale.US, "%.1f", avgSpeedKmh)} km/h) matches cycling profile"
            )
        }

        // 10. Ambiguous Running / Slow Cycling Zone (6.5 .. 14.0 km/h)
        if (avgSpeedKmh in thresholds.maxPedestrianSpeedKmh..thresholds.maxRunningSpeedKmh) {
            return if (avgSpeedKmh <= 10.5) {
                TransportPrediction(
                    mode = TransportMode.RUN,
                    confidence = 0.65f,
                    reason = "Speed (${String.format(java.util.Locale.US, "%.1f", avgSpeedKmh)} km/h) matches jogging/running pace"
                )
            } else {
                TransportPrediction(
                    mode = TransportMode.BICYCLE,
                    confidence = 0.60f,
                    reason = "Speed (${String.format(java.util.Locale.US, "%.1f", avgSpeedKmh)} km/h) in running/cycling transition range"
                )
            }
        }

        // 11. Walking Pace (0.4 .. 6.5 km/h)
        if (avgSpeedKmh in 0.4..thresholds.maxPedestrianSpeedKmh && segment.distanceMeters >= 50.0) {
            return TransportPrediction(
                mode = TransportMode.WALK,
                confidence = 0.88f,
                reason = "Pedestrian speed (${String.format(java.util.Locale.US, "%.1f", avgSpeedKmh)} km/h) with positive displacement (${segment.distanceMeters.toInt()}m)"
            )
        }

        return TransportPrediction(
            mode = TransportMode.UNKNOWN,
            confidence = 0.30f,
            reason = "Ambiguous motion profile (${String.format(java.util.Locale.US, "%.1f", avgSpeedKmh)} km/h, ${distanceKm.toInt()} km)"
        )
    }
}
