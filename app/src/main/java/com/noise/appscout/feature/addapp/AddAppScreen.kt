package com.noise.appscout.feature.addapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noise.appscout.R
import com.noise.appscout.core.ui.components.AppIcon
import com.noise.appscout.domain.model.SourceErrorReason

@Composable
fun AddAppRoute(
    onBack: () -> Unit,
    onTracked: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddAppViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSuccess, state.trackedAppId) {
        val trackedAppId = state.trackedAppId
        if (state.isSuccess && trackedAppId != null) {
            onTracked(trackedAppId)
        }
    }

    AddAppScreen(
        state = state,
        onRepositoryUrlChange = viewModel::onRepositoryUrlChange,
        onTrack = viewModel::validateAndTrack,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppScreen(
    state: AddAppUiState,
    onRepositoryUrlChange: (String) -> Unit,
    onTrack: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_app_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            state.installedApp?.let { app ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppIcon(
                        packageName = app.packageName,
                        label = app.appName,
                    )
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Installed: ${app.versionName ?: "-"} (${app.versionCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.add_app_source_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedTextField(
                    value = state.repositoryUrl,
                    onValueChange = onRepositoryUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.add_app_source_hint)) },
                    placeholder = { Text("https://github.com/owner/repo") },
                    enabled = !state.isValidating,
                    singleLine = true,
                    isError = state.errorMessage != null || state.sourceError != null,
                )

                if (state.isValidating) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.add_app_validating),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                state.sourceError?.let { reason ->
                    val msg = when (reason) {
                        SourceErrorReason.NETWORK -> stringResource(R.string.error_network)
                        SourceErrorReason.NOT_FOUND -> stringResource(R.string.error_not_found)
                        SourceErrorReason.NO_RELEASES -> stringResource(R.string.error_no_releases)
                        SourceErrorReason.RATE_LIMITED -> stringResource(R.string.error_rate_limited)
                        SourceErrorReason.SERVER_ERROR -> stringResource(R.string.error_server)
                        SourceErrorReason.MALFORMED_RESPONSE -> stringResource(R.string.error_malformed)
                        SourceErrorReason.UNKNOWN -> stringResource(R.string.error_unknown)
                    }
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Button(
                onClick = onTrack,
                enabled = !state.isValidating && state.installedApp != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_app_action_track))
            }

            if (state.isSuccess) {
                Text(
                    text = stringResource(R.string.add_app_success, state.installedApp?.appName ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
