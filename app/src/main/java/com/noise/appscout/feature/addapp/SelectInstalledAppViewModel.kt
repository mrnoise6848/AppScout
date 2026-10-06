package com.noise.appscout.feature.addapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noise.appscout.core.common.IoDispatcher
import com.noise.appscout.domain.model.InstalledApp
import com.noise.appscout.domain.repository.InstalledAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SelectInstalledAppUiState(
    val apps: List<InstalledApp> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
) {
    val filteredApps: List<InstalledApp> = if (query.isBlank()) {
        apps
    } else {
        val lower = query.lowercase()
        apps.filter { app ->
            app.appName.lowercase().contains(lower) || app.packageName.lowercase().contains(lower)
        }
    }
}

@HiltViewModel
class SelectInstalledAppViewModel @Inject constructor(
    private val installedAppRepository: InstalledAppRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val isLoading = MutableStateFlow(true)

    val state: StateFlow<SelectInstalledAppUiState> = combine(
        installedAppRepository.observeInstalledApps(),
        query,
        isLoading,
    ) { appsList, q, loading ->
        SelectInstalledAppUiState(
            apps = appsList,
            query = q,
            isLoading = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SelectInstalledAppUiState())

    init {
        refresh()
    }

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun refresh() = viewModelScope.launch {
        isLoading.value = true
        try {
            withContext(io) { installedAppRepository.refresh() }
        } finally {
            isLoading.value = false
        }
    }
}
