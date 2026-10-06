package com.noise.appscout.domain.model

/** Outcome of comparing the installed version with the latest published release. */
enum class ReleaseStatus {
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    VERSION_COMPARISON_UNCERTAIN,
    SOURCE_ERROR,
    NOT_INSTALLED,
}

/** Machine readable explanation for [ReleaseStatus.SOURCE_ERROR]. */
enum class SourceErrorReason {
    NETWORK,
    NOT_FOUND,
    NO_RELEASES,
    RATE_LIMITED,
    SERVER_ERROR,
    MALFORMED_RESPONSE,
    UNKNOWN,
}

/**
 * The complete, UI-ready result of a release check.
 *
 * [release] is always the last release known to the app (fresh or cached) so that the UI stays
 * useful offline. [isStale] tells the UI when that data could not be refreshed.
 */
data class ReleaseCheck(
    val status: ReleaseStatus,
    val installedVersion: String?,
    val release: Release?,
    val errorReason: SourceErrorReason? = null,
    val isStale: Boolean = false,
    val checkedAt: Long? = null,
) {
    companion object {
        fun notInstalled(installedVersion: String? = null): ReleaseCheck =
            ReleaseCheck(
                status = ReleaseStatus.NOT_INSTALLED,
                installedVersion = installedVersion,
                release = null,
            )
    }
}
