package com.noise.appscout.data.repository

import com.noise.appscout.core.database.dao.AiSummaryDao
import com.noise.appscout.core.database.dao.ReleaseDao
import com.noise.appscout.core.database.dao.SourceDao
import com.noise.appscout.data.local.SettingsDataStore
import com.noise.appscout.domain.model.AppSettings
import com.noise.appscout.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore,
    private val releaseDao: ReleaseDao,
    private val aiSummaryDao: AiSummaryDao,
    private val sourceDao: SourceDao,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> = dataStore.settings

    override suspend fun getSettings(): AppSettings = dataStore.settings.first()

    override suspend fun setNotificationsEnabled(enabled: Boolean) =
        dataStore.setNotificationsEnabled(enabled)

    override suspend fun setBackgroundRefreshEnabled(enabled: Boolean) =
        dataStore.setBackgroundRefreshEnabled(enabled)

    override suspend fun setAiEnabled(enabled: Boolean) = dataStore.setAiEnabled(enabled)

    override suspend fun setIncludePrereleases(enabled: Boolean) =
        dataStore.setIncludePrereleases(enabled)

    override suspend fun clearCachedReleaseData() {
        aiSummaryDao.clear()
        releaseDao.clear()
        // Conditional requests must be dropped together with the cache, otherwise GitHub would
        // answer 304 for content we no longer have.
        for (source in sourceDao.getAll()) {
            sourceDao.upsert(source.copy(etag = null, lastCheckAt = null, lastError = null))
        }
    }
}
