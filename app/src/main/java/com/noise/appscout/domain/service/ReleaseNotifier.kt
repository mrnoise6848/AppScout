package com.noise.appscout.domain.service

import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.TrackedApp

/**
 * Port through which the domain announces that a tracked app has a new release.
 *
 * Keeping this as an interface lets the notification decision (and its deduplication) be tested
 * without an Android notification manager.
 */
interface ReleaseNotifier {

    /** Posts a user-visible notification about [release] for [app]. Must never throw. */
    fun notifyNewRelease(app: TrackedApp, release: Release, installedVersion: String?)
}
