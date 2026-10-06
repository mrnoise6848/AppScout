package com.noise.appscout.domain.usecase

import com.noise.appscout.domain.repository.RefreshOutcome
import com.noise.appscout.domain.repository.ReleaseRepository
import com.noise.appscout.domain.repository.SettingsRepository
import com.noise.appscout.domain.repository.TrackedAppRepository
import javax.inject.Inject

/** Refreshes a single tracked app; used by "Check now" on App Details. */
class RefreshAppUseCase @Inject constructor(
    private val trackedAppRepository: TrackedAppRepository,
    private val releaseRepository: ReleaseRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(trackedAppId: String): RefreshOutcome? {
        val app = trackedAppRepository.getTrackedApp(trackedAppId) ?: return null
        val source = app.source ?: return null
        val settings = settingsRepository.getSettings()
        return releaseRepository.refresh(source, settings.includePrereleases)
    }
}
