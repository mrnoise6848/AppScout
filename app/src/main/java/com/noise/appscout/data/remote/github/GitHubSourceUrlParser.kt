package com.noise.appscout.data.remote.github

import com.noise.appscout.domain.model.ParsedSource
import com.noise.appscout.domain.model.SourceType
import com.noise.appscout.domain.model.SourceUrlParser
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Parses the GitHub URL forms AppScout supports:
 *
 * ```
 * https://github.com/owner/repository
 * https://github.com/owner/repository/
 * https://github.com/owner/repository.git
 * ```
 *
 * Everything else (other hosts, deeper paths, missing owner/repository) fails gracefully with
 * `null` instead of throwing.
 */
class GitHubSourceUrlParser @Inject constructor() : SourceUrlParser {

    override fun parse(input: String): ParsedSource? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val url = candidate.toHttpUrlOrNull() ?: return null

        if (!HOSTS.contains(url.host.lowercase())) return null
        if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty()) return null
        if (url.port != 443 && url.port != -1) return null

        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 2) return null

        val owner = segments[0]
        val repository = segments[1].removeSuffix(".git")

        if (!OWNER_PATTERN.matches(owner)) return null
        if (!REPOSITORY_PATTERN.matches(repository)) return null

        return ParsedSource(
            type = SourceType.GITHUB,
            owner = owner,
            repository = repository,
            url = "https://github.com/$owner/$repository",
        )
    }

    private companion object {
        val HOSTS = setOf("github.com", "www.github.com")

        // GitHub logins: alphanumerics and single hyphens, max 39 characters.
        val OWNER_PATTERN = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$")

        // Repository names: alphanumerics, hyphens, underscores and dots.
        val REPOSITORY_PATTERN = Regex("^[A-Za-z0-9._-]{1,100}$")
    }
}
