package com.noise.appscout.feature.releasedetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noise.appscout.R
import com.noise.appscout.core.ui.components.StatusPill
import com.noise.appscout.domain.model.UpdateImportance
import com.noise.appscout.core.ui.components.openInBrowser
import com.noise.appscout.core.ui.components.rememberDateFormatter

@Composable
fun ReleaseDetailsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReleaseDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReleaseDetailsScreen(
        state = state,
        onBack = onBack,
        onGenerateAi = { viewModel.generateAi(force = true) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseDetailsScreen(
    state: ReleaseDetailsUiState,
    onBack: () -> Unit,
    onGenerateAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formatDate = rememberDateFormatter()
    val loadingDescription = stringResource(R.string.ai_loading)
    val release = state.release

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.release_details_title)) },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (release == null && state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics { contentDescription = loadingDescription },
                    )
                }
                return@Column
            }

            if (release == null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.app_details_no_release),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Spacer(modifier = Modifier.height(4.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = release.tagName, style = MaterialTheme.typography.headlineSmall)
                release.title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = release.publishedAt?.let { stringResource(R.string.release_published, formatDate(it)) }
                        ?: stringResource(R.string.release_not_published),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (release.isPrerelease) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.release_prerelease)) },
                    )
                }
            }

            state.status?.let { status ->
                StatusPill(status = status.check.status, stale = status.check.isStale)
            }

            HorizontalDivider()

            AiSummarySection(
                aiEnabled = state.aiEnabled,
                ai = state.ai,
                onGenerate = onGenerateAi,
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.release_original_notes),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = release.body ?: stringResource(R.string.release_no_notes),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (release.assets.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.release_assets, release.assets.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                release.assets.forEach { asset ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = asset.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = formatBytes(asset.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { openInBrowser(context, release.htmlUrl) },
                enabled = !release.htmlUrl.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.action_open_github_release),
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val locale = java.util.Locale.getDefault()
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(locale, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(locale, "%.1f MB", mb)
    return String.format(locale, "%.1f GB", mb / 1024.0)
}


@Composable
private fun AiSummarySection(
    aiEnabled: Boolean,
    ai: AiUiState,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ai_summary_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.ai_generated_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            !aiEnabled -> Text(
                text = stringResource(R.string.ai_disabled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ai is AiUiState.Idle -> OutlinedButton(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_generate))
            }

            ai is AiUiState.Loading -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.ai_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ai is AiUiState.Failed -> {
                Text(
                    text = stringResource(aiErrorText(ai.reason)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ai_retry))
                }
            }

            ai is AiUiState.Ready -> {
                ai.summary.reasons.forEach { reason ->
                    Text(
                        text = "\u2022 $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = ai.summary.summary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(importanceLabel(ai.summary.importance)) },
                    )
                    if (ai.summary.securityRelated) {
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.ai_security_related)) },
                        )
                    }
                    if (ai.summary.breakingChangePossible) {
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.ai_breaking_possible)) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.ai_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun importanceLabel(importance: UpdateImportance): String = when (importance) {
    UpdateImportance.LOW -> stringResource(R.string.ai_importance_low)
    UpdateImportance.MEDIUM -> stringResource(R.string.ai_importance_medium)
    UpdateImportance.HIGH -> stringResource(R.string.ai_importance_high)
    UpdateImportance.UNKNOWN -> stringResource(R.string.ai_importance_unknown)
}

private fun aiErrorText(reason: com.noise.appscout.domain.ai.AiSummaryResult): Int = when (reason) {
    com.noise.appscout.domain.ai.AiSummaryResult.Disabled -> R.string.ai_error_disabled
    com.noise.appscout.domain.ai.AiSummaryResult.NoApiKey -> R.string.ai_error_no_key
    com.noise.appscout.domain.ai.AiSummaryResult.InvalidApiKey -> R.string.ai_error_invalid_key
    com.noise.appscout.domain.ai.AiSummaryResult.RateLimited -> R.string.ai_error_rate_limited
    com.noise.appscout.domain.ai.AiSummaryResult.NetworkError -> R.string.ai_error_network
    com.noise.appscout.domain.ai.AiSummaryResult.InvalidOutput -> R.string.ai_error_unavailable
    is com.noise.appscout.domain.ai.AiSummaryResult.Success -> R.string.ai_error_unavailable
}
