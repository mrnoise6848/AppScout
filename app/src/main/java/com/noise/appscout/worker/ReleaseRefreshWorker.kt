package com.noise.appscout.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noise.appscout.domain.repository.SettingsRepository
import com.noise.appscout.domain.usecase.NotifyNewReleasesUseCase
import com.noise.appscout.domain.usecase.RefreshTrackedAppsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Periodic release refresh.
 *
 * Responsibilities (spec §13): load tracked apps → refresh releases → update the local database →
 * detect newly available releases → notify. It never touches Compose UI; the database is the only
 * channel back to the user.
 *
 * Runs the same use cases as pull-to-refresh so foreground and background behaviour cannot drift.
 */
@HiltWorker
class ReleaseRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val refreshTrackedApps: RefreshTrackedAppsUseCase,
    private val notifyNewReleases: NotifyNewReleasesUseCase,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val enabled = runCatching { settingsRepository.getSettings().backgroundRefreshEnabled }
            .getOrDefault(false)
        if (!enabled) return Result.success()

        return try {
            // Per-source failures are already captured as SourceErrorReason and never throw,
            // so the cached state stays intact when the network is unavailable.
            refreshTrackedApps()
            // Notification failures must never fail the refresh run.
            runCatching { notifyNewReleases() }
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (runAttemptCount < RefreshWorkSpec.MAX_RUN_ATTEMPTS) Result.retry()
            else Result.success()
        }
    }
}
