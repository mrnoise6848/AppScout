package com.noise.appscout.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A GitHub repository attached to a tracked app.
 *
 * One [SourceEntity] belongs to at most one tracked app. The id is deterministic
 * (`github:owner/repository`) so re-attaching the same repository upserts instead of duplicating.
 */
@Entity(
    tableName = "sources",
    indices = [Index(value = ["owner", "repository"], unique = true)],
)
data class SourceEntity(
    @PrimaryKey val id: String,
    val type: String = TYPE_GITHUB,
    val owner: String,
    val repository: String,
    val url: String,
    /** `ETag` of the last successful `releases/latest` response, used for conditional requests. */
    val etag: String? = null,
    /** Last time a refresh succeeded. */
    val lastCheckAt: Long? = null,
    /** Why the most recent refresh failed; `null` after a successful refresh. */
    val lastError: String? = null,
    val createdAt: Long,
) {
    companion object {
        const val TYPE_GITHUB = "GITHUB"
    }
}

/**
 * An Android application the user chose to track.
 *
 * Icons are never stored here: they are resolved from [packageName] through
 * `android.content.pm.PackageManager` at render time.
 */
@Entity(
    tableName = "tracked_apps",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["packageName"], unique = true),
        Index(value = ["sourceId"]),
    ],
)
data class TrackedAppEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val appName: String,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    val sourceId: String?,
    val enabled: Boolean = true,
    /** Tag of the release the user was last notified about; used to avoid duplicate notifications. */
    val lastNotifiedTag: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A published GitHub release, cached so the UI stays useful offline. */
@Entity(
    tableName = "releases",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sourceId", "tagName"], unique = true),
        Index(value = ["sourceId"]),
    ],
)
data class ReleaseEntity(
    /** Stable id: `sourceId#tagName`. */
    @PrimaryKey val id: String,
    val sourceId: String,
    val tagName: String,
    val normalizedVersion: String?,
    val title: String?,
    val body: String?,
    val htmlUrl: String?,
    val publishedAt: Long?,
    val isPrerelease: Boolean,
    val isDraft: Boolean,
    /** JSON encoded list of [com.noise.appscout.core.database.converter.ReleaseAsset] values. */
    val assetsJson: String,
    val fetchedAt: Long,
)

/** Structured AI output for a single release, cached per release. */
@Entity(
    tableName = "ai_summaries",
    foreignKeys = [
        ForeignKey(
            entity = ReleaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["releaseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["releaseId"], unique = true),
    ],
)
data class AiSummaryEntity(
    @PrimaryKey val releaseId: String,
    val summary: String,
    val importance: String,
    /** JSON encoded list of reason strings. */
    val reasonsJson: String,
    val securityRelated: Boolean,
    val breakingChangePossible: Boolean,
    val model: String?,
    val createdAt: Long,
)
