package com.noise.appscout.domain.model

/** An Android application the user chose to monitor. */
data class TrackedApp(
    val id: String,
    val packageName: String,
    val appName: String,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    val source: AppSource?,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** Tag of the last release the user was notified about; prevents duplicate notifications. */
    val lastNotifiedTag: String?,
) {
    /** Icon lookup key, resolved through `PackageManager` by the presentation layer. */
    val iconReference: String get() = packageName
}
