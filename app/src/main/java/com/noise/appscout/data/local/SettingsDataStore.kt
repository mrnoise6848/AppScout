package com.noise.appscout.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.noise.appscout.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "appscout_settings")

/** Preferences backed by Jetpack DataStore. Holds no secrets: the Gemini key is stored securely. */
class SettingsDataStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: AppSettings.DEFAULT.notificationsEnabled,
            backgroundRefreshEnabled = prefs[KEY_BACKGROUND_REFRESH] ?: AppSettings.DEFAULT.backgroundRefreshEnabled,
            aiEnabled = prefs[KEY_AI_ENABLED] ?: AppSettings.DEFAULT.aiEnabled,
            includePrereleases = prefs[KEY_PRERELEASES] ?: AppSettings.DEFAULT.includePrereleases,
        )
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) = edit(KEY_NOTIFICATIONS, enabled)

    suspend fun setBackgroundRefreshEnabled(enabled: Boolean) = edit(KEY_BACKGROUND_REFRESH, enabled)

    suspend fun setAiEnabled(enabled: Boolean) = edit(KEY_AI_ENABLED, enabled)

    suspend fun setIncludePrereleases(enabled: Boolean) = edit(KEY_PRERELEASES, enabled)

    private suspend fun edit(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val KEY_BACKGROUND_REFRESH = booleanPreferencesKey("background_refresh_enabled")
        val KEY_AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val KEY_PRERELEASES = booleanPreferencesKey("include_prereleases")
    }
}
