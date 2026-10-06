package com.noise.appscout.domain.repository

import com.noise.appscout.domain.model.InstalledApp
import kotlinx.coroutines.flow.Flow

/** Reads the launcher visible applications installed on this device. */
interface InstalledAppRepository {

    /** Emits the current snapshot; call [refresh] to re-read from `PackageManager`. */
    fun observeInstalledApps(): Flow<List<InstalledApp>>

    /** Re-reads installed applications. Must never run on the main thread. */
    suspend fun refresh()

    suspend fun getInstalledApp(packageName: String): InstalledApp?
}
