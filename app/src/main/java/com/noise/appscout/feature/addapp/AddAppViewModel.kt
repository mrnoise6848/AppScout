package com.noise.appscout.feature.addapp

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.noise.appscout.core.ui.navigation.Routes
import com.noise.appscout.domain.model.InstalledApp
import com.noise.appscout.domain.model.SourceErrorReason
import com.noise.appscout.domain.repository.InstalledAppRepository
import com.noise.appscout.domain.usecase.TrackAppResult
import com.noise.appscout.domain.usecase.TrackAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddAppUiState(
    val packageName: String = "",
    val installedApp: InstalledApp? = null,
    val repositoryUrl: String = "",
    val isValidating: Boolean = false,
    val latestReleaseTag: String? = null,
    val errorMessage: String? = null,
    val sourceError: SourceErrorReason? = null,
    val isSuccess: Boolean = false,
    val trackedAppId: String? = null,
)

@HiltViewModel
class AddAppViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val installedAppRepository: InstalledAppRepository,
    private val trackAppUseCase: TrackAppUseCase,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Routes.AddApp>()
    private val _state = MutableStateFlow(AddAppUiState(packageName = route.packageName))
    val state: StateFlow<AddAppUiState> = _state.asStateFlow()

    init {
        loadInstalledApp()
    }

    private fun loadInstalledApp() {
        viewModelScope.launch {
            val app = installedAppRepository.getInstalledApp(route.packageName)
            _state.update { it.copy(installedApp = app) }
        }
    }

    fun onRepositoryUrlChange(url: String) {
        _state.update {
            it.copy(
                repositoryUrl = url,
                errorMessage = null,
                sourceError = null,
                latestReleaseTag = null,
            )
        }
    }

    fun validateAndTrack() {
        val url = state.value.repositoryUrl.trim()
        if (url.isEmpty()) {
            _state.update { it.copy(errorMessage = "Enter a GitHub repository URL") }
            return
        }
        val pkg = state.value.packageName
        if (pkg.isBlank()) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isValidating = true,
                    errorMessage = null,
                    sourceError = null,
                    latestReleaseTag = null,
                )
            }

            when (val result = trackAppUseCase(pkg, url)) {
                is TrackAppResult.Success -> {
                    _state.update {
                        it.copy(
                            isValidating = false,
                            isSuccess = true,
                            trackedAppId = result.trackedApp.id,
                        )
                    }
                }
                TrackAppResult.InvalidSourceUrl -> {
                    _state.update {
                        it.copy(
                            isValidating = false,
                            errorMessage = "Enter a valid GitHub repository URL (https://github.com/owner/repo)",
                        )
                    }
                }
                TrackAppResult.AppNotFound -> {
                    _state.update {
                        it.copy(
                            isValidating = false,
                            errorMessage = "App is no longer installed on this device",
                        )
                    }
                }
                is TrackAppResult.SourceError -> {
                    _state.update {
                        it.copy(
                            isValidating = false,
                            sourceError = result.reason,
                        )
                    }
                }
            }
        }
    }
}
