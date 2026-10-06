package com.noise.appscout.data.repository

import com.noise.appscout.core.database.dao.AiSummaryDao
import com.noise.appscout.core.database.entity.AiSummaryEntity
import com.noise.appscout.domain.model.AiSummary
import com.noise.appscout.domain.model.UpdateImportance
import com.noise.appscout.domain.repository.AiSummaryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AiSummaryRepositoryImpl @Inject constructor(
    private val dao: AiSummaryDao,
    private val json: Json,
) : AiSummaryRepository {

    override fun observeSummary(releaseId: String): Flow<AiSummary?> =
        dao.observeForRelease(releaseId).map { it?.toDomain() }

    override suspend fun getSummary(releaseId: String): AiSummary? =
        dao.getForRelease(releaseId)?.toDomain()

    override suspend fun saveSummary(releaseId: String, summary: AiSummary) {
        dao.upsert(
            AiSummaryEntity(
                releaseId = releaseId,
                summary = summary.summary,
                importance = summary.importance.name,
                reasonsJson = json.encodeToString(summary.reasons),
                securityRelated = summary.securityRelated,
                breakingChangePossible = summary.breakingChangePossible,
                model = summary.model,
                createdAt = summary.generatedAt,
            ),
        )
    }

    override suspend fun deleteSummary(releaseId: String) = dao.deleteForRelease(releaseId)

    private fun AiSummaryEntity.toDomain(): AiSummary = AiSummary(
        summary = summary,
        importance = UpdateImportance.fromRaw(importance),
        reasons = runCatching { json.decodeFromString<List<String>>(reasonsJson) }.getOrDefault(emptyList()),
        securityRelated = securityRelated,
        breakingChangePossible = breakingChangePossible,
        model = model,
        generatedAt = createdAt,
    )
}
