package com.noise.appscout.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noise.appscout.domain.model.AppSettings
import com.noise.appscout.core.security.ApiKeyVault
import com.noise.appscout.domain.repository.SettingsRepository
import com.noise.appscout.worker.RefreshScheduler
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
    /** Whether an API key currently sits in the Keystore vault (the key itself is never exposed). */
    val hasStoredApiKey: Boolean = false,
    /** Transient confirmation after saving/clearing the API key. */
    val apiKeySaved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val refreshScheduler: RefreshScheduler,
    private val apiKeyVault: ApiKeyVault,
) : ViewModel() {

    private val cacheCleared = MutableStateFlow(false)
    private val vaultState = MutableStateFlow(VaultState(apiKeyVault.hasKey(), saved = false))

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        cacheCleared,
        vaultState,
    ) { settings, cleared, vault ->
        SettingsUiState(
            settings = settings,
            cacheCleared = cleared,
            hasStoredApiKey = vault.hasKey,
            apiKeySaved = vault.saved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setNotificationsEnabled(enabled: Boolean) = intent { settingsRepository.setNotificationsEnabled(enabled) }

    fun setBackgroundRefreshEnabled(enabled: Boolean) = intent {
        settingsRepository.setBackgroundRefreshEnabled(enabled)
        // Schedule or drop the unique periodic job so a disabled setting never wakes the device.
        runCatching {
            if (enabled) refreshScheduler.schedule() else refreshScheduler.cancel()
        }
    }

    fun setIncludePrereleases(enabled: Boolean) = intent { settingsRepository.setIncludePrereleases(enabled) }

    fun setAiEnabled(enabled: Boolean) = intent { settingsRepository.setAiEnabled(enabled) }

    /** Stores the key in the Keystore vault; only "is a key present" is kept in memory. */
    fun saveApiKey(raw: String) {
        if (raw.isBlank()) return
        apiKeyVault.save(raw)
        flashKeySaved()
    }

    fun clearApiKey() {
        apiKeyVault.clear()
        flashKeySaved()
    }

    private fun flashKeySaved() {
        viewModelScope.launch {
            vaultState.value = VaultState(apiKeyVault.hasKey(), saved = true)
            delay(4_000)
            vaultState.value = VaultState(apiKeyVault.hasKey(), saved = false)
        }
    }

    fun clearCachedReleaseData() = intent {
        settingsRepository.clearCachedReleaseData()
        cacheCleared.value = true
        delay(4_000)
        cacheCleared.value = false
    }

    private data class VaultState(val hasKey: Boolean, val saved: Boolean)

    private fun intent(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
