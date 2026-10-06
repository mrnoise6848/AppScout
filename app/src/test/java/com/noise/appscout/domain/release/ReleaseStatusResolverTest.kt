package com.noise.appscout.domain.release

import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.ReleaseStatus
import com.noise.appscout.domain.model.SourceErrorReason
import com.noise.appscout.domain.model.TrackedApp
import com.noise.appscout.domain.repository.TrackedAppSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance tests D, F, G, H, I and J at the status-decision level. */
class ReleaseStatusResolverTest {

    private val source = AppSource(
        id = AppSource.githubId("owner", "repo"),
        type = com.noise.appscout.domain.model.SourceType.GITHUB,
        owner = "owner",
        repository = "repo",
        url = AppSource.githubUrl("owner", "repo"),
    )

    private fun release(tag: String, body: String? = "notes") = Release(
        id = "r-$tag",
        sourceId = source.id,
        tagName = tag,
        normalizedVersion = com.noise.appscout.domain.version.VersionParser.normalize(tag),
        title = tag,
        body = body,
        htmlUrl = "https://github.com/owner/repo/releases/tag/$tag",
        publishedAt = 1_700_000_000_000L,
        isPrerelease = false,
        isDraft = false,
        assets = emptyList(),
        fetchedAt = 1_700_000_000_000L,
    )

    private fun snapshot(
        installedVersion: String?,
        latestTag: String?,
        lastError: SourceErrorReason? = null,
        lastCheckAt: Long? = 1_700_000_000_000L,
    ) = TrackedAppSnapshot(
        app = TrackedApp(
            id = "app-1",
            packageName = "org.mozilla.firefox",
            appName = "Firefox",
            installedVersionName = installedVersion,
            installedVersionCode = 1,
            source = source,
            enabled = true,
            createdAt = 0L,
            updatedAt = 0L,
            lastNotifiedTag = null,
        ),
        latestRelease = latestTag?.let { release(it) },
        lastCheckAt = lastCheckAt,
        lastError = lastError,
    )

    private fun resolve(installedVersion: String?, latestTag: String?, error: SourceErrorReason? = null, isInstalled: Boolean = true) =
        ReleaseStatusResolver.resolve(
            isInstalled = isInstalled,
            installedVersion = installedVersion,
            snapshot = snapshot(installedVersion, latestTag, error),
        )

    @Test
    fun `update available when installed is older`() {
        // F
        val check = resolve("1.9.0", "v2.0.0")
        assertEquals(ReleaseStatus.UPDATE_AVAILABLE, check.status)
        assertEquals("1.9.0", check.installedVersion)
        assertEquals("v2.0.0", check.release?.tagName)
        assertEquals(false, check.isStale)
    }

    @Test
    fun `up to date when versions match`() {
        // G
        assertEquals(ReleaseStatus.UP_TO_DATE, resolve("2.0.0", "v2.0.0").status)
        assertEquals(ReleaseStatus.UP_TO_DATE, resolve("2.0.1", "v2.0.0").status)
    }

    @Test
    fun `uncertain comparison never reports an update`() {
        // D
        val check = resolve("2026.09", "stable-latest")
        assertEquals(ReleaseStatus.VERSION_COMPARISON_UNCERTAIN, check.status)
        // release stays visible, but no update decision is made
        assertEquals("stable-latest", check.release?.tagName)
    }

    @Test
    fun `source error when there is no release at all`() {
        // H: repository missing → no cached release and a failed check
        val check = resolve("1.0.0", null, error = SourceErrorReason.NOT_FOUND)
        assertEquals(ReleaseStatus.SOURCE_ERROR, check.status)
        assertEquals(SourceErrorReason.NOT_FOUND, check.errorReason)
        assertNull(check.release)
    }

    @Test
    fun `offline keeps cached data visible as stale`() {
        // J: offline refresh fails, cache remains, decision is marked stale
        val check = resolve("1.9.0", "v2.0.0", error = SourceErrorReason.NETWORK)
        assertEquals(ReleaseStatus.UPDATE_AVAILABLE, check.status)
        assertTrue(check.isStale)
        assertEquals(SourceErrorReason.NETWORK, check.errorReason)
    }

    @Test
    fun `rate limit keeps cached decision with a helpful reason`() {
        // I
        val check = resolve("1.9.0", "v2.0.0", error = SourceErrorReason.RATE_LIMITED)
        assertEquals(ReleaseStatus.UPDATE_AVAILABLE, check.status)
        assertTrue(check.isStale)
        assertEquals(SourceErrorReason.RATE_LIMITED, check.errorReason)
    }

    @Test
    fun `missing installed version is uncertain`() {
        val check = resolve(null, "v2.0.0")
        assertEquals(ReleaseStatus.VERSION_COMPARISON_UNCERTAIN, check.status)
        assertEquals(false, check.isStale)
    }

    @Test
    fun `uninstalled app is reported as not installed`() {
        val check = resolve("1.0.0", "v2.0.0", isInstalled = false)
        assertEquals(ReleaseStatus.NOT_INSTALLED, check.status)
    }

    @Test
    fun `up to date stays visible while offline`() {
        val check = resolve("2.0.0", "v2.0.0", error = SourceErrorReason.NETWORK)
        assertEquals(ReleaseStatus.UP_TO_DATE, check.status)
        assertTrue(check.isStale)
    }
}
