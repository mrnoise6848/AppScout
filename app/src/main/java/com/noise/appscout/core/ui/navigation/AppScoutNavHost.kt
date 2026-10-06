package com.noise.appscout.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.noise.appscout.feature.home.HomeRoute
import com.noise.appscout.feature.settings.SettingsRoute

/**
 * Single navigation graph for the app.
 *
 * Destinations are registered as they are implemented phase by phase; [Routes] declares the full
 * type-safe route set up front so screens never invent ad-hoc string routes.
 */
@Composable
fun AppScoutNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Home,
        modifier = modifier,
    ) {
        composable<Routes.Home> {
            HomeRoute(
                onOpenSettings = { navController.navigate(Routes.Settings) },
            )
        }

        composable<Routes.Settings> {
            SettingsRoute(onBack = { navController.popBackStack() })
        }
    }
}
