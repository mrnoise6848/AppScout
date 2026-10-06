package com.noise.appscout.domain.usecase

import com.noise.appscout.domain.model.ReleaseCheck
import com.noise.appscout.domain.model.InstalledApp
import com.noise.appscout.domain.release.ReleaseStatusResolver
import com.noise.appscout.domain.repository.InstalledAppRepository
import com.noise.appscout.domain.repository.ReleaseRepository
import com.noise.appscout.domain.repository.RefreshOutcome
import com.noise.appscout.domain.repository.SettingsRepository
import com.noise.appscout.domain.repository.TrackedAppRepository
import com.noise.appscout.domain.repository.TrackedAppSnapshot
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/** A tracked app resolved against the device state. */
data class TrackedAppStatus(
    val snapshot: TrackedAppSnapshot,
    val installed: InstalledApp?,
    val check: ReleaseCheck,
) {
    /** Stable display name for lists and notifications. */
    val displayName: String get() = snapshot.app.appName
}

/** Streams every tracked app together with its up-to-date release status. */
class ObserveTrackedAppStatusesUseCase @Inject constructor(
    private val trackedAppRepository: TrackedAppRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val releaseRepository: ReleaseRepository,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<TrackedAppStatus>> = flow {
        installedAppRepository.refresh()
        emitAll(
            combine(
                releaseRepository.observeSnapshots(),
                installedAppRepository.observeInstalledApps(),
            ) { snapshots, installedApps ->
                val installedByPackage = installedApps.associateBy { it.packageName }
                snapshots.map { snapshot ->
                    val installed = installedByPackage[snapshot.app.packageName]
                    TrackedAppStatus(
                        snapshot = snapshot,
                        installed = installed,
                        check = ReleaseStatusResolver.resolve(
                            isInstalled = installed != null,
                            installedVersion = installed?.versionName
                                ?: snapshot.app.installedVersionName,
                            snapshot = snapshot,
                        ),
                    )
                }
            },
        )
    }
}

/** Fetches fresh releases for every tracked app; used by the worker and by pull-to-refresh. */
class RefreshTrackedAppsUseCase @Inject constructor(
    private val installedAppRepository: InstalledAppRepository,
    private val trackedAppRepository: TrackedAppRepository,
    private val releaseRepository: ReleaseRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend operator fun invoke(): List<RefreshOutcome> {
        installedAppRepository.refresh()
        val installed = installedAppRepository.observeInstalledApps().first()
        trackedAppRepository.syncInstalledVersions(installed)

        val settings = settingsRepository.getSettings()
        val tracked = trackedAppRepository.observeTrackedApps().first()

        return tracked.mapNotNull { app ->
            val source = app.source ?: return@mapNotNull null
            releaseRepository.refresh(source, settings.includePrereleases)
        }
    }
}
