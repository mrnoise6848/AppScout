package com.noise.appscout.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noise.appscout.domain.model.ReleaseStatus
import com.noise.appscout.domain.usecase.ObserveTrackedAppStatusesUseCase
import com.noise.appscout.domain.usecase.RefreshTrackedAppsUseCase
import com.noise.appscout.domain.usecase.TrackedAppStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Home screen renders, derived in one place so the UI stays declarative. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** A refresh failed outright (offline, DNS, timeout); cached rows are still valid. */
    val refreshFailed: Boolean = false,
    val updates: List<TrackedAppStatus> = emptyList(),
    val upToDate: List<TrackedAppStatus> = emptyList(),
    val needsAttention: List<TrackedAppStatus> = emptyList(),
) {
    val trackedCount: Int get() = updates.size + upToDate.size + needsAttention.size
    val isEmpty: Boolean get() = !isLoading && trackedCount == 0
    val updateCount: Int get() = updates.size
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeStatuses: ObserveTrackedAppStatusesUseCase,
    private val refreshTrackedApps: RefreshTrackedAppsUseCase,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)
    private val refreshFailed = MutableStateFlow(false)
    private val isLoading = MutableStateFlow(true)
    private var refreshJob: Job? = null

    val state: StateFlow<HomeUiState> = combine(
        observeStatuses(),
        isRefreshing,
        refreshFailed,
        isLoading,
    ) { statuses, refreshing, failed, loading ->
        HomeUiState(
            isLoading = loading,
            isRefreshing = refreshing,
            refreshFailed = failed,
            updates = statuses.filter { it.check.status == ReleaseStatus.UPDATE_AVAILABLE }
                .sortedBy { it.displayName.lowercase() },
            upToDate = statuses.filter { it.check.status == ReleaseStatus.UP_TO_DATE }
                .sortedBy { it.displayName.lowercase() },
            needsAttention = statuses.filter {
                it.check.status == ReleaseStatus.SOURCE_ERROR ||
                    it.check.status == ReleaseStatus.VERSION_COMPARISON_UNCERTAIN ||
                    it.check.status == ReleaseStatus.NOT_INSTALLED
            }.sortedBy { it.displayName.lowercase() },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        refresh()
    }

    /** Fetches fresh releases; failures leave the cached state untouched. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            isRefreshing.value = true
            try {
                refreshTrackedApps()
                refreshFailed.value = false
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                refreshFailed.value = true
            } finally {
                isRefreshing.value = false
                isLoading.value = false
            }
        }
    }
}
