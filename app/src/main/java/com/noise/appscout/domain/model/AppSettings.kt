package com.noise.appscout.domain.model

/** User facing preferences. The Gemini API key is not part of this model; it is stored securely. */
data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val backgroundRefreshEnabled: Boolean = true,
    val aiEnabled: Boolean = true,
    val includePrereleases: Boolean = false,
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}
