package com.portalcast.tv.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.portalcast.tv.di.AppContainer

/**
 * Session-gated navigation. Start destination is decided once the session store has
 * loaded: signed in → home, server known → pairing, nothing → server URL.
 * A session appearing/disappearing later (pairing succeeded, refresh died, logout)
 * re-routes and clears the back stack.
 *
 * Phase 0 wires placeholder screens; each later phase replaces the composable
 * inside its route and nothing else here.
 */
@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val isLoaded by container.sessionStore.isLoaded.collectAsStateWithLifecycle()
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val serverUrl by container.sessionStore.serverUrl.collectAsStateWithLifecycle()

    if (!isLoaded) {
        PlaceholderScreen(title = "Portalcast", subtitle = "Loading…")
        return
    }

    val start = when {
        session != null -> Routes.home()
        serverUrl != null -> Routes.PAIR
        else -> Routes.SERVER
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    LaunchedEffect(session, currentRoute) {
        val onAuthRoute = currentRoute in Routes.authRoutes
        if (session != null && onAuthRoute) nav.resetTo(Routes.home())
        if (session == null && currentRoute != null && !onAuthRoute) nav.resetTo(if (serverUrl != null) Routes.PAIR else Routes.SERVER)
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SERVER) { PlaceholderScreen("Server URL", "Phase 1b: enter server address") }
        composable(Routes.PAIR) { PlaceholderScreen("Pair this TV", "Phase 1b: QR + code") }
        composable(Routes.LOGIN) { PlaceholderScreen("Sign in", "Phase 1b: email + password") }

        composable(
            Routes.HOME,
            arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = HomeTab.DISCOVER.route }),
        ) { entry ->
            val tab = HomeTab.fromRoute(entry.arguments?.getString("tab"))
            PlaceholderScreen("Home · ${tab.title}", "Phase 2a: nav rail + ${tab.title}")
        }

        composable(
            Routes.MOVIE_DETAIL,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("category") { type = NavType.StringType; defaultValue = "*" },
            ),
        ) { entry ->
            PlaceholderScreen("Movie ${entry.arguments?.getString("id")}", "Phase 2a: detail")
        }

        composable(
            Routes.SERIES_DETAIL,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("category") { type = NavType.StringType; defaultValue = "*" },
            ),
        ) { entry ->
            PlaceholderScreen("Series ${entry.arguments?.getString("id")}", "Phase 2a: seasons + episodes")
        }

        composable(Routes.PLAYER) { PlaceholderScreen("Player", "Phase 1c: ExoPlayer") }
        composable(Routes.SETTINGS) { PlaceholderScreen("Settings", "Phase 1b") }
    }
}

/** Navigate and drop everything else from the back stack. */
fun NavHostController.resetTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}
