package com.noise.appscout.core.ui.components

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Returns a formatter that renders an epoch-millis timestamp as a localized date (with time). */
@Composable
fun rememberDateFormatter(): (Long) -> String {
    val context = LocalContext.current
    return remember(context) {
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        val dateTime = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
        val zone = ZoneId.systemDefault()
        val formatter: (Long) -> String = { millis ->
            val value = Instant.ofEpochMilli(millis).atZone(zone)
            if (value.hour == 0 && value.minute == 0) value.format(date) else value.format(dateTime)
        }
        formatter
    }
}

/**
 * Opens [url] in the user's browser (or any app that handles the link).
 *
 * AppScout never downloads or installs anything itself (ADR-006): leaving the app for the
 * release page is the intended behaviour.
 */
fun openInBrowser(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
