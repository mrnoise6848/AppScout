package com.noise.appscout.domain.usecase

import com.noise.appscout.domain.repository.TrackedAppRepository
import javax.inject.Inject

/** Stops tracking an app and drops everything cached for it. */
class StopTrackingUseCase @Inject constructor(
    private val trackedAppRepository: TrackedAppRepository,
) {
    suspend operator fun invoke(trackedAppId: String) = trackedAppRepository.stopTracking(trackedAppId)
}
