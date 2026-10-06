package com.noise.appscout.domain.model

/**
 * A user-facing Android application that can be launched from the device launcher.
 *
 * The icon is deliberately not part of this model: domain code must not depend on Android
 * graphics types, so the presentation layer resolves an icon from [packageName].
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long?,
) {
    /** Icon lookup key. */
    val iconReference: String get() = packageName
}
