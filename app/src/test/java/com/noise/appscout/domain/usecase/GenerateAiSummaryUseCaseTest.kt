package com.noise.appscout.domain.usecase

import com.noise.appscout.ai.FakeAiSummaryProvider
import com.noise.appscout.domain.ai.AiSummaryResult
import com.noise.appscout.domain.model.AiSummary
import com.noise.appscout.domain.model.AppSettings
import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.UpdateImportance
import com.noise.appscout.domain.repository.AiSummaryRepository
import com.noise.appscout.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7 acceptance — AI stays optional and advisory:
 * valid response, malformed response, no key and the evidence constraint (spec §23).
 */
class GenerateAiSummaryUseCaseTest {

    private val release = Release(
        id = "source#v1.2.3",
        sourceId = AppSource.githubId("owner", "repo"),
        tagName = "v1.2.3",
        normalizedVersion = "1.2.3",
        title = "Version 1.2.3",
        body = "Fixed crash when opening settings.\nImproved startup performance.",
        htmlUrl = "https://github.com/owner/repo/releases/tag/v1.2.3",
        publishedAt = 1L,
        isPrerelease = false,
        isDraft = false,
        assets = emptyList(),
        fetchedAt = 1L,
    )

    private class FakeAiSummaryRepository : AiSummaryRepository {
        val stored = mutableMapOf<String, AiSummary>()
        override fun observeSummary(releaseId: String): Flow<AiSummary?> = flowOf(stored[releaseId])
        override suspend fun getSummary(releaseId: String): AiSummary? = stored[releaseId]
        override suspend fun saveSummary(releaseId: String, summary: AiSummary) {
            stored[releaseId] = summary
        }

        override suspend fun deleteSummary(releaseId: String) {
            stored.remove(releaseId)
        }
    }

    private class FakeSettings(private val aiEnabled: Boolean) : SettingsRepository {
        override fun observeSettings(): Flow<AppSettings> = flowOf(AppSettings(aiEnabled = aiEnabled))
        override suspend fun getSettings(): AppSettings = AppSettings(aiEnabled = aiEnabled)
        override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun setBackgroundRefreshEnabled(enabled: Boolean) = Unit
        override suspend fun setAiEnabled(enabled: Boolean) = Unit
        override suspend fun setIncludePrereleases(enabled: Boolean) = Unit
        override suspend fun clearCachedReleaseData() = Unit
    }

    private fun build(
        provider: FakeAiSummaryProvider = FakeAiSummaryProvider(),
        aiEnabled: Boolean = true,
    ): Triple<GenerateAiSummaryUseCase, FakeAiSummaryRepository, FakeAiSummaryProvider> {
        val repository = FakeAiSummaryRepository()
        val useCase = GenerateAiSummaryUseCase(
            aiSummaryRepository = repository,
            settingsRepository = FakeSettings(aiEnabled),
            aiSummaryProvider = provider,
        )
        return Triple(useCase, repository, provider)
    }

    @Test
    fun `valid response is generated and cached`() = runTest {
        val (useCase, repository, provider) = build()

        val outcome = useCase(release, "Firefox")

        assertTrue(outcome is AiSummaryOutcome.Available)
        outcome as AiSummaryOutcome.Available
        assertTrue(outcome.generated)
        assertEquals(UpdateImportance.LOW, outcome.summary.importance)
        assertFalse(outcome.summary.securityRelated)
        assertEquals(1, provider.calls.size)
        assertEquals(outcome.summary, repository.stored[release.id])
    }

    @Test
    fun `second call reuses the cached summary without calling the provider`() = runTest {
        val (useCase, _, provider) = build()

        useCase(release, "Firefox")
        val second = useCase(release, "Firefox")

        assertTrue(second is AiSummaryOutcome.Available)
        assertFalse((second as AiSummaryOutcome.Available).generated)
        assertEquals(1, provider.calls.size)
    }

    @Test
    fun `malformed response surfaces as unavailable and keeps release data intact`() = runTest {
        val (useCase, repository, provider) = build()
        provider.result = AiSummaryResult.InvalidOutput

        val outcome = useCase(release, "Firefox")

        assertTrue(outcome is AiSummaryOutcome.Unavailable)
        assertEquals(AiSummaryResult.InvalidOutput, (outcome as AiSummaryOutcome.Unavailable).reason)
        // nothing cached, no crash, and the release itself is untouched
        assertTrue(repository.stored.isEmpty())
        assertEquals("Fixed crash when opening settings.\nImproved startup performance.", release.body)
    }

    @Test
    fun `missing api key blocks nothing but the summary`() = runTest {
        val (useCase, _, provider) = build()
        provider.result = AiSummaryResult.NoApiKey

        val outcome = useCase(release, "Firefox")

        assertTrue(outcome is AiSummaryOutcome.Unavailable)
        assertEquals(AiSummaryResult.NoApiKey, (outcome as AiSummaryOutcome.Unavailable).reason)
        // core data stays available
        assertEquals("v1.2.3", release.tagName)
        assertEquals(1, provider.calls.size)
    }

    @Test
    fun `disabled AI never reaches the provider`() = runTest {
        val (useCase, _, provider) = build(aiEnabled = false)

        val outcome = useCase(release, "Firefox")

        assertTrue(outcome is AiSummaryOutcome.Unavailable)
        assertEquals(AiSummaryResult.Disabled, (outcome as AiSummaryOutcome.Unavailable).reason)
        assertTrue(provider.calls.isEmpty())
    }

    @Test
    fun `force regenerates even when a cached summary exists`() = runTest {
        val (useCase, _, provider) = build()

        useCase(release, "Firefox")
        val forced = useCase(release, "Firefox", force = true)

        assertTrue((forced as AiSummaryOutcome.Available).generated)
        assertEquals(2, provider.calls.size)
    }
}
