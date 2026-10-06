package com.noise.appscout.domain.release

import com.noise.appscout.domain.model.ReleaseCheck
import com.noise.appscout.domain.model.ReleaseStatus
import com.noise.appscout.domain.model.SourceErrorReason
import com.noise.appscout.domain.repository.TrackedAppSnapshot
import com.noise.appscout.domain.version.VersionComparator
import com.noise.appscout.domain.version.VersionComparison

/**
 * Pure, side-effect free mapping from cached data to a [ReleaseCheck].
 *
 * Rules:
 * * an app that is no longer installed is reported as [ReleaseStatus.NOT_INSTALLED]
 * * without a cached release and with a failed refresh the result is [ReleaseStatus.SOURCE_ERROR]
 * * when the last refresh failed the cached decision is kept but marked stale
 * * the comparator decides; if it cannot prove anything the result is
 *   [ReleaseStatus.VERSION_COMPARISON_UNCERTAIN], never a false update
 */
object ReleaseStatusResolver {

    fun resolve(
        isInstalled: Boolean,
        installedVersion: String?,
        snapshot: TrackedAppSnapshot,
    ): ReleaseCheck {
        val release = snapshot.latestRelease
        val error = snapshot.lastError
        val stale = error != null

        if (!isInstalled) {
            return ReleaseCheck.notInstalled(installedVersion).copy(isStale = stale, checkedAt = snapshot.lastCheckAt)
        }

        if (release == null) {
            return ReleaseCheck(
                status = ReleaseStatus.SOURCE_ERROR,
                installedVersion = installedVersion,
                release = null,
                errorReason = error ?: SourceErrorReason.NO_RELEASES,
                isStale = stale,
                checkedAt = snapshot.lastCheckAt,
            )
        }

        if (installedVersion.isNullOrBlank()) {
            return ReleaseCheck(
                status = ReleaseStatus.VERSION_COMPARISON_UNCERTAIN,
                installedVersion = installedVersion,
                release = release,
                errorReason = error,
                isStale = stale,
                checkedAt = snapshot.lastCheckAt,
            )
        }

        val status = when (VersionComparator.compare(installedVersion, release.tagName)) {
            VersionComparison.UNCERTAIN -> ReleaseStatus.VERSION_COMPARISON_UNCERTAIN
            // The installed version is older than the published release.
            VersionComparison.LOWER -> ReleaseStatus.UPDATE_AVAILABLE
            // Up to date, or the installed build is newer than the published one.
            VersionComparison.EQUAL, VersionComparison.GREATER -> ReleaseStatus.UP_TO_DATE
        }

        return ReleaseCheck(
            status = status,
            installedVersion = installedVersion,
            release = release,
            errorReason = error,
            isStale = stale,
            checkedAt = snapshot.lastCheckAt,
        )
    }
}
