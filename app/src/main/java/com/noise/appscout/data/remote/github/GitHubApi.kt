package com.noise.appscout.data.remote.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class GitHubAssetDto(
    val id: Long = 0,
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
)

@Serializable
data class GitHubUserDto(
    val login: String? = null,
)

@Serializable
data class GitHubReleaseDto(
    val id: Long = 0,
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList(),
    val author: GitHubUserDto? = null,
)

/** Error payload returned by the GitHub REST API. */
@Serializable
data class GitHubErrorDto(
    val message: String? = null,
    @SerialName("documentation_url") val documentationUrl: String? = null,
)

/**
 * GitHub REST API surface used by AppScout.
 *
 * Two endpoints are supported on purpose: `releases/latest` is the cheap, spec-mandated call, while
 * `releases` is the fallback that lets AppScout tell "repository not found" apart from "repository
 * has no releases" and filter drafts/prereleases explicitly.
 */
interface GitHubApi {

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("If-None-Match") ifNoneMatch: String? = null,
    ): Response<GitHubReleaseDto>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun listReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30,
    ): Response<List<GitHubReleaseDto>>
}
