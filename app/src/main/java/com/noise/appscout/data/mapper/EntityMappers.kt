package com.noise.appscout.data.mapper

import com.noise.appscout.core.database.converter.ReleaseAssetJson
import com.noise.appscout.core.database.entity.ReleaseEntity
import com.noise.appscout.core.database.entity.SourceEntity
import com.noise.appscout.core.database.entity.TrackedAppEntity
import com.noise.appscout.core.database.relation.TrackedAppRelations
import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.SourceErrorReason
import com.noise.appscout.domain.model.SourceType
import com.noise.appscout.domain.model.TrackedApp
import com.noise.appscout.domain.repository.TrackedAppSnapshot

fun SourceEntity.toDomain(): AppSource = AppSource(
    id = id,
    type = runCatching { SourceType.valueOf(type) }.getOrDefault(SourceType.GITHUB),
    owner = owner,
    repository = repository,
    url = url,
)

fun TrackedAppEntity.toDomain(source: SourceEntity?): TrackedApp = TrackedApp(
    id = id,
    packageName = packageName,
    appName = appName,
    installedVersionName = installedVersionName,
    installedVersionCode = installedVersionCode,
    source = source?.toDomain(),
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastNotifiedTag = lastNotifiedTag,
)

fun ReleaseEntity.toDomain(): Release = Release(
    id = id,
    sourceId = sourceId,
    tagName = tagName,
    normalizedVersion = normalizedVersion,
    title = title,
    body = body,
    htmlUrl = htmlUrl,
    publishedAt = publishedAt,
    isPrerelease = isPrerelease,
    isDraft = isDraft,
    assets = ReleaseAssetJson.decode(assetsJson).map {
        com.noise.appscout.domain.model.ReleaseAsset(
            name = it.name,
            sizeBytes = it.sizeBytes,
            downloadUrl = it.downloadUrl,
        )
    },
    fetchedAt = fetchedAt,
)

fun Release.toEntity(): ReleaseEntity = ReleaseEntity(
    id = id,
    sourceId = sourceId,
    tagName = tagName,
    normalizedVersion = normalizedVersion,
    title = title,
    body = body,
    htmlUrl = htmlUrl,
    publishedAt = publishedAt,
    isPrerelease = isPrerelease,
    isDraft = isDraft,
    assetsJson = ReleaseAssetJson.encode(
        assets.map {
            com.noise.appscout.core.database.converter.ReleaseAsset(
                name = it.name,
                sizeBytes = it.sizeBytes,
                downloadUrl = it.downloadUrl,
            )
        },
    ),
    fetchedAt = fetchedAt,
)

fun String?.toSourceErrorReason(): SourceErrorReason =
    runCatching { SourceErrorReason.valueOf(this!!) }.getOrDefault(SourceErrorReason.UNKNOWN)

fun TrackedAppRelations.toSnapshot(): TrackedAppSnapshot = TrackedAppSnapshot(
    app = app.toDomain(source),
    latestRelease = releases
        .maxWithOrNull(compareBy({ it.publishedAt ?: 0L }, { it.fetchedAt }))
        ?.toDomain(),
    lastCheckAt = source?.lastCheckAt,
    lastError = source?.lastError?.toSourceErrorReason(),
)
