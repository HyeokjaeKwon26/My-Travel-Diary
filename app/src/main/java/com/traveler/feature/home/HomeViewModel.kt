package com.traveler.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.traveler.core.database.TravelerDatabase
import com.traveler.core.model.Trip
import com.traveler.data.repository.TripRepositoryImpl
import com.traveler.domain.repository.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TripRepository = TripRepositoryImpl(
        TravelerDatabase.getDatabase(application)
    )

    val trips: StateFlow<List<Trip>> = repository.getAllTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.cleanOrphanTripData()
        }
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            repository.deleteTrip(tripId)
        }
    }
}
