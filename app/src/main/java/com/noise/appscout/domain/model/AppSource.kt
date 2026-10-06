package com.noise.appscout.domain.model

enum class SourceType {
    GITHUB,
}

/**
 * The external release source attached to a [TrackedApp].
 *
 * `id` is stable and deterministic so that re-attaching the same repository is idempotent.
 */
data class AppSource(
    val id: String,
    val type: SourceType,
    val owner: String,
    val repository: String,
    val url: String,
) {
    companion object {
        const val GITHUB_ID_PREFIX = "github:"

        fun githubId(owner: String, repository: String): String =
            "$GITHUB_ID_PREFIX$owner/$repository"

        fun githubUrl(owner: String, repository: String): String =
            "https://github.com/$owner/$repository"
    }
}
