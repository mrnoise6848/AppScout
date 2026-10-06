package com.noise.appscout.domain.repository

import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.SourceErrorReason
import com.noise.appscout.domain.model.TrackedApp
import kotlinx.coroutines.flow.Flow

/** Everything the UI needs to render one tracked app, straight out of the local cache. */
data class TrackedAppSnapshot(
    val app: TrackedApp,
    val latestRelease: Release?,
    /** Last time a refresh succeeded. */
    val lastCheckAt: Long?,
    /** Why the most recent refresh failed; `null` when the last refresh succeeded. */
    val lastError: SourceErrorReason?,
)

/** Raw result of asking GitHub for a release, before anything is written to disk. */
sealed interface FetchResult {

    data class Fetched(val release: Release, val etag: String?) : FetchResult

    /** The cached release is still current (`HTTP 304`). */
    data object Cached : FetchResult

    data class Failed(val error: SourceErrorReason) : FetchResult
}

/** Outcome of a full refresh (fetch + persist). */
data class RefreshOutcome(
    val sourceId: String,
    val release: Release?,
    val error: SourceErrorReason?,
    val wasNotModified: Boolean = false,
) {
    val isSuccess: Boolean get() = error == null
}

/** Reads and refreshes cached GitHub releases. */
interface ReleaseRepository {

    /** All tracked apps together with their cached release, emitted whenever the cache changes. */
    fun observeSnapshots(): Flow<List<TrackedAppSnapshot>>

    fun observeSnapshot(trackedAppId: String): Flow<TrackedAppSnapshot?>

    suspend fun getSnapshot(trackedAppId: String): TrackedAppSnapshot?

    /** Performs the network call only; safe to call before the source exists locally. */
    suspend fun fetch(source: AppSource, includePrerelease: Boolean): FetchResult

    /** Writes a [FetchResult] (or its failure) to the local cache. */
    suspend fun persist(source: AppSource, result: FetchResult)

    /** Convenience wrapper: [fetch] followed by [persist]. */
    suspend fun refresh(source: AppSource, includePrerelease: Boolean): RefreshOutcome

    /** The release currently cached for [sourceId], if any. */
    suspend fun getCachedRelease(sourceId: String): Release?
}
