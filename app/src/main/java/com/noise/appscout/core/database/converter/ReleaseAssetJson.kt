package com.noise.appscout.core.database.converter

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A downloadable artifact attached to a GitHub release. */
@Serializable
data class ReleaseAsset(
    val name: String,
    val sizeBytes: Long,
    val downloadUrl: String,
)

/**
 * Converts the small, read-only release payload lists to/from JSON text so Room does not
 * need to know about the serialization library.
 */
object ReleaseAssetJson {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(assets: List<ReleaseAsset>): String = json.encodeToString(assets)

    fun decode(value: String?): List<ReleaseAsset> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ReleaseAsset>>(value) }.getOrDefault(emptyList())
    }

    fun encodeStrings(values: List<String>): String = json.encodeToString(values)

    fun decodeStrings(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
    }
}
