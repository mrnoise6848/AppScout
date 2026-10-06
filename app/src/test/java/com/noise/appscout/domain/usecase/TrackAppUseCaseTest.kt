package com.noise.appscout.domain.usecase

import com.noise.appscout.data.remote.github.GitHubSourceUrlParser
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
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance for Phase 3 — the source is validated before anything is persisted. */
class TrackAppUseCaseTest {

    private val installed = InstalledApp(
        packageName = "org.mozilla.firefox",
        appName = "Firefox",
        versionName = "1.9.0",
        versionCode = 190,
    )

    private fun useCase(
        fetchResult: FetchResult,
        installedApp: InstalledApp? = installed,
    ): Pair<TrackAppUseCase, FakeTrackedAppRepository> {
        val tracked = FakeTrackedAppRepository()
        val useCase = TrackAppUseCase(
            sourceUrlParser = GitHubSourceUrlParser(),
            installedAppRepository = FakeInstalledAppRepository(installedApp),
            trackedAppRepository = tracked,
            releaseRepository = FakeReleaseRepository(fetchResult),
            settingsRepository = FakeSettingsRepository(),
        )
        return useCase to tracked
    }

    @Test
    fun `valid url with reachable repository tracks the app`() = runTest {
        val (useCase, tracked) = useCase(FetchResult.Fetched(fakeRelease(), "etag"))

        val result = useCase("org.mozilla.firefox", "https://github.com/mozilla-mobile/firefox-android")

        assertTrue(result is TrackAppResult.Success)
        assertEquals(1, tracked.tracked.size)
        assertEquals("org.mozilla.firefox", tracked.tracked.first().packageName)
    }

    @Test
    fun `invalid url is rejected and nothing is persisted`() = runTest {
        val (useCase, tracked) = useCase(FetchResult.Fetched(fakeRelease(), "etag"))

        val result = useCase("org.mozilla.firefox", "https://gitlab.com/owner/repo")

        assertEquals(TrackAppResult.InvalidSourceUrl, result)
        assertTrue(tracked.tracked.isEmpty())
    }

    @Test
    fun `source failure is surfaced and nothing is persisted`() = runTest {
        val (useCase, tracked) = useCase(FetchResult.Failed(SourceErrorReason.NOT_FOUND))

        val result = useCase("org.mozilla.firefox", "https://github.com/owner/repo")

        assertTrue(result is TrackAppResult.SourceError)
        assertEquals(SourceErrorReason.NOT_FOUND, (result as TrackAppResult.SourceError).reason)
        assertTrue(tracked.tracked.isEmpty())
    }

    @Test
    fun `uninstalled package is reported as app not found`() = runTest {
        val (useCase, tracked) = useCase(
            fetchResult = FetchResult.Fetched(fakeRelease(), "etag"),
            installedApp = null,
        )

        val result = useCase("com.gone.app", "https://github.com/owner/repo")

        assertEquals(TrackAppResult.AppNotFound, result)
        assertTrue(tracked.tracked.isEmpty())
    }

    private fun fakeRelease() = Release(
        id = "r1",
        sourceId = AppSource.githubId("owner", "repo"),
        tagName = "v2.0.0",
        normalizedVersion = "2.0.0",
        title = "Release 2.0.0",
        body = null,
        htmlUrl = "https://github.com/owner/repo/releases/tag/v2.0.0",
        publishedAt = 0L,
        isPrerelease = false,
        isDraft = false,
        assets = emptyList(),
        fetchedAt = 0L,
    )

    private class FakeInstalledAppRepository(
        private val app: InstalledApp?,
    ) : InstalledAppRepository {
        override fun observeInstalledApps(): Flow<List<InstalledApp>> = flowOf(listOfNotNull(app))
        override suspend fun refresh() = Unit
        override suspend fun getInstalledApp(packageName: String): InstalledApp? =
            app?.takeIf { it.packageName == packageName }
    }

    private class FakeTrackedAppRepository : TrackedAppRepository {
        val tracked = mutableListOf<TrackedApp>()
        override fun observeTrackedApps(): Flow<List<TrackedApp>> = flowOf(tracked.toList())
        override suspend fun getTrackedApp(id: String): TrackedApp? = tracked.firstOrNull { it.id == id }
        override suspend fun getTrackedAppByPackage(packageName: String): TrackedApp? =
            tracked.firstOrNull { it.packageName == packageName }

        override suspend fun track(installedApp: InstalledApp, source: AppSource): TrackedApp {
            val app = TrackedApp(
                id = "tracked-${installedApp.packageName}",
                packageName = installedApp.packageName,
                appName = installedApp.appName,
                installedVersionName = installedApp.versionName,
                installedVersionCode = installedApp.versionCode,
                source = source,
                enabled = true,
                createdAt = 0L,
                updatedAt = 0L,
                lastNotifiedTag = null,
            )
            tracked += app
            return app
        }

        override suspend fun stopTracking(trackedAppId: String) {
            tracked.removeAll { it.id == trackedAppId }
        }

        override suspend fun markNotified(trackedAppId: String, tag: String) = Unit
        override suspend fun syncInstalledVersions(installedApps: List<InstalledApp>) = Unit
    }

    private class FakeReleaseRepository(
        private val fetchResult: FetchResult,
    ) : ReleaseRepository {
        override fun observeSnapshots(): Flow<List<TrackedAppSnapshot>> = flowOf(emptyList())
        override fun observeSnapshot(trackedAppId: String): Flow<TrackedAppSnapshot?> = flowOf(null)
        override suspend fun getSnapshot(trackedAppId: String): TrackedAppSnapshot? = null
        override suspend fun fetch(source: AppSource, includePrerelease: Boolean): FetchResult = fetchResult
        override suspend fun persist(source: AppSource, result: FetchResult) = Unit

        override suspend fun refresh(source: AppSource, includePrerelease: Boolean): RefreshOutcome =
            RefreshOutcome(source.id, null, error = null)

        override suspend fun getCachedRelease(sourceId: String): Release? = null
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
}
