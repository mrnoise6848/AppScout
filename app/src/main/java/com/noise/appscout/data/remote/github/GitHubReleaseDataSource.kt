package com.noise.appscout.data.remote.github

import com.noise.appscout.core.common.IoDispatcher
import com.noise.appscout.core.common.TimeProvider
import com.noise.appscout.core.network.RateLimitedException
import com.noise.appscout.core.network.TransientHttpException
import com.noise.appscout.core.network.retryWithBackoff
import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.SourceErrorReason
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * Reads the newest eligible GitHub release for a source.
 *
 * `releases/latest` is used first because it is the cheapest, spec-mandated call. When that call
 * cannot answer the question on its own (404, or a payload that is a draft/prerelease) the client
 * falls back to the release list so AppScout can distinguish "repository not found" from
 * "repository publishes no releases" and filter prereleases explicitly.
 */
class GitHubReleaseDataSource @Inject constructor(
    private val api: GitHubApi,
    private val timeProvider: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val errorJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    suspend fun fetchLatestRelease(
        source: AppSource,
        cachedEtag: String?,
        includePrerelease: Boolean,
    ): ReleaseFetchResult = withContext(io) {
        val response = try {
            latestResponse(source.owner, source.repository, cachedEtag)
        } catch (t: Throwable) {
            return@withContext t.toFetchFailure()
        }

        when {
            response.code() == CODE_NOT_MODIFIED -> ReleaseFetchResult.NotModified

            response.isSuccessful -> {
                val body = response.body()
                    ?: return@withContext ReleaseFetchResult.Failure(
                        SourceErrorReason.MALFORMED_RESPONSE,
                        "empty response body",
                    )

                if (body.isEligible(includePrerelease)) {
                    ReleaseFetchResult.Success(
                        release = body.toDomain(source.id, timeProvider.nowMillis()),
                        etag = response.headers()["ETag"],
                    )
                } else {
                    fetchFromList(source, includePrerelease)
                }
            }

            response.code() == 404 -> fetchFromList(source, includePrerelease)

            else -> ReleaseFetchResult.Failure(
                reason = if (response.code() == 403 || response.code() == 429) {
                    SourceErrorReason.RATE_LIMITED
                } else {
                    reasonForHttpCode(response.code())
                },
                detail = response.readErrorMessage(),
            )
        }
    }

    private suspend fun fetchFromList(
        source: AppSource,
        includePrerelease: Boolean,
    ): ReleaseFetchResult {
        val response = try {
            listResponse(source.owner, source.repository)
        } catch (t: Throwable) {
            return t.toFetchFailure()
        }

        if (!response.isSuccessful) {
            return ReleaseFetchResult.Failure(
                reason = reasonForHttpCode(response.code()),
                detail = response.readErrorMessage(),
            )
        }

        val releases = response.body()
            ?: return ReleaseFetchResult.Failure(
                SourceErrorReason.MALFORMED_RESPONSE,
                "empty response body",
            )

        val eligible = releases
            .filter { it.isEligible(includePrerelease) }
            .sortedByDescending { parseInstant(it.publishedAt) ?: 0L }

        val newest = eligible.firstOrNull()
            ?: return ReleaseFetchResult.Failure(SourceErrorReason.NO_RELEASES)

        return ReleaseFetchResult.Success(
            release = newest.toDomain(source.id, timeProvider.nowMillis()),
            etag = null,
        )
    }

    private suspend fun latestResponse(
        owner: String,
        repo: String,
        etag: String?,
    ): Response<GitHubReleaseDto> = retryWithBackoff {
        val response = api.getLatestRelease(owner, repo, etag)
        response.guardTransient()
    }

    private suspend fun listResponse(
        owner: String,
        repo: String,
    ): Response<List<GitHubReleaseDto>> = retryWithBackoff {
        api.listReleases(owner, repo).guardTransient()
    }

    private fun <T> Response<T>.guardTransient(): Response<T> = when {
        code() in 500..599 -> throw TransientHttpException(code())
        isRateLimited() -> throw RateLimitedException(retryAfterSeconds())
        else -> this
    }

    private fun <T> Response<T>.isRateLimited(): Boolean {
        if (code() == 429) return true
        if (code() != 403) return false
        val remaining = headers()["X-RateLimit-Remaining"] ?: return false
        return remaining == "0"
    }

    private fun <T> Response<T>.retryAfterSeconds(): Long? =
        headers()["Retry-After"]?.toLongOrNull()

    private fun <T> Response<T>.readErrorMessage(): String? {
        val raw = runCatching { errorBody()?.string() }.getOrNull() ?: return null
        if (raw.isBlank()) return null
        return runCatching {
            errorJson.decodeFromString<GitHubErrorDto>(raw).message
        }.getOrNull() ?: raw.take(200)
    }

    private companion object {
        const val CODE_NOT_MODIFIED = 304
    }
}
