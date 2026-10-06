package com.noise.appscout.domain.repository

import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.InstalledApp
import com.noise.appscout.domain.model.TrackedApp
import kotlinx.coroutines.flow.Flow

/** Persistence for the apps the user chose to monitor. */
interface TrackedAppRepository {

    fun observeTrackedApps(): Flow<List<TrackedApp>>

    suspend fun getTrackedApp(id: String): TrackedApp?

    suspend fun getTrackedAppByPackage(packageName: String): TrackedApp?

    /**
     * Starts tracking [installedApp] against [source]. Re-running for an already tracked package
     * updates the existing record and re-points it at [source].
     *
     * @return the persisted tracked app.
     */
    suspend fun track(installedApp: InstalledApp, source: AppSource): TrackedApp

    /** Stops tracking and removes everything cached for that app. */
    suspend fun stopTracking(trackedAppId: String)

    /** Records that the user was notified about [tag]; used to avoid duplicate notifications. */
    suspend fun markNotified(trackedAppId: String, tag: String)

    /** Refreshes the cached installed versions from a live [PackageManager] snapshot. */
    suspend fun syncInstalledVersions(installedApps: List<InstalledApp>)
}
