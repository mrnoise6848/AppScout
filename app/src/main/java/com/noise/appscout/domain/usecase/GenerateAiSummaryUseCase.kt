package com.noise.appscout.domain.usecase

import com.noise.appscout.domain.ai.AiSummaryProvider
import com.noise.appscout.domain.ai.AiSummaryResult
import com.noise.appscout.domain.model.AiSummary
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.repository.AiSummaryRepository
import com.noise.appscout.domain.repository.SettingsRepository
import javax.inject.Inject

/** UI-ready outcome of asking for an AI summary. */
sealed interface AiSummaryOutcome {

    data class Available(
        val summary: AiSummary,
        /** True when this run produced it instead of reading it from the cache. */
        val generated: Boolean,
    ) : AiSummaryOutcome

    /** Nothing to show, with the reason the UI already knows how to explain. */
    data class Unavailable(val reason: AiSummaryResult) : AiSummaryOutcome
}

/**
 * Generates (or reuses) the structured AI summary for a release.
 *
 * Order of operations keeps AI firmly optional (ADR-005):
 * disabled → cached → provider. Core screens only ever receive an [AiSummaryOutcome]; there is no
 * path where AI failure blocks release information.
 */
class GenerateAiSummaryUseCase @Inject constructor(
    private val aiSummaryRepository: AiSummaryRepository,
    private val settingsRepository: SettingsRepository,
    private val aiSummaryProvider: AiSummaryProvider,
) {

    suspend operator fun invoke(
        release: Release,
        appName: String,
        force: Boolean = false,
    ): AiSummaryOutcome {
        if (!settingsRepository.getSettings().aiEnabled) {
            return AiSummaryOutcome.Unavailable(AiSummaryResult.Disabled)
        }

        if (!force) {
            aiSummaryRepository.getSummary(release.id)?.let {
                return AiSummaryOutcome.Available(it, generated = false)
            }
        }

        return when (val result = aiSummaryProvider.summarize(release, appName)) {
            is AiSummaryResult.Success -> {
                aiSummaryRepository.saveSummary(release.id, result.summary)
                AiSummaryOutcome.Available(result.summary, generated = true)
            }
            AiSummaryResult.Disabled -> AiSummaryOutcome.Unavailable(AiSummaryResult.Disabled)
            else -> AiSummaryOutcome.Unavailable(result)
        }
    }
}
