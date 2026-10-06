package com.noise.appscout.domain.usecase

import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.SourceErrorReason
import com.noise.appscout.domain.repository.FetchResult
import com.noise.appscout.domain.repository.InstalledAppRepository
import com.noise.appscout.domain.repository.ReleaseRepository
import com.noise.appscout.domain.repository.SettingsRepository
import com.noise.appscout.domain.repository.TrackedAppRepository
import com.noise.appscout.domain.model.TrackedApp
import javax.inject.Inject

sealed interface TrackAppResult {
    data class Success(val trackedApp: TrackedApp) : TrackAppResult
    data object InvalidSourceUrl : TrackAppResult
    data object AppNotFound : TrackAppResult
    data class SourceError(val reason: SourceErrorReason) : TrackAppResult
}

/**
 * Attaches a GitHub repository to an installed app.
 *
 * The repository is validated with a real request *before* anything is written, so an invalid or
 * unreachable source never leaves a half-configured tracked app behind.
 */
class TrackAppUseCase @Inject constructor(
    private val sourceUrlParser: com.noise.appscout.domain.model.SourceUrlParser,
    private val installedAppRepository: InstalledAppRepository,
    private val trackedAppRepository: TrackedAppRepository,
    private val releaseRepository: ReleaseRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend operator fun invoke(packageName: String, repositoryUrl: String): TrackAppResult {
        val installed = installedAppRepository.getInstalledApp(packageName)
            ?: return TrackAppResult.AppNotFound

        val parsed = sourceUrlParser.parse(repositoryUrl) ?: return TrackAppResult.InvalidSourceUrl
        val source = AppSource(
            id = AppSource.githubId(parsed.owner, parsed.repository),
            type = parsed.type,
            owner = parsed.owner,
            repository = parsed.repository,
            url = parsed.url,
        )

        val settings = settingsRepository.getSettings()
        val fetched = releaseRepository.fetch(source, settings.includePrereleases)
        if (fetched is FetchResult.Failed) return TrackAppResult.SourceError(fetched.error)

        val tracked = trackedAppRepository.track(installed, source)
        releaseRepository.persist(source, fetched)
        return TrackAppResult.Success(tracked)
    }
}
