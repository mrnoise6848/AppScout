package com.noise.appscout.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noise.appscout.R
import com.noise.appscout.ui.theme.AppScoutTheme

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onNotificationsChange = viewModel::setNotificationsEnabled,
        onBackgroundRefreshChange = viewModel::setBackgroundRefreshEnabled,
        onIncludePrereleasesChange = viewModel::setIncludePrereleases,
        onAiEnabledChange = viewModel::setAiEnabled,
        onSaveApiKey = viewModel::saveApiKey,
        onClearApiKey = viewModel::clearApiKey,
        onClearCache = viewModel::clearCachedReleaseData,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onBackgroundRefreshChange: (Boolean) -> Unit,
    onIncludePrereleasesChange: (Boolean) -> Unit,
    onAiEnabledChange: (Boolean) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    val keySavedMessage = stringResource(R.string.settings_api_key_saved)
    val keyClearedMessage = stringResource(R.string.settings_api_key_cleared)
    val snackbarHostState = remember { SnackbarHostState() }
    val cacheClearedMessage = stringResource(R.string.settings_clear_cache_done)

    LaunchedEffect(state.cacheCleared) {
        if (state.cacheCleared) snackbarHostState.showSnackbar(cacheClearedMessage)
    }

    LaunchedEffect(state.apiKeySaved) {
        if (state.apiKeySaved) {
            snackbarHostState.showSnackbar(
                if (state.hasStoredApiKey) keySavedMessage else keyClearedMessage,
            )
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_section_notifications))
            SwitchRow(
                title = stringResource(R.string.settings_notifications),
                description = stringResource(R.string.settings_notifications_description),
                checked = state.settings.notificationsEnabled,
                onCheckedChange = onNotificationsChange,
            )

            SectionTitle(stringResource(R.string.settings_section_refresh))
            SwitchRow(
                title = stringResource(R.string.settings_background_refresh),
                description = stringResource(R.string.settings_background_refresh_description),
                checked = state.settings.backgroundRefreshEnabled,
                onCheckedChange = onBackgroundRefreshChange,
            )
            SwitchRow(
                title = stringResource(R.string.settings_include_prereleases),
                description = stringResource(R.string.settings_include_prereleases_description),
                checked = state.settings.includePrereleases,
                onCheckedChange = onIncludePrereleasesChange,
            )

            SectionTitle(stringResource(R.string.settings_section_ai))
            SwitchRow(
                title = stringResource(R.string.settings_ai_enabled),
                description = stringResource(R.string.settings_ai_enabled_description),
                checked = state.settings.aiEnabled,
                onCheckedChange = onAiEnabledChange,
            )
            Text(
                text = stringResource(
                    if (state.hasStoredApiKey) R.string.settings_api_key_present
                    else R.string.settings_api_key_missing,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = state.settings.aiEnabled,
                label = { Text(stringResource(R.string.settings_api_key_label)) },
                placeholder = { Text(stringResource(R.string.settings_api_key_placeholder)) },
                supportingText = {
                    Text(stringResource(R.string.settings_api_key_hint))
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSaveApiKey(apiKeyInput)
                        apiKeyInput = ""
                    },
                    enabled = state.settings.aiEnabled && apiKeyInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_api_key_save))
                }
                OutlinedButton(
                    onClick = {
                        onClearApiKey()
                        apiKeyInput = ""
                    },
                    enabled = state.hasStoredApiKey,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_api_key_clear))
                }
            }

            SectionTitle(stringResource(R.string.settings_section_data))
            TextButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_clear_cache))
            }
            Text(
                text = stringResource(R.string.settings_clear_cache_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_about))
            Text(
                text = stringResource(R.string.settings_version, rememberAppVersion()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    AppScoutTheme {
        SettingsScreen(
            state = SettingsUiState(),
            onBack = {},
            onNotificationsChange = {},
            onBackgroundRefreshChange = {},
            onIncludePrereleasesChange = {},
            onAiEnabledChange = {},
            onSaveApiKey = {},
            onClearApiKey = {},
            onClearCache = {},
        )
    }
}
