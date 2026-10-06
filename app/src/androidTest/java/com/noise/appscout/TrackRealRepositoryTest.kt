package com.noise.appscout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.noise.appscout.core.common.SystemTimeProvider
import com.noise.appscout.core.database.AppScoutDatabase
import com.noise.appscout.data.local.PackageManagerInstalledAppRepository
import com.noise.appscout.data.remote.github.GitHubApi
import com.noise.appscout.data.remote.github.GitHubReleaseDataSource
import com.noise.appscout.data.remote.github.GitHubSourceUrlParser
import com.noise.appscout.data.repository.ReleaseRepositoryImpl
import com.noise.appscout.data.repository.TrackedAppRepositoryImpl
import com.noise.appscout.domain.repository.InstalledAppRepository
import com.noise.appscout.domain.repository.ReleaseRepository
import com.noise.appscout.domain.repository.SettingsRepository
import com.noise.appscout.domain.repository.TrackedAppRepository
import com.noise.appscout.domain.usecase.TrackAppResult
import com.noise.appscout.domain.usecase.TrackAppUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json

/**
 * Phase 3 acceptance, exercised end to end on a real device with the real GitHub API:
 * *a real public GitHub repository attached to a real installed app.*
 *
 * The repository under test is the app itself (always installed, always launcher visible).
 */
@RunWith(AndroidJUnit4::class)
class TrackRealRepositoryTest {

    private lateinit var database: AppScoutDatabase
    private lateinit var installedApps: InstalledAppRepository
    private lateinit var trackedApps: TrackedAppRepository
    private lateinit var releases: ReleaseRepository
    private lateinit var useCase: TrackAppUseCase

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppScoutDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val time = SystemTimeProvider()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val api = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)

        val dataSource = GitHubReleaseDataSource(api, time, Dispatchers.IO)

        installedApps = PackageManagerInstalledAppRepository(context, Dispatchers.IO)
        trackedApps = TrackedAppRepositoryImpl(database.trackedAppDao(), database.sourceDao(), time)
        releases = ReleaseRepositoryImpl(
            dataSource = dataSource,
            trackedAppDao = database.trackedAppDao(),
            sourceDao = database.sourceDao(),
            releaseDao = database.releaseDao(),
            timeProvider = time,
        )

        useCase = TrackAppUseCase(
            sourceUrlParser = GitHubSourceUrlParser(),
            installedAppRepository = installedApps,
            trackedAppRepository = trackedApps,
            releaseRepository = releases,
            settingsRepository = FakeSettings,
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            runCatching { trackedApps.getTrackedAppByPackage(context.packageName)?.let { trackedApps.stopTracking(it.id) } }
        }
        database.close()
    }

    @Test
    fun realPublicRepositoryAttachesToRealInstalledApp() = runBlocking {
        // 1. a real installed app (this very app) must be discoverable
        val installed = withTimeout(30_000) {
            installedApps.getInstalledApp(context.packageName)
        }
        assertNotNull("app under test must be installed", installed)
        assertEquals(context.packageName, installed!!.packageName)
        assertNotNull("version must be readable", installed.versionName)

        // 2. attach a real, public GitHub repository that publishes releases
        val result = withTimeout(60_000) {
            useCase(context.packageName, "https://github.com/square/okhttp")
        }

        assertTrue("expected success, got $result", result is TrackAppResult.Success)

        // 3. the tracked app is persisted
        val persisted = trackedApps.getTrackedAppByPackage(context.packageName)
        assertNotNull(persisted)
        assertEquals("square", persisted!!.source?.owner)
        assertEquals("okhttp", persisted.source?.repository)

        // 4. a real release was fetched and cached locally
        val snapshot = withTimeout(10_000) {
            releases.observeSnapshots().first { list -> list.any { it.app.id == persisted.id } }
        }.first { it.app.id == persisted.id }

        val release = snapshot.latestRelease
        assertNotNull("release must be cached", release)
        assertTrue("tag must not be blank", release!!.tagName.isNotBlank())
        assertEquals("square/okhttp source id", persisted.source?.id, release.sourceId)
        assertNullError(snapshot)

        // 5. repeated tracking of the same package is idempotent (single record)
        val again = useCase(context.packageName, "https://github.com/square/okhttp")
        assertTrue(again is TrackAppResult.Success)
        val all = trackedApps.observeTrackedApps().first().filter { it.packageName == context.packageName }
        assertEquals("tracking must stay idempotent", 1, all.size)
    }

    private fun assertNullError(snapshot: com.noise.appscout.domain.repository.TrackedAppSnapshot) {
        assertEquals("no error expected for a healthy fetch", null, snapshot.lastError)
    }

    private object FakeSettings : SettingsRepository {
        override fun observeSettings() =
            kotlinx.coroutines.flow.flowOf(com.noise.appscout.domain.model.AppSettings.DEFAULT)

        override suspend fun getSettings() = com.noise.appscout.domain.model.AppSettings.DEFAULT
        override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun setBackgroundRefreshEnabled(enabled: Boolean) = Unit
        override suspend fun setAiEnabled(enabled: Boolean) = Unit
        override suspend fun setIncludePrereleases(enabled: Boolean) = Unit
        override suspend fun clearCachedReleaseData() = Unit
    }
}
