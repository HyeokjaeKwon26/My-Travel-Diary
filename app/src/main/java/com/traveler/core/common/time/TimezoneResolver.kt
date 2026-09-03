package com.traveler.core.common.time

import com.traveler.core.common.geo.GeoPoint
import java.time.ZoneId

sealed interface TimezoneResolution {
    data class Resolved(val zoneId: ZoneId) : TimezoneResolution
    data object Offshore : TimezoneResolution
    data object Unavailable : TimezoneResolution
    data class Failure(val reason: String) : TimezoneResolution
    data class EngineInitializationFailure(val reason: String) : TimezoneResolution

    val zoneIdOrNull: ZoneId?
        get() = (this as? Resolved)?.zoneId
}

interface TimezoneResolver {
    suspend fun resolve(point: GeoPoint?): TimezoneResolution
    fun resolveSync(point: GeoPoint?): TimezoneResolution
    fun release() {}
}
