package com.noise.appscout.domain.repository

import com.noise.appscout.domain.model.AppSettings
import com.noise.appscout.domain.model.AiSummary
import com.noise.appscout.domain.model.Release
import kotlinx.coroutines.flow.Flow

/** User preferences persisted locally. */
interface SettingsRepository {

    fun observeSettings(): Flow<AppSettings>

    suspend fun getSettings(): AppSettings

    suspend fun setNotificationsEnabled(enabled: Boolean)

    suspend fun setBackgroundRefreshEnabled(enabled: Boolean)

    suspend fun setAiEnabled(enabled: Boolean)

    suspend fun setIncludePrereleases(enabled: Boolean)

    /** Deletes all cached release information (release notes, statuses, summaries). */
    suspend fun clearCachedReleaseData()
}

/** Caches structured AI output per release. */
interface AiSummaryRepository {

    fun observeSummary(releaseId: String): Flow<AiSummary?>

    suspend fun getSummary(releaseId: String): AiSummary?

    suspend fun saveSummary(releaseId: String, summary: AiSummary)

    suspend fun deleteSummary(releaseId: String)
}
