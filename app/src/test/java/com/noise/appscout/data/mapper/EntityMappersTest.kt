package com.noise.appscout.data.mapper

import com.noise.appscout.core.database.relation.TrackedAppRelations
import com.noise.appscout.core.database.entity.ReleaseEntity
import com.noise.appscout.core.database.entity.SourceEntity
import com.noise.appscout.core.database.entity.TrackedAppEntity
import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.ReleaseAsset
import com.noise.appscout.domain.model.SourceErrorReason
import com.noise.appscout.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cached release state survives a disk round-trip unchanged (Phase 4). */
class EntityMappersTest {

    private val release = Release(
        id = AppSource.githubId("owner", "repo") + "#v2.0.0",
        sourceId = AppSource.githubId("owner", "repo"),
        tagName = "v2.0.0",
        normalizedVersion = "2.0.0",
        title = "Release 2.0.0",
        body = "Bug fixes\nPerformance improvements",
        htmlUrl = "https://github.com/owner/repo/releases/tag/v2.0.0",
        publishedAt = 1_700_000_000_000L,
        isPrerelease = false,
        isDraft = false,
        assets = listOf(
            ReleaseAsset(name = "app.apk", sizeBytes = 1234L, downloadUrl = "https://example.com/app.apk"),
        ),
        fetchedAt = 1_700_000_100_000L,
    )

    @Test
    fun `release survives entity round trip`() {
        val restored = release.toEntity().toDomain()
        assertEquals(release, restored)
    }

    @Test
    fun `release with no assets and no body survives round trip`() {
        val bare = release.copy(body = null, assets = emptyList(), title = null, htmlUrl = null, publishedAt = null)
        assertEquals(bare, bare.toEntity().toDomain())
    }

    @Test
    fun `source survives round trip`() {
        val source = AppSource(
            id = AppSource.githubId("owner", "repo"),
            type = SourceType.GITHUB,
            owner = "owner",
            repository = "repo",
            url = "https://github.com/owner/repo",
        )
        val entity = SourceEntity(
            id = source.id,
            type = source.type.name,
            owner = source.owner,
            repository = source.repository,
            url = source.url,
            createdAt = 1L,
            etag = "\"etag\"",
            lastCheckAt = 2L,
            lastError = SourceErrorReason.NETWORK.name,
        )
        assertEquals(source, entity.toDomain())
        assertEquals("\"etag\"", entity.etag)
        assertEquals(SourceErrorReason.NETWORK, entity.lastError.toSourceErrorReason())
        // an unparsable/absent reason degrades to UNKNOWN instead of crashing
        assertEquals(SourceErrorReason.UNKNOWN, entity.copy(lastError = null).lastError.toSourceErrorReason())
    }

    @Test
    fun `snapshot mapping exposes app, release and source health`() {
        val sourceEntity = SourceEntity(
            id = AppSource.githubId("owner", "repo"),
            type = SourceType.GITHUB.name,
            owner = "owner",
            repository = "repo",
            url = "https://github.com/owner/repo",
            createdAt = 1L,
            lastError = SourceErrorReason.RATE_LIMITED.name,
            lastCheckAt = 5L,
        )
        val app = TrackedAppEntity(
            id = "app-1",
            packageName = "org.mozilla.firefox",
            appName = "Firefox",
            installedVersionName = "1.9.0",
            installedVersionCode = 190L,
            sourceId = sourceEntity.id,
            enabled = true,
            createdAt = 1L,
            updatedAt = 2L,
        )
        val relations = TrackedAppRelations(
            app = app,
            source = sourceEntity,
            releases = listOf(release.toEntity()),
        )

        val snapshot = relations.toSnapshot()

        assertEquals("app-1", snapshot.app.id)
        assertEquals("Firefox", snapshot.app.appName)
        assertEquals("v2.0.0", snapshot.latestRelease?.tagName)
        assertEquals(SourceErrorReason.RATE_LIMITED, snapshot.lastError)
        assertEquals(5L, snapshot.lastCheckAt)
        assertEquals("owner", snapshot.app.source?.owner)
    }

    @Test
    fun `snapshot with no release maps to null release`() {
        val sourceEntity = SourceEntity(
            id = AppSource.githubId("owner", "repo"),
            type = SourceType.GITHUB.name,
            owner = "owner",
            repository = "repo",
            url = "https://github.com/owner/repo",
            createdAt = 1L,
        )
        val relations = TrackedAppRelations(
            app = TrackedAppEntity(
                id = "app-1",
                packageName = "p",
                appName = "P",
                installedVersionName = null,
                installedVersionCode = null,
                sourceId = null,
                enabled = true,
                createdAt = 1L,
                updatedAt = 1L,
            ),
            source = null,
            releases = emptyList(),
        )
        val snapshot = relations.toSnapshot()
        assertNull(snapshot.latestRelease)
        assertNull(snapshot.app.source)
        assertNull(snapshot.lastError)
        assertEquals("owner", sourceEntity.owner)
    }
}
