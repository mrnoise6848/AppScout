package com.noise.appscout.domain.ai

import com.noise.appscout.domain.model.AiSummary
import com.noise.appscout.domain.model.Release

/** Outcome of asking an [AiSummaryProvider] for a structured summary of a release. */
sealed interface AiSummaryResult {

    data class Success(val summary: AiSummary) : AiSummaryResult

    /** Summaries are switched off in settings; core functionality is unaffected. */
    data object Disabled : AiSummaryResult

    /** No API key is stored on this device. */
    data object NoApiKey : AiSummaryResult

    /** The stored key was rejected by the provider. */
    data object InvalidApiKey : AiSummaryResult

    data object RateLimited : AiSummaryResult

    data object NetworkError : AiSummaryResult

    /** The provider answered, but the payload violated the output contract. */
    data object InvalidOutput : AiSummaryResult
}

/**
 * Replaceable AI boundary (ADR-005).
 *
 * The only production implementation is the BYOK Gemini provider; tests inject a fake. Nothing in
 * the core tracking flow may depend on this interface succeeding.
 */
interface AiSummaryProvider {

    suspend fun summarize(release: Release, appName: String): AiSummaryResult
}
