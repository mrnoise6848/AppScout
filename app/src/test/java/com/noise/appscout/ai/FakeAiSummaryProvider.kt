package com.noise.appscout.ai

import com.noise.appscout.domain.ai.AiSummaryProvider
import com.noise.appscout.domain.ai.AiSummaryResult
import com.noise.appscout.domain.model.AiSummary
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.UpdateImportance

/**
 * Test double for [AiSummaryProvider] (spec §23).
 *
 * Lives in test sources only: production code must never contain a fake AI backend (spec §34).
 */
class FakeAiSummaryProvider(
    var result: AiSummaryResult = AiSummaryResult.Success(
        AiSummary(
            summary = "Fixes a settings crash and improves startup performance.",
            importance = UpdateImportance.LOW,
            reasons = listOf("Fixed crash when opening settings", "Improved startup performance"),
            securityRelated = false,
            breakingChangePossible = false,
            model = "fake-model",
            generatedAt = 1_700_000_000_000L,
        ),
    ),
) : AiSummaryProvider {

    val calls = mutableListOf<Pair<String, String>>() // release id → app name

    override suspend fun summarize(release: Release, appName: String): AiSummaryResult {
        calls += release.id to appName
        return result
    }
}
