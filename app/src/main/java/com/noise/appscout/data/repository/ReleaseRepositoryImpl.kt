package com.noise.appscout.data.repository

import com.noise.appscout.core.common.TimeProvider
import com.noise.appscout.core.database.dao.ReleaseDao
import com.noise.appscout.core.database.dao.SourceDao
import com.noise.appscout.core.database.dao.TrackedAppDao
import com.noise.appscout.core.database.entity.SourceEntity
import com.noise.appscout.data.mapper.toDomain
import com.noise.appscout.data.mapper.toEntity
import com.noise.appscout.data.mapper.toSnapshot
import com.noise.appscout.data.remote.github.GitHubReleaseDataSource
import com.noise.appscout.data.remote.github.ReleaseFetchResult
import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.repository.FetchResult
import com.noise.appscout.domain.repository.RefreshOutcome
import com.noise.appscout.domain.repository.ReleaseRepository
import com.noise.appscout.domain.repository.TrackedAppSnapshot
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReleaseRepositoryImpl @Inject constructor(
    private val dataSource: GitHubReleaseDataSource,
    private val trackedAppDao: TrackedAppDao,
    private val sourceDao: SourceDao,
    private val releaseDao: ReleaseDao,
    private val timeProvider: TimeProvider,
) : ReleaseRepository {

    override fun observeSnapshots(): Flow<List<TrackedAppSnapshot>> =
        trackedAppDao.observeWithRelations().map { rows -> rows.map { it.toSnapshot() } }

    override fun observeSnapshot(trackedAppId: String): Flow<TrackedAppSnapshot?> =
        trackedAppDao.observeByIdWithRelations(trackedAppId).map { it?.toSnapshot() }

    override suspend fun getSnapshot(trackedAppId: String): TrackedAppSnapshot? =
        trackedAppDao.getByIdWithRelations(trackedAppId)?.toSnapshot()

    override suspend fun fetch(source: AppSource, includePrerelease: Boolean): FetchResult {
        val cachedEtag = sourceDao.getById(source.id)?.etag
        return when (val result = dataSource.fetchLatestRelease(source, cachedEtag, includePrerelease)) {
            is ReleaseFetchResult.Success -> FetchResult.Fetched(result.release, result.etag)
            is ReleaseFetchResult.NotModified -> FetchResult.Cached
            is ReleaseFetchResult.Failure -> FetchResult.Failed(result.reason)
        }
    }

    override suspend fun persist(source: AppSource, result: FetchResult) {
        val now = timeProvider.nowMillis()
        val existing = sourceDao.getById(source.id)
        val sourceEntity = existing ?: SourceEntity(
            id = source.id,
            type = source.type.name,
            owner = source.owner,
            repository = source.repository,
            url = source.url,
            createdAt = now,
        )

        when (result) {
            is FetchResult.Fetched -> {
                releaseDao.upsert(result.release.toEntity())
                releaseDao.deleteStale(source.id, result.release.id)
                sourceDao.upsert(
                    sourceEntity.copy(
                        etag = result.etag ?: sourceEntity.etag,
                        lastCheckAt = now,
                        lastError = null,
                    ),
                )
            }

            FetchResult.Cached -> {
                sourceDao.upsert(sourceEntity.copy(lastCheckAt = now, lastError = null))
            }

            is FetchResult.Failed -> {
                sourceDao.upsert(sourceEntity.copy(lastError = result.error.name))
            }
        }
    }

    override suspend fun refresh(source: AppSource, includePrerelease: Boolean): RefreshOutcome {
        val result = fetch(source, includePrerelease)
        persist(source, result)

        return when (result) {
            is FetchResult.Fetched -> RefreshOutcome(source.id, result.release, error = null)
            FetchResult.Cached -> RefreshOutcome(
                sourceId = source.id,
                release = getCachedRelease(source.id),
                error = null,
                wasNotModified = true,
            )

            is FetchResult.Failed -> RefreshOutcome(
                sourceId = source.id,
                release = getCachedRelease(source.id),
                error = result.error,
            )
        }
    }

    override suspend fun getCachedRelease(sourceId: String): Release? =
        releaseDao.getLatestForSource(sourceId)?.toDomain()
}
