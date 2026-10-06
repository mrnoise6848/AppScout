package com.noise.appscout.data.repository

import com.noise.appscout.core.common.TimeProvider
import com.noise.appscout.core.database.dao.SourceDao
import com.noise.appscout.core.database.dao.TrackedAppDao
import com.noise.appscout.core.database.entity.SourceEntity
import com.noise.appscout.core.database.entity.TrackedAppEntity
import com.noise.appscout.data.mapper.toDomain
import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.InstalledApp
import com.noise.appscout.domain.model.TrackedApp
import com.noise.appscout.domain.repository.TrackedAppRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrackedAppRepositoryImpl @Inject constructor(
    private val trackedAppDao: TrackedAppDao,
    private val sourceDao: SourceDao,
    private val timeProvider: TimeProvider,
) : TrackedAppRepository {

    override fun observeTrackedApps(): Flow<List<TrackedApp>> =
        trackedAppDao.observeWithRelations().map { rows -> rows.map { it.app.toDomain(it.source) } }

    override suspend fun getTrackedApp(id: String): TrackedApp? {
        val entity = trackedAppDao.getById(id) ?: return null
        return entity.toDomain(entity.sourceId?.let { sourceDao.getById(it) })
    }

    override suspend fun getTrackedAppByPackage(packageName: String): TrackedApp? {
        val entity = trackedAppDao.getByPackageName(packageName) ?: return null
        return entity.toDomain(entity.sourceId?.let { sourceDao.getById(it) })
    }

    override suspend fun track(
        installedApp: InstalledApp,
        source: AppSource,
    ): TrackedApp {
        val now = timeProvider.nowMillis()
        val existing = trackedAppDao.getByPackageName(installedApp.packageName)

        sourceDao.upsert(
            SourceEntity(
                id = source.id,
                type = source.type.name,
                owner = source.owner,
                repository = source.repository,
                url = source.url,
                createdAt = now,
            ),
        )

        // Re-pointing an app at a different repository drops the previous source and its cache.
        val previousSourceId = existing?.sourceId
        if (previousSourceId != null && previousSourceId != source.id) {
            sourceDao.deleteById(previousSourceId)
        }

        val currentSource = sourceDao.getById(source.id)
        val entity = TrackedAppEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            packageName = installedApp.packageName,
            appName = installedApp.appName,
            installedVersionName = installedApp.versionName,
            installedVersionCode = installedApp.versionCode,
            sourceId = source.id,
            enabled = existing?.enabled ?: true,
            lastNotifiedTag = existing?.lastNotifiedTag,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        trackedAppDao.upsert(entity)
        return entity.toDomain(currentSource)
    }

    override suspend fun stopTracking(trackedAppId: String) {
        val entity = trackedAppDao.getById(trackedAppId) ?: return
        trackedAppDao.delete(entity)
        // Deleting the source cascades to its releases and AI summaries.
        entity.sourceId?.let { sourceDao.deleteById(it) }
    }

    override suspend fun markNotified(trackedAppId: String, tag: String) {
        trackedAppDao.markNotified(trackedAppId, tag, timeProvider.nowMillis())
    }

    override suspend fun syncInstalledVersions(installedApps: List<InstalledApp>) {
        if (installedApps.isEmpty()) return
        val live = installedApps.associateBy { it.packageName }
        val now = timeProvider.nowMillis()

        for (tracked in trackedAppDao.getAll()) {
            val installed = live[tracked.packageName] ?: continue
            if (
                tracked.installedVersionName == installed.versionName &&
                tracked.installedVersionCode == installed.versionCode
            ) {
                continue
            }
            trackedAppDao.upsert(
                tracked.copy(
                    installedVersionName = installed.versionName,
                    installedVersionCode = installed.versionCode,
                    updatedAt = now,
                ),
            )
        }
    }
}
