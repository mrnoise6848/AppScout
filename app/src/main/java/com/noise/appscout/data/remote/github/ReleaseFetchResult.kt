package com.noise.appscout.data.remote.github

import com.noise.appscout.core.network.RateLimitedException
import com.noise.appscout.core.network.TransientHttpException
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.ReleaseAsset
import com.noise.appscout.domain.model.SourceErrorReason
import com.noise.appscout.domain.version.VersionParser
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CancellationException

/** Outcome of asking GitHub for the newest eligible release of a source. */
sealed interface ReleaseFetchResult {

    data class Success(val release: Release, val etag: String?) : ReleaseFetchResult

    /** The cached release is still current; the caller keeps its stored copy. */
    data object NotModified : ReleaseFetchResult

    data class Failure(
        val reason: SourceErrorReason,
        val detail: String? = null,
    ) : ReleaseFetchResult
}

internal fun Throwable.toFetchFailure(): ReleaseFetchResult.Failure = when (this) {
    is CancellationException -> throw this
    is RateLimitedException -> ReleaseFetchResult.Failure(SourceErrorReason.RATE_LIMITED, message)
    is TransientHttpException -> ReleaseFetchResult.Failure(SourceErrorReason.SERVER_ERROR, "HTTP $code")
    is IOException -> ReleaseFetchResult.Failure(SourceErrorReason.NETWORK, message)
    is kotlinx.serialization.SerializationException ->
        ReleaseFetchResult.Failure(SourceErrorReason.MALFORMED_RESPONSE, message)
    else -> ReleaseFetchResult.Failure(SourceErrorReason.UNKNOWN, message)
}

internal fun reasonForHttpCode(code: Int): SourceErrorReason = when (code) {
    404 -> SourceErrorReason.NOT_FOUND
    403, 429 -> SourceErrorReason.RATE_LIMITED
    in 500..599 -> SourceErrorReason.SERVER_ERROR
    else -> SourceErrorReason.UNKNOWN
}

internal fun parseInstant(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}

/** Maps a GitHub release payload to the domain model. */
internal fun GitHubReleaseDto.toDomain(sourceId: String, fetchedAt: Long): Release = Release(
    id = "$sourceId#$tagName",
    sourceId = sourceId,
    tagName = tagName,
    normalizedVersion = VersionParser.normalize(tagName),
    title = name?.takeIf { it.isNotBlank() },
    body = body?.takeIf { it.isNotBlank() },
    htmlUrl = htmlUrl,
    publishedAt = parseInstant(publishedAt),
    isPrerelease = prerelease,
    isDraft = draft,
    assets = assets.mapNotNull { asset ->
        val url = asset.browserDownloadUrl
        if (asset.name.isBlank() || url.isNullOrBlank()) null
        else ReleaseAsset(name = asset.name, sizeBytes = asset.size, downloadUrl = url)
    },
    fetchedAt = fetchedAt,
)

/** Drafts are never eligible; prereleases are only eligible when the user opted in. */
internal fun GitHubReleaseDto.isEligible(includePrerelease: Boolean): Boolean =
    !draft && (includePrerelease || !prerelease)
