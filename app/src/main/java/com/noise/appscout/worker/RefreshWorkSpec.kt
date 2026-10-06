package com.noise.appscout.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import com.noise.appscout.core.network.NetworkConfig
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for the background-refresh contract.
 *
 * Keeping the unique work name and the interval here means "schedule again" can never drift into
 * duplicate work: every caller uses the same name with [ExistingPeriodicWorkPolicy.KEEP].
 */
object RefreshWorkSpec {

    const val WORK_NAME = "release_refresh"

    /** Android controls the exact execution time; 12 h is the shortest interval we accept. */
    const val INTERVAL_HOURS = 12L

    const val MAX_RUN_ATTEMPTS = 3

    val existingPolicy: ExistingPeriodicWorkPolicy get() = ExistingPeriodicWorkPolicy.KEEP

    fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    inline fun <reified W : androidx.work.ListenableWorker> request(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<W>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints())
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                NetworkConfig.INITIAL_BACKOFF_MS,
                TimeUnit.MILLISECONDS,
            )
            .build()
}
