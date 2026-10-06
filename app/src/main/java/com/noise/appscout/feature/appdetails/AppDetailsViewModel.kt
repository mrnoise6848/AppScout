package com.noise.appscout.feature.appdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.noise.appscout.core.common.TimeProvider
import com.noise.appscout.core.ui.navigation.Routes
import com.noise.appscout.domain.usecase.ObserveTrackedAppStatusesUseCase
import com.noise.appscout.domain.usecase.RefreshAppUseCase
import com.noise.appscout.domain.usecase.StopTrackingUseCase
import com.noise.appscout.domain.usecase.TrackedAppStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppDetailsUiState(
    val status: TrackedAppStatus? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** Set when the tracked app no longer exists; the screen closes itself. */
    val isStopped: Boolean = false,
)

@HiltViewModel
class AppDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeStatuses: ObserveTrackedAppStatusesUseCase,
    private val refreshApp: RefreshAppUseCase,
    private val stopTracking: StopTrackingUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Routes.AppDetails>()
    private val isRefreshing = MutableStateFlow(false)
    private val isStopped = MutableStateFlow(false)

    val state: StateFlow<AppDetailsUiState> = combine(
        observeStatuses(),
        isRefreshing,
        isStopped,
    ) { statuses, refreshing, stopped ->
        AppDetailsUiState(
            status = statuses.firstOrNull { it.snapshot.app.id == route.trackedAppId },
            isLoading = false,
            isRefreshing = refreshing,
            isStopped = stopped,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDetailsUiState())

    init {
        autoRefreshIfStale()
    }

    /**
     * Opens from cache instantly and only goes to the network when the cached check is missing
     * or older than [AUTO_REFRESH_AFTER_MS]; otherwise every tap would visibly re-run the screen.
     * "Check now" always forces a refresh.
     */
    private fun autoRefreshIfStale() {
        viewModelScope.launch {
            val firstKnown = state.first { !it.isLoading }
            val checkedAt = firstKnown.status?.check?.checkedAt
            val isStale = checkedAt == null || timeProvider.nowMillis() - checkedAt >= AUTO_REFRESH_AFTER_MS
            if (isStale) refresh()
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                refreshApp(route.trackedAppId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Offline: cached data stays on screen and is flagged stale by the resolver.
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private companion object {
        const val AUTO_REFRESH_AFTER_MS = 30 * 60 * 1000L
    }

    fun onStopTrackingConfirmed() {
        viewModelScope.launch {
            try {
                stopTracking(route.trackedAppId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // ignore; the app remains tracked and the user can retry
            } finally {
                isStopped.value = true
            }
        }
    }
}
