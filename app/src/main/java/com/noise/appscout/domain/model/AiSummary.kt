package com.noise.appscout.domain.model

enum class UpdateImportance {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN,
    ;

    companion object {
        fun fromRaw(raw: String?): UpdateImportance =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * Structured, advisory AI output for a release.
 *
 * This is never a security certification: it is a summary of the supplied release notes only.
 */
data class AiSummary(
    val summary: String,
    val importance: UpdateImportance,
    val reasons: List<String>,
    val securityRelated: Boolean,
    val breakingChangePossible: Boolean,
    val model: String? = null,
    val generatedAt: Long,
)
