package com.noise.appscout.domain.version

/** Result of comparing two versions. */
enum class VersionComparison {
    LOWER,
    EQUAL,
    GREATER,
    UNCERTAIN,
}

/**
 * Compares two release tags.
 *
 * The comparator never guesses: if either side cannot be parsed the result is [UNCERTAIN].
 * Prerelease identifiers follow the ordering defined by Semantic Versioning section 11
 * (numeric identifiers compare numerically and sort below alphanumeric ones).
 */
object VersionComparator {

    fun compare(leftRaw: String?, rightRaw: String?): VersionComparison {
        val left = VersionParser.parse(leftRaw) ?: return VersionComparison.UNCERTAIN
        val right = VersionParser.parse(rightRaw) ?: return VersionComparison.UNCERTAIN
        return compare(left, right)
    }

    fun compare(left: ParsedVersion, right: ParsedVersion): VersionComparison {
        val coreSize = maxOf(left.core.size, right.core.size)
        for (index in 0 until coreSize) {
            val l = left.core.getOrElse(index) { 0L }
            val r = right.core.getOrElse(index) { 0L }
            if (l != r) return if (l < r) VersionComparison.LOWER else VersionComparison.GREATER
        }

        // Equal numeric cores: a build carrying a prerelease marker is older than a stable build.
        if (left.isStable && right.isStable) return VersionComparison.EQUAL
        if (left.isStable) return VersionComparison.GREATER
        if (right.isStable) return VersionComparison.LOWER

        return comparePrerelease(left.prerelease.orEmpty(), right.prerelease.orEmpty())
    }

    private fun comparePrerelease(left: String, right: String): VersionComparison {
        val leftTokens = left.split('.')
        val rightTokens = right.split('.')
        val size = maxOf(leftTokens.size, rightTokens.size)

        for (index in 0 until size) {
            val l = leftTokens.getOrNull(index) ?: return VersionComparison.LOWER
            val r = rightTokens.getOrNull(index) ?: return VersionComparison.GREATER

            val lNumber = l.toLongOrNull()
            val rNumber = r.toLongOrNull()

            when {
                lNumber != null && rNumber != null -> {
                    if (lNumber != rNumber) {
                        return if (lNumber < rNumber) VersionComparison.LOWER else VersionComparison.GREATER
                    }
                }
                lNumber != null -> return VersionComparison.LOWER
                rNumber != null -> return VersionComparison.GREATER
                else -> {
                    val order = l.compareTo(r, ignoreCase = true)
                    if (order != 0) return if (order < 0) VersionComparison.LOWER else VersionComparison.GREATER
                }
            }
        }

        return VersionComparison.EQUAL
    }
}
