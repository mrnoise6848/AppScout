package com.noise.appscout.domain.usecase

import com.noise.appscout.domain.model.AppSettings
import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.InstalledApp
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.TrackedApp
import com.noise.appscout.domain.repository.FetchResult
import com.noise.appscout.domain.repository.InstalledAppRepository
import com.noise.appscout.domain.repository.RefreshOutcome
import com.noise.appscout.domain.repository.ReleaseRepository
import com.noise.appscout.domain.repository.SettingsRepository
import com.noise.appscout.domain.repository.TrackedAppRepository
import com.noise.appscout.domain.repository.TrackedAppSnapshot
import com.noise.appscout.domain.service.ReleaseNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance for spec §26 — notification behaviour: notify once, never twice, honours settings. */
class NotifyNewReleasesUseCaseTest {

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

    private fun release(tag: String) = Release(
        id = "r-$tag",
        sourceId = source.id,
        tagName = tag,
        normalizedVersion = tag.removePrefix("v"),
        title = tag,
        body = null,
        htmlUrl = "https://github.com/owner/repo/releases/tag/$tag",
        publishedAt = 1_700_000_000_000L,
        isPrerelease = false,
        isDraft = false,
        assets = emptyList(),
        fetchedAt = 1_700_000_000_000L,
    )

    /** A tracked app whose cached release is [tag]; [lastNotifiedTag] models the dedupe record. */
    private fun trackedApp(tag: String?, lastNotifiedTag: String? = null) = TrackedApp(
        id = "app-1",
        packageName = installed.packageName,
        appName = installed.appName,
        installedVersionName = installed.versionName,
        installedVersionCode = installed.versionCode,
        source = source,
        enabled = true,
        createdAt = 0L,
        updatedAt = 0L,
        lastNotifiedTag = lastNotifiedTag,
    )

    private class FakeNotifier : ReleaseNotifier {
        val notifications = mutableListOf<Pair<String, String>>() // app id → tag
        override fun notifyNewRelease(app: TrackedApp, release: Release, installedVersion: String?) {
            notifications += app.id to release.tagName
        }
    }

    private fun build(
        app: TrackedApp,
        latestTag: String?,
        notificationsEnabled: Boolean = true,
        installedApp: InstalledApp = installed,
        notifier: FakeNotifier = FakeNotifier(),
    ): Triple<NotifyNewReleasesUseCase, FakeTrackedAppRepository, FakeNotifier> {
        val trackedRepo = FakeTrackedAppRepository(app)
        val settings = FakeSettings(notificationsEnabled)
        val observe = ObserveTrackedAppStatusesUseCase(
            trackedAppRepository = trackedRepo,
            installedAppRepository = FakeInstalledAppRepository(installedApp),
            // Mirrors Room: the snapshot always reflects the *current* tracked app row, so a
            // `markNotified` write is visible to the next collection.
            releaseRepository = FakeReleaseRepository(
                snapshotOf = {
                    TrackedAppSnapshot(
                        app = trackedRepo.app,
                        latestRelease = latestTag?.let { release(it) },
                        lastCheckAt = 1_700_000_000_000L,
                        lastError = null,
                    )
                },
            ),
        )
        val useCase = NotifyNewReleasesUseCase(
            observeStatuses = observe,
            trackedAppRepository = trackedRepo,
            settingsRepository = settings,
            releaseNotifier = notifier,
        )
        return Triple(useCase, trackedRepo, notifier)
    }

    @Test
    fun `new release triggers a notification and records the tag`() = runTest {
        val (useCase, trackedRepo, notifier) = build(trackedApp("v1.9.0"), latestTag = "v2.0.0")

        val posted = useCase()

        assertEquals(1, posted)
        assertEquals(listOf("app-1" to "v2.0.0"), notifier.notifications)
        assertEquals("v2.0.0", trackedRepo.app.lastNotifiedTag)
    }

    @Test
    fun `same release never notifies twice`() = runTest {
        val (useCase, trackedRepo, notifier) = build(trackedApp("v1.9.0"), latestTag = "v2.0.0")

        assertEquals(1, useCase())
        // second run: the recorded tag now matches the cached release
        val second = useCase()

        assertEquals(0, second)
        assertEquals(1, notifier.notifications.size)
        assertEquals("v2.0.0", trackedRepo.app.lastNotifiedTag)
    }

    @Test
    fun `notifications can be disabled`() = runTest {
        val (useCase, _, notifier) = build(
            app = trackedApp("v1.9.0"),
            latestTag = "v2.0.0",
            notificationsEnabled = false,
        )

        assertEquals(0, useCase())
        assertTrue(notifier.notifications.isEmpty())
    }

    @Test
    fun `up to date app is not notified`() = runTest {
        val upToDate = installed.copy(versionName = "2.0.0", versionCode = 200)
        val (useCase, _, notifier) = build(
            app = trackedApp("v2.0.0").copy(installedVersionName = "2.0.0"),
            latestTag = "v2.0.0",
            installedApp = upToDate,
        )

        assertEquals(0, useCase())
        assertTrue(notifier.notifications.isEmpty())
    }

    @Test
    fun `app without cached release is not notified`() = runTest {
        val (useCase, _, notifier) = build(trackedApp("v1.9.0"), latestTag = null)

        assertEquals(0, useCase())
        assertTrue(notifier.notifications.isEmpty())
    }

    // --- fakes -----------------------------------------------------------------------------

    private class FakeTrackedAppRepository(var app: TrackedApp) : TrackedAppRepository {
        override fun observeTrackedApps(): Flow<List<TrackedApp>> = MutableStateFlow(listOf(app))
        override suspend fun getTrackedApp(id: String): TrackedApp? = app.takeIf { it.id == id }
        override suspend fun getTrackedAppByPackage(packageName: String): TrackedApp? =
            app.takeIf { it.packageName == packageName }

        override suspend fun track(installedApp: InstalledApp, source: AppSource): TrackedApp = app

        override suspend fun stopTracking(trackedAppId: String) = Unit

        override suspend fun markNotified(trackedAppId: String, tag: String) {
            app = app.copy(lastNotifiedTag = tag)
        }

        override suspend fun syncInstalledVersions(installedApps: List<InstalledApp>) = Unit
    }

    private class FakeInstalledAppRepository(private val app: InstalledApp) : InstalledAppRepository {
        private val apps = MutableStateFlow(listOf(app))
        override fun observeInstalledApps(): Flow<List<InstalledApp>> = apps
        override suspend fun refresh() = Unit
        override suspend fun getInstalledApp(packageName: String): InstalledApp? =
            apps.value.firstOrNull { it.packageName == packageName }
    }

    private class FakeReleaseRepository(private val snapshotOf: () -> TrackedAppSnapshot) : ReleaseRepository {
        override fun observeSnapshots(): Flow<List<TrackedAppSnapshot>> = flowOf(listOf(snapshotOf()))
        override fun observeSnapshot(trackedAppId: String): Flow<TrackedAppSnapshot?> = flowOf(snapshotOf())
        override suspend fun getSnapshot(trackedAppId: String): TrackedAppSnapshot? = snapshotOf()
        override suspend fun fetch(source: AppSource, includePrerelease: Boolean): FetchResult =
            FetchResult.Fetched(snapshotOf().latestRelease!!, null)

        override suspend fun persist(source: AppSource, result: FetchResult) = Unit
        override suspend fun refresh(source: AppSource, includePrerelease: Boolean): RefreshOutcome =
            RefreshOutcome(source.id, snapshotOf().latestRelease, error = null)

        override suspend fun getCachedRelease(sourceId: String): Release? = snapshotOf().latestRelease
    }

    private class FakeSettings(private val notificationsEnabled: Boolean) : SettingsRepository {
        override fun observeSettings(): Flow<AppSettings> =
            flowOf(AppSettings(notificationsEnabled = notificationsEnabled))

        override suspend fun getSettings(): AppSettings =
            AppSettings(notificationsEnabled = notificationsEnabled)

        override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun setBackgroundRefreshEnabled(enabled: Boolean) = Unit
        override suspend fun setAiEnabled(enabled: Boolean) = Unit
        override suspend fun setIncludePrereleases(enabled: Boolean) = Unit
        override suspend fun clearCachedReleaseData() = Unit
    }
}
