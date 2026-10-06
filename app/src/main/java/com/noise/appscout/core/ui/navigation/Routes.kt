package com.noise.appscout.core.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
@Serializable
sealed interface Routes {

    @Serializable
    data object Home : Routes

    @Serializable
    data object SelectInstalledApp : Routes

    @Serializable
    data class AddApp(val packageName: String) : Routes

    @Serializable
    data class AppDetails(val trackedAppId: String) : Routes

    @Serializable
    data class ReleaseDetails(val trackedAppId: String) : Routes

    @Serializable
    data object Settings : Routes
}
