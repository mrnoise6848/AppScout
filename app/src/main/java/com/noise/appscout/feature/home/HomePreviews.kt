package com.noise.appscout.feature.home

import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.ReleaseStatus
import com.noise.appscout.domain.model.TrackedApp
import com.noise.appscout.domain.release.ReleaseStatusResolver
import com.noise.appscout.domain.repository.TrackedAppSnapshot
import com.noise.appscout.domain.usecase.TrackedAppStatus

/**
 * Fixture for `@Preview` rendering only.
 *
 * Previews never touch production data paths; the same shapes the runtime produces are built by
 * hand here.
 */
internal fun previewStatus(
    appName: String,
    status: ReleaseStatus,
    installedVersion: String? = null,
    latestVersion: String? = null,
): TrackedAppStatus {
    // Derived from the requested status so previews always show what they claim to show.
    val installed = installedVersion ?: when (status) {
        ReleaseStatus.VERSION_COMPARISON_UNCERTAIN -> ""
        ReleaseStatus.UPDATE_AVAILABLE -> "1.2.0"
        else -> "1.3.0"
    }
    val latest = latestVersion ?: when (status) {
        ReleaseStatus.UPDATE_AVAILABLE -> "1.3.0"
        else -> "1.3.0"
    }
    val source = AppSource(
        id = AppSource.githubId("example", appName.lowercase()),
        type = com.noise.appscout.domain.model.SourceType.GITHUB,
        owner = "example",
        repository = appName.lowercase(),
        url = AppSource.githubUrl("example", appName.lowercase()),
    )
    val release = Release(
        id = "release-$appName",
        sourceId = source.id,
        tagName = latest,
        normalizedVersion = latest,
        title = "$appName $latest",
        body = "Bug fixes and performance improvements.",
        htmlUrl = "${source.url}/releases/tag/$latest",
        publishedAt = 1_759_468_800_000L,
        isPrerelease = false,
        isDraft = false,
        assets = emptyList(),
        fetchedAt = 1_759_468_800_000L,
    )
    val snapshot = TrackedAppSnapshot(
        app = TrackedApp(
            id = "app-$appName",
            packageName = "com.example.${appName.lowercase()}",
            appName = appName,
            installedVersionName = installed,
            installedVersionCode = 1,
            source = source,
            enabled = true,
            createdAt = 0L,
            updatedAt = 0L,
            lastNotifiedTag = null,
        ),
        latestRelease = if (status == ReleaseStatus.SOURCE_ERROR) null else release,
        lastCheckAt = 1_759_468_800_000L,
        lastError = if (status == ReleaseStatus.SOURCE_ERROR) {
            com.noise.appscout.domain.model.SourceErrorReason.NETWORK
        } else {
            null
        },
    )
    return TrackedAppStatus(
        snapshot = snapshot,
        installed = null,
        check = ReleaseStatusResolver.resolve(
            isInstalled = status != ReleaseStatus.NOT_INSTALLED,
            installedVersion = installed,
            snapshot = snapshot,
        ),
    )
}
