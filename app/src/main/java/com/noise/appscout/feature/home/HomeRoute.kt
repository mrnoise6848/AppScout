package com.noise.appscout.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Thin Hilt-aware wrapper around the stateless [HomeScreen].
 *
 * Keeping ViewModel wiring out of the screen makes the UI previewable and lets instrumentation
 * tests drive [HomeScreen] with plain data.
 */
@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onAddApp: (() -> Unit)? = null,
    onAppClick: ((String) -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onOpenSettings = onOpenSettings,
        onAddApp = onAddApp,
        onAppClick = onAppClick,
        modifier = modifier,
    )
}
