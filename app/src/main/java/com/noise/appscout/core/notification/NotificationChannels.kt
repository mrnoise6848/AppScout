package com.noise.appscout.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Creates and owns the single notification channel used by AppScout. */
object NotificationChannels {

    const val UPDATE_UPDATES = "release_updates"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(UPDATE_UPDATES) != null) return

        val channel = NotificationChannel(
            UPDATE_UPDATES,
            context.getString(com.noise.appscout.R.string.notification_channel_updates),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(com.noise.appscout.R.string.notification_channel_updates_description)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }
}
