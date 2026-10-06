package com.noise.appscout.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noise.appscout.domain.model.AppSettings
import com.noise.appscout.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings.DEFAULT,
    /** Transient confirmation shown after "clear cached release data" succeeded. */
    val cacheCleared: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val cacheCleared = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        cacheCleared,
    ) { settings, cleared ->
        SettingsUiState(settings = settings, cacheCleared = cleared)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setNotificationsEnabled(enabled: Boolean) = intent { settingsRepository.setNotificationsEnabled(enabled) }

    fun setBackgroundRefreshEnabled(enabled: Boolean) = intent { settingsRepository.setBackgroundRefreshEnabled(enabled) }

    fun setIncludePrereleases(enabled: Boolean) = intent { settingsRepository.setIncludePrereleases(enabled) }

    fun clearCachedReleaseData() = intent {
        settingsRepository.clearCachedReleaseData()
        cacheCleared.value = true
        delay(4_000)
        cacheCleared.value = false
    }

    private fun intent(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
