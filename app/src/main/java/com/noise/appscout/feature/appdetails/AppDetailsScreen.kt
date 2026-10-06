package com.noise.appscout.feature.appdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noise.appscout.R
import com.noise.appscout.core.ui.components.AppIcon
import com.noise.appscout.core.ui.components.StatusPill
import com.noise.appscout.core.ui.components.rememberDateFormatter
import com.noise.appscout.domain.model.ReleaseStatus
import com.noise.appscout.domain.usecase.TrackedAppStatus

@Composable
fun AppDetailsRoute(
    onBack: () -> Unit,
    onOpenReleaseDetails: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isStopped) {
        if (state.isStopped) onBack()
    }

    AppDetailsScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onStopTracking = viewModel::onStopTrackingConfirmed,
        onOpenRelease = onOpenReleaseDetails,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    state: AppDetailsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStopTracking: () -> Unit,
    onOpenRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStopDialog by remember { mutableStateOf(false) }
    val formatDate = rememberDateFormatter()
    val status = state.status

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(status?.displayName ?: stringResource(R.string.app_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_check_now),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (status == null) {
            // Tracked app vanished (stopped elsewhere) — close the screen.
            LaunchedEffect(Unit) { onBack() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    packageName = status.snapshot.app.packageName,
                    label = status.displayName,
                    modifier = Modifier.size(56.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = status.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = status.snapshot.app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(status = status.check.status, stale = status.check.isStale)
            }

            HorizontalDivider()

            DetailRow(
                label = stringResource(R.string.app_details_status),
                value = statusValue(status, stringResource(R.string.status_update_available)),
            )
            DetailRow(
                label = stringResource(R.string.version_installed),
                value = status.check.installedVersion ?: "—",
            )
            DetailRow(
                label = stringResource(R.string.app_details_latest_release),
                value = status.check.release?.tagName
                    ?: stringResource(R.string.app_details_no_release),
            )
            DetailRow(
                label = stringResource(R.string.app_details_source),
                value = status.snapshot.app.source?.let { "${it.owner}/${it.repository}" } ?: "—",
            )
            DetailRow(
                label = stringResource(R.string.app_details_last_checked_line),
                value = status.check.checkedAt?.let { formatDate(it) }
                    ?: stringResource(R.string.app_details_never_checked),
            )

            HorizontalDivider()

            if (status.check.release != null) {
                OutlinedButton(
                    onClick = onOpenRelease,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.action_open_release))
                }
            }

            OutlinedButton(
                onClick = { showStopDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.action_stop_tracking))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = {
                Text(
                    stringResource(
                        R.string.stop_tracking_confirm_title,
                        status?.displayName ?: "",
                    ),
                )
            },
            text = { Text(stringResource(R.string.stop_tracking_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopDialog = false
                        onStopTracking()
                    },
                ) {
                    Text(
                        stringResource(R.string.action_stop_tracking),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun statusValue(status: TrackedAppStatus, updateAvailableLabel: String): String =
    when (status.check.status) {
        ReleaseStatus.UPDATE_AVAILABLE -> updateAvailableLabel
        ReleaseStatus.UP_TO_DATE -> stringResource(R.string.status_up_to_date)
        ReleaseStatus.VERSION_COMPARISON_UNCERTAIN -> stringResource(R.string.status_version_uncertain)
        ReleaseStatus.SOURCE_ERROR -> stringResource(R.string.status_source_error)
        ReleaseStatus.NOT_INSTALLED -> stringResource(R.string.status_not_installed)
    }

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
    }
}
