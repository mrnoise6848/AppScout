package com.noise.appscout.core.network

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Runs [block] with bounded exponential backoff and jitter.
 *
 * Only transient failures are retried; caller errors (404, malformed payloads) fail immediately so
 * the user never waits on pointless retries. Cancellation is never swallowed.
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int = NetworkConfig.MAX_ATTEMPTS,
    initialDelayMs: Long = NetworkConfig.INITIAL_BACKOFF_MS,
    shouldRetry: (Throwable) -> Boolean = { it is IOException || it is TransientHttpException },
    block: suspend (attempt: Int) -> T,
): T {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1" }

    var delayMs = initialDelayMs
    var lastFailure: Throwable? = null

    repeat(maxAttempts) { attempt ->
        try {
            return block(attempt)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            lastFailure = t
            if (!shouldRetry(t) || attempt == maxAttempts - 1) throw t
            val jitter = if (delayMs > 1) Random.nextLong(delayMs / 2) else 0L
            delay(delayMs + jitter)
            delayMs = (delayMs * 2).coerceAtMost(NetworkConfig.MAX_BACKOFF_MS)
        }
    }

    throw lastFailure ?: IllegalStateException("retryWithBackoff completed without a result")
}
