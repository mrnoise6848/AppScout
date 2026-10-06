package com.noise.appscout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.noise.appscout.core.ui.navigation.AppScoutNavHost
import com.noise.appscout.ui.theme.AppScoutTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppScoutTheme {
                AppScoutNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
