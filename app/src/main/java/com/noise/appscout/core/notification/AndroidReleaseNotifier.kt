package com.noise.appscout.core.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.noise.appscout.MainActivity
import com.noise.appscout.R
import com.noise.appscout.domain.model.Release
import com.noise.appscout.domain.model.TrackedApp
import com.noise.appscout.domain.service.ReleaseNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Android implementation of [ReleaseNotifier].
 *
 * Uses a stable notification id derived from the tracked app, so a re-post replaces the previous
 * notification instead of stacking duplicates. The tap opens AppScout on the app's detail screen.
 */
class AndroidReleaseNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReleaseNotifier {

    override fun notifyNewRelease(app: TrackedApp, release: Release, installedVersion: String?) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        NotificationChannels.ensureCreated(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_TRACKED_APP_ID, app.id)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            app.id.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = context.getString(R.string.notification_new_release_title, app.appName)
        val body = context.getString(
            R.string.notification_new_release_body,
            app.appName,
            installedVersion ?: context.getString(R.string.version_unknown),
            release.tagName,
        )

        val notification = Notification.Builder(context, NotificationChannels.UPDATE_UPDATES)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        manager.notify(app.id.hashCode(), notification)
    }
}
