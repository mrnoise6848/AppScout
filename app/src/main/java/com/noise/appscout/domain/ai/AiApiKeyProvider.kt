package com.noise.appscout.domain.ai

/**
 * Read-only access to the user's AI API key.
 *
 * The domain only ever asks for the key; where it is stored (Android Keystore vault in
 * production, an in-memory fake in tests) is an infrastructure detail.
 */
interface AiApiKeyProvider {

    /** @return the stored key, or `null` when the user has not provided one. */
    fun readApiKey(): String?
}
