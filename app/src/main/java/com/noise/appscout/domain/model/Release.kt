package com.noise.appscout.domain.model

/** A downloadable artifact attached to a release. */
data class ReleaseAsset(
    val name: String,
    val sizeBytes: Long,
    val downloadUrl: String,
)

/** A published release from the attached source, cached locally for offline use. */
data class Release(
    val id: String,
    val sourceId: String,
    val tagName: String,
    /** Best-effort normalized form of [tagName]; `null` when the tag cannot be normalized. */
    val normalizedVersion: String?,
    val title: String?,
    val body: String?,
    val htmlUrl: String?,
    val publishedAt: Long?,
    val isPrerelease: Boolean,
    val isDraft: Boolean,
    val assets: List<ReleaseAsset>,
    val fetchedAt: Long,
)
