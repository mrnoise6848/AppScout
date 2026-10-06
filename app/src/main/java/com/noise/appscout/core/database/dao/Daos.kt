package com.noise.appscout.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.noise.appscout.core.database.entity.AiSummaryEntity
import com.noise.appscout.core.database.entity.ReleaseEntity
import com.noise.appscout.core.database.entity.SourceEntity
import com.noise.appscout.core.database.entity.TrackedAppEntity
import com.noise.appscout.core.database.relation.TrackedAppRelations
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedAppDao {

    @Query("SELECT * FROM tracked_apps ORDER BY appName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrackedAppEntity>>

    @Transaction
    @Query("SELECT * FROM tracked_apps ORDER BY appName COLLATE NOCASE ASC")
    fun observeWithRelations(): Flow<List<TrackedAppRelations>>

    @Transaction
    @Query("SELECT * FROM tracked_apps WHERE id = :id")
    fun observeByIdWithRelations(id: String): Flow<TrackedAppRelations?>

    @Transaction
    @Query("SELECT * FROM tracked_apps WHERE id = :id")
    suspend fun getByIdWithRelations(id: String): TrackedAppRelations?

    @Query("SELECT * FROM tracked_apps ORDER BY appName COLLATE NOCASE ASC")
    suspend fun getAll(): List<TrackedAppEntity>

    @Query("SELECT * FROM tracked_apps WHERE id = :id")
    fun observeById(id: String): Flow<TrackedAppEntity?>

    @Query("SELECT * FROM tracked_apps WHERE id = :id")
    suspend fun getById(id: String): TrackedAppEntity?

    @Query("SELECT * FROM tracked_apps WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): TrackedAppEntity?

    @Query("SELECT * FROM tracked_apps WHERE sourceId = :sourceId")
    suspend fun getBySourceId(sourceId: String): TrackedAppEntity?

    @Upsert
    suspend fun upsert(app: TrackedAppEntity)

    @Delete
    suspend fun delete(app: TrackedAppEntity)

    @Query("DELETE FROM tracked_apps WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """
        UPDATE tracked_apps
        SET lastNotifiedTag = :tag, updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markNotified(id: String, tag: String, updatedAt: Long)

    @Transaction
    suspend fun upsertAll(apps: List<TrackedAppEntity>) = apps.forEach { upsert(it) }
}

@Dao
interface SourceDao {

    @Query("SELECT * FROM sources")
    suspend fun getAll(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun getById(id: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE owner = :owner AND repository = :repository")
    suspend fun getByOwnerRepo(owner: String, repository: String): SourceEntity?

    @Upsert
    suspend fun upsert(source: SourceEntity)

    @Delete
    suspend fun delete(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ReleaseDao {

    @Query("SELECT * FROM releases WHERE sourceId = :sourceId ORDER BY publishedAt DESC LIMIT 1")
    fun observeLatestForSource(sourceId: String): Flow<ReleaseEntity?>

    @Query("SELECT * FROM releases WHERE sourceId = :sourceId ORDER BY publishedAt DESC LIMIT 1")
    suspend fun getLatestForSource(sourceId: String): ReleaseEntity?

    @Query("SELECT * FROM releases WHERE id = :id")
    suspend fun getById(id: String): ReleaseEntity?

    @Query("SELECT * FROM releases WHERE sourceId = :sourceId ORDER BY publishedAt DESC")
    suspend fun getAllForSource(sourceId: String): List<ReleaseEntity>

    @Upsert
    suspend fun upsert(release: ReleaseEntity)

    @Query("DELETE FROM releases WHERE sourceId = :sourceId AND id != :keepId")
    suspend fun deleteStale(sourceId: String, keepId: String)

    @Query("DELETE FROM releases WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: String)

    @Query("DELETE FROM releases")
    suspend fun clear()
}

@Dao
interface AiSummaryDao {

    @Query("SELECT * FROM ai_summaries WHERE releaseId = :releaseId")
    fun observeForRelease(releaseId: String): Flow<AiSummaryEntity?>

    @Query("SELECT * FROM ai_summaries WHERE releaseId = :releaseId")
    suspend fun getForRelease(releaseId: String): AiSummaryEntity?

    @Upsert
    suspend fun upsert(summary: AiSummaryEntity)

    @Query("DELETE FROM ai_summaries WHERE releaseId = :releaseId")
    suspend fun deleteForRelease(releaseId: String)

    @Query("DELETE FROM ai_summaries")
    suspend fun clear()
}
