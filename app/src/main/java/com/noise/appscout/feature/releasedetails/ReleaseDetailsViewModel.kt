package com.noise.appscout.feature.releasedetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.noise.appscout.core.ui.navigation.Routes
import com.noise.appscout.domain.ai.AiSummaryResult
import com.noise.appscout.domain.model.AiSummary
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.repository.SettingsRepository
import com.noise.appscout.domain.usecase.AiSummaryOutcome
import com.noise.appscout.domain.usecase.GenerateAiSummaryUseCase
import com.noise.appscout.domain.usecase.ObserveTrackedAppStatusesUseCase
import com.noise.appscout.domain.usecase.TrackedAppStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** AI section state. It is deliberately independent from the release information shown above it. */
sealed interface AiUiState {

    /** Nothing requested yet (or no release to summarize). */
    data object Idle : AiUiState

    data object Loading : AiUiState

    data class Ready(val summary: AiSummary, val generated: Boolean) : AiUiState

    /** Explains why the summary is missing; release details stay fully usable. */
    data class Failed(val reason: AiSummaryResult) : AiUiState
}

data class ReleaseDetailsUiState(
    val isLoading: Boolean = true,
    val appName: String = "",
    val installedVersion: String? = null,
    val release: Release? = null,
    val status: TrackedAppStatus? = null,
    /** Whether AI summaries are switched on in settings (and therefore worth offering). */
    val aiEnabled: Boolean = false,
    val ai: AiUiState = AiUiState.Idle,
)

@HiltViewModel
class ReleaseDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeStatuses: ObserveTrackedAppStatusesUseCase,
    private val generateAiSummary: GenerateAiSummaryUseCase,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Routes.ReleaseDetails>()
    private val aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)

    val state: StateFlow<ReleaseDetailsUiState> = combine(
        observeStatuses(),
        aiState,
        settingsRepository.observeSettings(),
    ) { statuses, ai, settings ->
        val status = statuses.firstOrNull { it.snapshot.app.id == route.trackedAppId }
        ReleaseDetailsUiState(
            isLoading = false,
            appName = status?.displayName.orEmpty(),
            installedVersion = status?.check?.installedVersion,
            release = status?.check?.release ?: status?.snapshot?.latestRelease,
            status = status,
            aiEnabled = settings.aiEnabled,
            ai = if (status?.check?.release == null) AiUiState.Idle else ai,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReleaseDetailsUiState())

    /** Requests (or reuses) the AI summary. Safe to call again after a failure. */
    fun generateAi(force: Boolean = false) {
        val release = state.value.release ?: return
        if (aiState.value is AiUiState.Loading) return
        val appName = state.value.appName
        viewModelScope.launch {
            aiState.value = AiUiState.Loading
            aiState.value = try {
                when (val outcome = generateAiSummary(release, appName, force)) {
                    is AiSummaryOutcome.Available ->
                        AiUiState.Ready(outcome.summary, outcome.generated)
                    is AiSummaryOutcome.Unavailable ->
                        AiUiState.Failed(outcome.reason)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AiUiState.Failed(AiSummaryResult.NetworkError)
            }
        }
    }
}
