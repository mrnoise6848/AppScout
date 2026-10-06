package com.noise.appscout.domain.usecase

import com.noise.appscout.domain.model.ReleaseStatus
import com.noise.appscout.domain.repository.ReleaseRepository
import com.noise.appscout.domain.repository.SettingsRepository
import com.noise.appscout.domain.repository.TrackedAppRepository
import com.noise.appscout.domain.service.ReleaseNotifier
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Notifies about newly published releases exactly once per release tag.
 *
 * Rules (all must hold before a notification is posted):
 * 1. notifications are enabled in settings
 * 2. there is a cached release that is genuinely newer than what is installed
 * 3. the release tag differs from the tag the user was last notified about
 *
 * [TrackedAppRepository.markNotified] records the decision right after posting, so a repeated
 * run with unchanged data stays silent.
 */
class NotifyNewReleasesUseCase @Inject constructor(
    private val observeStatuses: ObserveTrackedAppStatusesUseCase,
    private val trackedAppRepository: TrackedAppRepository,
    private val settingsRepository: SettingsRepository,
    private val releaseNotifier: ReleaseNotifier,
) {

    /** @return how many notifications were posted. */
    suspend operator fun invoke(): Int {
        if (!settingsRepository.getSettings().notificationsEnabled) return 0

        var posted = 0
        val statuses = try {
            observeStatuses().first()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Without a readable state there is nothing safe to announce.
            return 0
        }

        for (status in statuses) {
            val release = status.check.release ?: continue
            if (status.check.status != ReleaseStatus.UPDATE_AVAILABLE) continue

            val app = status.snapshot.app
            if (app.lastNotifiedTag == release.tagName) continue

            val delivered = runCatching {
                releaseNotifier.notifyNewRelease(app, release, status.check.installedVersion)
            }.isSuccess
            if (!delivered) continue

            runCatching { trackedAppRepository.markNotified(app.id, release.tagName) }
            posted++
        }

        return posted
    }
}
