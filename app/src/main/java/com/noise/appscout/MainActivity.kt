package com.noise.appscout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.noise.appscout.core.ui.navigation.AppScoutNavHost
import com.noise.appscout.ui.theme.AppScoutTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Set when the app is launched from a release notification. */
    private var openedFromTrackedApp by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedFromTrackedApp = intent?.getStringExtra(EXTRA_TRACKED_APP_ID)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            AppScoutTheme {
                AppScoutNavHost(
                    modifier = Modifier.fillMaxSize(),
                    openTrackedAppId = openedFromTrackedApp,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openedFromTrackedApp = intent.getStringExtra(EXTRA_TRACKED_APP_ID)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1001

        const val EXTRA_TRACKED_APP_ID = "com.noise.appscout.TRACKED_APP_ID"
    }
}
