package com.noise.appscout.data.remote.gemini

import com.noise.appscout.core.common.TimeProvider
import com.noise.appscout.domain.ai.AiApiKeyProvider
import com.noise.appscout.domain.ai.AiSummaryProvider
import com.noise.appscout.domain.ai.AiSummaryResult
import com.noise.appscout.domain.model.AiSummary
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.UpdateImportance
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bring-your-own-key Gemini provider (ADR-005).
 *
 * * the key comes from [ApiKeyVault] and is sent as a request header, never logged
 * * the model is forced into structured JSON output with an explicit schema
 * * every response is validated against the AI output contract before it is trusted:
 *   blank/oversized summaries, too many reasons and security claims that the release notes do
 *   not support are rejected or corrected
 * * any failure maps to an [AiSummaryResult] the UI can render without breaking release details
 */
class GeminiAiSummaryProvider @Inject constructor(
    private val api: GeminiApi,
    private val apiKeyProvider: AiApiKeyProvider,
    private val timeProvider: TimeProvider,
) : AiSummaryProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    override suspend fun summarize(release: Release, appName: String): AiSummaryResult {
        val notes = release.body
        if (notes.isNullOrBlank()) return AiSummaryResult.InvalidOutput

        val key = apiKeyProvider.readApiKey()
        if (key.isNullOrEmpty()) return AiSummaryResult.NoApiKey

        val response = try {
            api.generateContent(
                model = MODEL,
                apiKey = key,
                request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(prompt(appName, release)))),
                    ),
                    generationConfig = GeminiGenerationConfig(
                        responseMimeType = "application/json",
                        responseSchema = SCHEMA,
                        temperature = 0.2,
                    ),
                ),
            )
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            return AiSummaryResult.NetworkError
        } catch (_: Exception) {
            return AiSummaryResult.NetworkError
        }

        if (response.isSuccessful.not()) {
            return when (response.code()) {
                401, 403 -> AiSummaryResult.InvalidApiKey
                429 -> AiSummaryResult.RateLimited
                else -> AiSummaryResult.NetworkError
            }
        }

        val rawText = response.body()
            ?.candidates?.firstOrNull()
            ?.content?.parts?.firstOrNull()
            ?.text
            .orEmpty()

        return validate(rawText, notes, timeProvider.nowMillis())
            ?.let { AiSummaryResult.Success(it) }
            ?: AiSummaryResult.InvalidOutput
    }

    /** Parses and validates the model output; `null` means the contract was violated. */
    internal fun validate(rawText: String, releaseNotes: String, now: Long): AiSummary? {
        val payload = runCatching { json.decodeFromString<AiSummaryPayload>(rawText.trim()) }
            .getOrNull() ?: return null

        val summary = payload.summary.trim()
        if (summary.isEmpty() || summary.length > MAX_SUMMARY_LENGTH) return null

        val reasons = payload.reasons
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_REASONS)
        if (payload.reasons.isNotEmpty() && reasons.isEmpty()) return null

        val evidence = releaseNotes.lowercase()
        val claimsSecurity = payload.securityRelated ||
            reasons.any { it.lowercase().mentionsSecurity() } ||
            summary.lowercase().mentionsSecurity()
        val securitySupported = evidence.mentionsSecurity()

        val cleanedReasons = if (securitySupported) reasons else reasons.filterNot { it.lowercase().mentionsSecurity() }

        return AiSummary(
            summary = summary,
            importance = UpdateImportance.fromRaw(payload.importance),
            reasons = cleanedReasons,
            // Evidence constraint: a security claim is only kept when the release notes support it.
            securityRelated = claimsSecurity && securitySupported,
            breakingChangePossible = payload.breakingChangePossible,
            model = MODEL,
            generatedAt = now,
        )
    }

    private fun prompt(appName: String, release: Release): String = buildString {
        appendLine("You summarize software release notes for a mobile app called \"$appName\".")
        appendLine("Release tag: ${release.tagName}")
        release.title?.let { appendLine("Release title: $it") }
        appendLine()
        appendLine("Release notes:")
        appendLine(release.body.orEmpty())
        appendLine()
        appendLine("Rules:")
        appendLine("- Use ONLY the release notes above as evidence. Never invent changes.")
        appendLine("- Never claim a security fix or vulnerability patch unless the notes explicitly say so.")
        appendLine("- If the notes are insufficient to judge importance, use \"UNKNOWN\".")
        appendLine("- Return JSON with keys: summary, importance, reasons, security_related, breaking_change_possible.")
        appendLine("- importance must be one of LOW, MEDIUM, HIGH, UNKNOWN.")
        appendLine("- reasons is a short list of the concrete changes mentioned in the notes.")
    }

    private fun String.mentionsSecurity(): Boolean =
        SECURITY_TERMS.any { contains(it) }

    @Serializable
    internal data class AiSummaryPayload(
        val summary: String = "",
        val importance: String = "",
        val reasons: List<String> = emptyList(),
        @SerialName("security_related") val securityRelated: Boolean = false,
        @SerialName("breaking_change_possible") val breakingChangePossible: Boolean = false,
    )

    private companion object {
        const val MODEL = "gemini-2.0-flash"
        const val MAX_SUMMARY_LENGTH = 600
        const val MAX_REASONS = 8

        val SECURITY_TERMS = listOf(
            "security", "vulnerab", "cve-", "exploit", "patch", "malware",
            "xss", "csrf", "injection", "authentication bypass",
        )

        val SCHEMA = GeminiSchema(
            type = "OBJECT",
            properties = mapOf(
                "summary" to GeminiSchema(type = "STRING"),
                "importance" to GeminiSchema(type = "STRING"),
                "reasons" to GeminiSchema(type = "ARRAY", items = GeminiSchema(type = "STRING")),
                "security_related" to GeminiSchema(type = "BOOLEAN"),
                "breaking_change_possible" to GeminiSchema(type = "BOOLEAN"),
            ),
            propertyOrdering = listOf(
                "summary", "importance", "reasons", "security_related", "breaking_change_possible",
            ),
        )
    }
}
