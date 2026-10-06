package com.noise.appscout.domain.version

/**
 * Parses release tags into a comparable form without pretending that every repository follows
 * Semantic Versioning.
 *
 * Supported shape:
 * ```
 * [prefix] <numeric>(.<numeric>)* [-|.|_ <alphanumeric suffix>] [+ build metadata]
 * ```
 * Anything that cannot be proven comparable parses to `null`, which callers surface as
 * [com.noise.appscout.domain.model.ReleaseStatus.VERSION_COMPARISON_UNCERTAIN].
 */
data class ParsedVersion(
    /** Numeric segments before any suffix, e.g. `[1, 2, 3]` for `1.2.3-rc.1`. */
    val core: List<Long>,
    /** Lowercase prerelease suffix without its separator, e.g. `rc.1`. `null` for stable builds. */
    val prerelease: String?,
) {
    val isStable: Boolean get() = prerelease == null

    /** Canonical, prefix-free representation, e.g. `1.2.3` or `2.0.0-rc.1`. */
    fun normalized(): String {
        val coreText = core.joinToString(".")
        val suffix = prerelease ?: return coreText
        return "$coreText-$suffix"
    }
}

object VersionParser {

    private val TAG_PREFIXES = listOf("release-", "rel-", "version-", "ver-")

    private const val SEPARATORS = "-._+"

    /** @return the parsed version, or `null` when the tag is not provably comparable. */
    fun parse(raw: String?): ParsedVersion? {
        val input = raw?.trim().orEmpty()
        if (input.isEmpty()) return null

        val trimmed = stripKnownPrefixes(input)

        // Build metadata never affects ordering (Semantic Versioning rule 10).
        val withoutBuild = trimmed.substringBefore('+')

        val coreEnd = scanCoreEnd(withoutBuild)
        if (coreEnd == 0) return null

        val coreText = withoutBuild.substring(0, coreEnd)
        val rest = withoutBuild.substring(coreEnd)

        val core = parseCoreSegments(coreText) ?: return null
        if (core.isEmpty()) return null

        if (rest.isEmpty()) return ParsedVersion(core, null)
        if (rest[0] !in SEPARATORS) return null

        val suffix = rest.substring(1).trim()
        if (suffix.isEmpty()) return null

        val prerelease = normalizeSuffix(suffix) ?: return null
        return ParsedVersion(core, prerelease)
    }

    /** @return canonical prefix-free text, or `null` when the tag cannot be normalized. */
    fun normalize(raw: String?): String? = parse(raw)?.normalized()

    private fun stripKnownPrefixes(value: String): String {
        val lowered = value.lowercase()
        TAG_PREFIXES.firstOrNull { lowered.startsWith(it) }?.let { return value.substring(it.length) }
        if (value.length > 1 && (value[0] == 'v' || value[0] == 'V') && value[1].isDigit()) {
            return value.substring(1)
        }
        return value
    }

    private fun scanCoreEnd(value: String): Int {
        if (value.isEmpty() || !value[0].isDigit()) return 0
        var index = 0
        while (index < value.length && value[index].isDigit()) index++
        while (
            index < value.length &&
            value[index] == '.' &&
            index + 1 < value.length &&
            value[index + 1].isDigit()
        ) {
            index++
            while (index < value.length && value[index].isDigit()) index++
        }
        return index
    }

    private fun parseCoreSegments(coreText: String): List<Long>? {
        if (coreText.isEmpty()) return null
        val segments = mutableListOf<Long>()
        for (part in coreText.split('.')) {
            if (part.isEmpty() || !part.all { it.isDigit() }) return null
            segments += part.toLongOrNull() ?: return null
        }
        return segments
    }

    /**
     * Accepts suffixes made of dot/underscore/dash separated alphanumeric identifiers, which is
     * the ordering Semantic Versioning defines. Anything else (dates with separators that are not
     * alphanumeric, words with spaces, empty identifiers) is rejected as ambiguous.
     */
    private fun normalizeSuffix(suffix: String): String? {
        val tokens = suffix.split('-', '.', '_').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        if (tokens.any { token -> token.any { !it.isLetterOrDigit() } }) return null
        return tokens.joinToString(".").lowercase()
    }
}
