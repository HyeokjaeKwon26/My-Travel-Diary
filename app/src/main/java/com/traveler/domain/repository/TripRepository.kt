package com.traveler.domain.repository

import com.traveler.core.database.entity.UserOverrideEntity
import com.traveler.core.model.TransportMode
import com.traveler.core.model.Trip
import kotlinx.coroutines.flow.Flow

data class OrphanCleanupSummary(
    val deletedVisits: Int,
    val deletedSegments: Int,
    val deletedMedia: Int
)

interface TripRepository {
    fun getAllTrips(): Flow<List<Trip>>
    suspend fun getTripById(tripId: String): Trip?
    suspend fun saveTrip(trip: Trip)
    suspend fun deleteTrip(tripId: String)
    suspend fun cleanOrphanTripData(): OrphanCleanupSummary
    suspend fun updateTransportMode(tripId: String, segmentSourceId: String, mode: TransportMode)
    suspend fun updateVisitName(tripId: String, visitSourceId: String, name: String)
    suspend fun updateMediaVisit(tripId: String, mediaKey: String, visitSourceId: String?)
    suspend fun setRepresentativeMedia(tripId: String, mediaKey: String, isRepresentative: Boolean)
    suspend fun getDurableUserOverrides(): List<UserOverrideEntity>
}
