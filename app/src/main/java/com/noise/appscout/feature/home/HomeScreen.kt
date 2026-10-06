package com.noise.appscout.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noise.appscout.R
import com.noise.appscout.core.ui.components.AppIcon
import com.noise.appscout.core.ui.components.StatusPill
import com.noise.appscout.domain.model.ReleaseStatus
import com.noise.appscout.domain.usecase.TrackedAppStatus
import com.noise.appscout.ui.theme.AppScoutTheme

/**
 * Home dashboard.
 *
 * Stateless: the screen renders [HomeUiState] and reports user intent back through callbacks, so
 * it can be previewed and tested without Hilt, Room or a network stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null until the Add App destination is registered; the affordances are hidden, not stubbed. */
    onAddApp: (() -> Unit)? = null,
    /** Attached together with the App Details destination; null keeps the row inert. */
    onAppClick: ((String) -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_open_settings),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> LoadingState()

                state.isEmpty -> EmptyState(onAddApp = onAddApp)

                else -> TrackedAppList(state = state, onAddApp = onAddApp, onAppClick = onAppClick)
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.cd_checking_for_updates)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = loadingDescription },
        )
    }
}

@Composable
private fun EmptyState(
    onAddApp: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onAddApp != null) {
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(onClick = onAddApp) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.action_add_app))
            }
        }
    }
}

@Composable
private fun TrackedAppList(
    state: HomeUiState,
    onAddApp: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onAppClick: ((String) -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.refreshFailed) {
            item(key = "offline") { OfflineNotice() }
        }

        if (state.updateCount > 0) {
            item(key = "header-updates") {
                SectionHeader(
                    text = pluralStringResource(
                        R.plurals.home_updates_count,
                        state.updateCount,
                        state.updateCount,
                    ),
                )
            }
            items(state.updates, key = { it.snapshot.app.id }) { status -> AppRow(status, onAppClick = onAppClick) }
        }

        if (state.upToDate.isNotEmpty()) {
            item(key = "header-uptodate") {
                SectionHeader(text = stringResource(R.string.home_section_up_to_date))
            }
            items(state.upToDate, key = { it.snapshot.app.id }) { status -> AppRow(status, onAppClick = onAppClick) }
        }

        if (state.needsAttention.isNotEmpty()) {
            item(key = "header-attention") {
                SectionHeader(text = stringResource(R.string.home_section_needs_attention))
            }
            items(state.needsAttention, key = { it.snapshot.app.id }) { status -> AppRow(status, onAppClick = onAppClick) }
        }

        if (onAddApp != null) {
            item(key = "add") { AddAppRow(onAddApp = onAddApp) }
        }
    }
}

@Composable
private fun OfflineNotice(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.home_offline_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
    )
    HorizontalDivider()
}

@Composable
private fun AppRow(
    status: TrackedAppStatus,
    modifier: Modifier = Modifier,
    onAppClick: ((String) -> Unit)? = null,
) {
    val check = status.check
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onAppClick != null) Modifier.clickable { onAppClick(status.snapshot.app.id) } else Modifier)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            packageName = status.snapshot.app.packageName,
            label = status.displayName,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            val installed = check.installedVersion
            val latest = check.release?.tagName
            Text(
                text = when {
                    installed != null && latest != null ->
                        stringResource(R.string.version_change, installed, latest)
                    latest != null -> stringResource(R.string.version_latest, latest)
                    else -> status.snapshot.app.packageName
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        StatusPill(status = check.status, stale = check.isStale)
    }
}

@Composable
private fun AddAppRow(onAddApp: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onAddApp)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.action_add_app),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeEmptyPreview() {
    AppScoutTheme {
        HomeScreen(
            state = HomeUiState(isLoading = false),
            onRefresh = {},
            onOpenSettings = {},
            onAddApp = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeStatusPreview() {
    AppScoutTheme {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                updates = listOf(previewStatus("Firefox", ReleaseStatus.UPDATE_AVAILABLE)),
                upToDate = listOf(previewStatus("VLC", ReleaseStatus.UP_TO_DATE)),
                needsAttention = listOf(previewStatus("Signal", ReleaseStatus.SOURCE_ERROR)),
            ),
            onRefresh = {},
            onOpenSettings = {},
            onAddApp = {},
        )
    }
}
