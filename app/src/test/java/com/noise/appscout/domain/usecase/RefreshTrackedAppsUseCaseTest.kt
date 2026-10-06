package com.noise.appscout.domain.usecase

import com.noise.appscout.domain.model.AppSettings
import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.InstalledApp
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.SourceErrorReason
import com.noise.appscout.domain.model.TrackedApp
import com.noise.appscout.domain.repository.FetchResult
import com.noise.appscout.domain.repository.InstalledAppRepository
import com.noise.appscout.domain.repository.RefreshOutcome
import com.noise.appscout.domain.repository.ReleaseRepository
import com.noise.appscout.domain.repository.SettingsRepository
import com.noise.appscout.domain.repository.TrackedAppRepository
import com.noise.appscout.domain.repository.TrackedAppSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour of the background refresh pipeline (spec §25):
 * successful run saves releases, an offline run keeps the cache and never throws.
 */
class RefreshTrackedAppsUseCaseTest {

    private val source = AppSource(
        id = AppSource.githubId("owner", "repo"),
        type = com.noise.appscout.domain.model.SourceType.GITHUB,
        owner = "owner",
        repository = "repo",
        url = AppSource.githubUrl("owner", "repo"),
    )

    private val installed = InstalledApp(
        packageName = "org.mozilla.firefox",
        appName = "Firefox",
        versionName = "1.9.0",
        versionCode = 190,
    )

    private val tracked = TrackedApp(
        id = "app-1",
        packageName = installed.packageName,
        appName = installed.appName,
        installedVersionName = installed.versionName,
        installedVersionCode = installed.versionCode,
        source = source,
        enabled = true,
        createdAt = 0L,
        updatedAt = 0L,
        lastNotifiedTag = null,
    )

    private fun release(tag: String) = Release(
        id = "r-$tag",
        sourceId = source.id,
        tagName = tag,
        normalizedVersion = tag.removePrefix("v"),
        title = tag,
        body = null,
        htmlUrl = null,
        publishedAt = 1L,
        isPrerelease = false,
        isDraft = false,
        assets = emptyList(),
        fetchedAt = 1L,
    )

    private class FakeTrackedAppRepository(private val app: TrackedApp) : TrackedAppRepository {
        var syncedInstalled: List<InstalledApp>? = null
            private set
        override fun observeTrackedApps(): Flow<List<TrackedApp>> = flowOf(listOf(app))
        override suspend fun getTrackedApp(id: String): TrackedApp? = app
        override suspend fun getTrackedAppByPackage(packageName: String): TrackedApp? = app
        override suspend fun track(installedApp: InstalledApp, source: AppSource): TrackedApp = app
        override suspend fun stopTracking(trackedAppId: String) = Unit
        override suspend fun markNotified(trackedAppId: String, tag: String) = Unit
        override suspend fun syncInstalledVersions(installedApps: List<InstalledApp>) {
            syncedInstalled = installedApps
        }
    }

    /** In-memory stand-in for the Room cache: survives failed refreshes untouched. */
    private class FakeReleaseRepository(
        private val app: TrackedApp,
        private val initialCachedRelease: Release?,
        private val fetchResult: () -> FetchResult,
    ) : ReleaseRepository {
        var cachedRelease: Release? = initialCachedRelease
        var lastError: SourceErrorReason? = null
        var persistCount = 0
            private set

        override fun observeSnapshots(): Flow<List<TrackedAppSnapshot>> = flowOf(
            listOf(
                TrackedAppSnapshot(
                    app = app,
                    latestRelease = cachedRelease,
                    lastCheckAt = 1L,
                    lastError = lastError,
                ),
            ),
        )

        override fun observeSnapshot(trackedAppId: String): Flow<TrackedAppSnapshot?> = flowOf(null)

        override suspend fun getSnapshot(trackedAppId: String): TrackedAppSnapshot? = null

        override suspend fun fetch(source: AppSource, includePrerelease: Boolean): FetchResult =
            fetchResult()

        override suspend fun persist(source: AppSource, result: FetchResult) {
            persistCount++
            when (result) {
                is FetchResult.Fetched -> {
                    cachedRelease = result.release
                    lastError = null
                }
                FetchResult.Cached -> lastError = null
                is FetchResult.Failed -> lastError = result.error
            }
        }

        override suspend fun refresh(source: AppSource, includePrerelease: Boolean): RefreshOutcome {
            val fetched = fetchResult()
            persist(source, fetched)
            return RefreshOutcome(source.id, cachedRelease, error = lastError)
        }

        override suspend fun getCachedRelease(sourceId: String): Release? = cachedRelease
    }

    private class FakeInstalledAppRepository(private val app: InstalledApp) : InstalledAppRepository {
        override fun observeInstalledApps(): Flow<List<InstalledApp>> = flowOf(listOf(app))
        override suspend fun refresh() = Unit
        override suspend fun getInstalledApp(packageName: String): InstalledApp? = app
    }

    private class FakeSettingsRepository : SettingsRepository {
        override fun observeSettings(): Flow<AppSettings> = flowOf(AppSettings.DEFAULT)
        override suspend fun getSettings(): AppSettings = AppSettings.DEFAULT
        override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun setBackgroundRefreshEnabled(enabled: Boolean) = Unit
        override suspend fun setAiEnabled(enabled: Boolean) = Unit
        override suspend fun setIncludePrereleases(enabled: Boolean) = Unit
        override suspend fun clearCachedReleaseData() = Unit
    }

    private fun useCase(
        fetch: () -> FetchResult,
        app: TrackedApp = tracked,
    ): Triple<RefreshTrackedAppsUseCase, FakeReleaseRepository, FakeTrackedAppRepository> {
        val releases = FakeReleaseRepository(app, release("v1.5.0"), fetch)
        val trackedRepo = FakeTrackedAppRepository(app)
        val useCase = RefreshTrackedAppsUseCase(
            installedAppRepository = FakeInstalledAppRepository(installed),
            trackedAppRepository = trackedRepo,
            releaseRepository = releases,
            settingsRepository = FakeSettingsRepository(),
        )
        return Triple(useCase, releases, trackedRepo)
    }

    @Test
    fun `successful refresh fetches and saves the release`() = runTest {
        val (useCase, releases, trackedRepo) = useCase(fetch = { FetchResult.Fetched(release("v2.0.0"), null) })

        val outcomes = useCase()

        assertEquals(1, outcomes.size)
        assertTrue(outcomes.first().isSuccess)
        assertEquals("v2.0.0", releases.cachedRelease?.tagName)
        assertEquals(1, releases.persistCount)
        // installed versions are re-synced from PackageManager on every run
        assertNotNull(trackedRepo.syncedInstalled)
        assertEquals(installed.packageName, trackedRepo.syncedInstalled!!.first().packageName)
    }

    @Test
    fun `offline refresh keeps the cache and surfaces the error`() = runTest {
        val (useCase, releases, _) = useCase(fetch = { FetchResult.Failed(SourceErrorReason.NETWORK) })
        val cachedBefore = releases.cachedRelease

        val outcomes = useCase()

        assertEquals(1, outcomes.size)
        assertEquals(SourceErrorReason.NETWORK, outcomes.first().error)
        // cached state preserved
        assertEquals(cachedBefore, releases.cachedRelease)
        assertEquals(SourceErrorReason.NETWORK, releases.lastError)
        assertEquals(1, releases.persistCount)
    }

    @Test
    fun `rate limited refresh is a normal outcome, not a crash`() = runTest {
        val (useCase, releases, _) = useCase(fetch = { FetchResult.Failed(SourceErrorReason.RATE_LIMITED) })

        val outcomes = useCase()

        assertTrue(outcomes.first().error == SourceErrorReason.RATE_LIMITED)
        assertNotNull(releases.cachedRelease)
    }

    @Test
    fun `app without a source is skipped without failing the run`() = runTest {
        val unattached = tracked.copy(source = null)
        val (useCase, releases, _) = useCase(
            fetch = { FetchResult.Fetched(release("v2.0.0"), null) },
            app = unattached,
        )

        val outcomes = useCase()

        // nothing to fetch for an app with no attached source, and the run still succeeds
        assertEquals(0, outcomes.size)
        assertNull(releases.lastError)
        assertEquals(0, releases.persistCount)
    }
}
