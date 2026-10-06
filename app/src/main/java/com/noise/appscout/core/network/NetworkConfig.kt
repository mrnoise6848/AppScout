package com.noise.appscout.core.network

/** Central place for HTTP behaviour so polling stays conservative and predictable. */
object NetworkConfig {

    const val GITHUB_BASE_URL = "https://api.github.com/"
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 20L
    const val WRITE_TIMEOUT_SECONDS = 20L

    const val MAX_ATTEMPTS = 3
    const val INITIAL_BACKOFF_MS = 500L
    const val MAX_BACKOFF_MS = 8_000L

    const val USER_AGENT = "AppScout/1.0 (android; release checker)"
}

/** A response with a transient (5xx) status. Retried with backoff before being surfaced. */
class TransientHttpException(val code: Int) : Exception("GitHub returned HTTP $code")

/** Raised when GitHub reports that the unauthenticated rate limit is exhausted. */
class RateLimitedException(val retryAfterSeconds: Long?) : Exception("GitHub rate limit exhausted")
