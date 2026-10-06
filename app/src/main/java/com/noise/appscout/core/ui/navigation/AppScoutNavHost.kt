package com.noise.appscout.core.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
    openTrackedAppId: String? = null,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(openTrackedAppId) {
        if (!openTrackedAppId.isNullOrBlank()) {
            navController.navigate(Routes.AppDetails(openTrackedAppId))
        }
    }

    // Deliberately short, direction-communicating transitions: forward pushes in from the right,
    // back returns the previous screen from the left. Nothing decorative, nothing that delays
    // reading of release information.
    NavHost(
        navController = navController,
        startDestination = Routes.Home,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(NAV_TRANSITION_MS),
            ) + fadeIn(tween(NAV_TRANSITION_MS))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(NAV_TRANSITION_MS),
            ) + fadeOut(tween(NAV_TRANSITION_MS))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(NAV_TRANSITION_MS),
            ) + fadeIn(tween(NAV_TRANSITION_MS))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(NAV_TRANSITION_MS),
            ) + fadeOut(tween(NAV_TRANSITION_MS))
        },
    ) {
        composable<Routes.Home> {
            HomeRoute(
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onAddApp = { navController.navigate(Routes.SelectInstalledApp) },
                onAppClick = { id -> navController.navigate(Routes.AppDetails(id)) },
            )
        }

        composable<Routes.Settings> {
            SettingsRoute(onBack = { navController.popBackStack() })
        }

        composable<Routes.SelectInstalledApp> {
            com.noise.appscout.feature.addapp.SelectInstalledAppRoute(
                onBack = { navController.popBackStack() },
                onSelect = { app ->
                    navController.navigate(Routes.AddApp(app.packageName))
                },
            )
        }

        composable<Routes.AddApp> { backStackEntry ->
            com.noise.appscout.feature.addapp.AddAppRoute(
                onBack = { navController.popBackStack() },
                onTracked = { navController.popBackStack(Routes.Home, inclusive = false) },
            )
        }

        composable<Routes.AppDetails> { entry ->
            val args = entry.toRoute<Routes.AppDetails>()
            com.noise.appscout.feature.appdetails.AppDetailsRoute(
                onBack = { navController.popBackStack() },
                onOpenReleaseDetails = { navController.navigate(Routes.ReleaseDetails(args.trackedAppId)) },
            )
        }

        composable<Routes.ReleaseDetails> {
            com.noise.appscout.feature.releasedetails.ReleaseDetailsRoute(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private const val NAV_TRANSITION_MS = 220
