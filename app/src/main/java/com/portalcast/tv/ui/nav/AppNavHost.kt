package com.portalcast.tv.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.produceState
import com.portalcast.tv.data.model.EpgData
import com.portalcast.tv.di.AppContainer
import com.portalcast.tv.playback.PlaybackHolder
import com.portalcast.tv.playback.PlaybackSession
import com.portalcast.tv.ui.home.HomeScreen
import com.portalcast.tv.ui.movies.MovieDetailScreen
import com.portalcast.tv.ui.series.SeriesDetailScreen
import com.portalcast.tv.ui.auth.PairingScreen
import com.portalcast.tv.ui.auth.PasswordLoginScreen
import com.portalcast.tv.ui.auth.ServerUrlScreen
import com.portalcast.tv.ui.settings.SettingsScreen

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
        PlaceholderScreen(title = "PortalCast", subtitle = "Loading…")
        return
    }

    // Decided once: changing NavHost's startDestination later rebuilds the graph mid-navigation
    // (crashed on pairing success). Later session changes are handled by resetTo below.
    // Must be a declared route pattern (Routes.HOME, not "home/discover"); the tab arg defaults.
    val start = remember {
        when {
            session != null -> Routes.HOME
            serverUrl != null -> Routes.PAIR
            else -> Routes.SERVER
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    LaunchedEffect(session, currentRoute) {
        val onAuthRoute = currentRoute in Routes.authRoutes
        if (session != null && onAuthRoute) nav.resetTo(Routes.home())
        if (session == null && currentRoute != null && !onAuthRoute) nav.resetTo(if (serverUrl != null) Routes.PAIR else Routes.SERVER)
    }

    // A card opened from a person's page or More Like This: the resolved item is kept so
    // the detail page starts from it (catalog id, art) like one opened from Home.
    val openMovieFromCard: (com.portalcast.tv.data.model.MediaItem) -> Unit = { item ->
        com.portalcast.tv.ui.SelectedItemHolder.put(item); nav.navigate(Routes.movieDetail(item.id, item.categoryId))
    }
    val openSeriesFromCard: (com.portalcast.tv.data.model.MediaItem) -> Unit = { item ->
        com.portalcast.tv.ui.SelectedItemHolder.put(item); nav.navigate(Routes.seriesDetail(item.id, item.categoryId))
    }

    // The viewer's Not interested titles, for Home's rows (ui/components/TitleMenu.kt).
    LaunchedEffect(session) {
        if (session == null) com.portalcast.tv.ui.components.HiddenTitles.set(emptySet())
        else container.discoverRepository.hiddenIds().onSuccess { com.portalcast.tv.ui.components.HiddenTitles.set(it) }
    }
    // The title menu (hold Center on a title card), for every screen.
    var titleMenu by remember { mutableStateOf<com.portalcast.tv.ui.components.TitleMenuTarget?>(null) }
    titleMenu?.let { t -> com.portalcast.tv.ui.components.TitleMenuDialog(t, onDismiss = { titleMenu = null }) }
    androidx.compose.runtime.CompositionLocalProvider(com.portalcast.tv.ui.components.LocalTitleMenu provides { titleMenu = it }) {
    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SERVER) {
            ServerUrlScreen(onConnected = {
                // Pairing becomes the signed-out root; Back from it leaves the app, not back to the URL form.
                nav.navigate(Routes.PAIR) {
                    popUpTo(Routes.SERVER) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }
        composable(Routes.PAIR) {
            PairingScreen(
                onUsePassword = { nav.navigate(Routes.LOGIN) { launchSingleTop = true } },
                // Drop the pairing entry: its code belongs to the old server.
                onChangeServer = { nav.navigate(Routes.SERVER) { popUpTo(Routes.PAIR) { inclusive = true } } },
            )
        }
        composable(Routes.LOGIN) {
            PasswordLoginScreen(onUsePairing = {
                if (!nav.popBackStack(Routes.PAIR, inclusive = false)) nav.navigate(Routes.PAIR)
            })
        }

        composable(
            Routes.HOME,
            arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = HomeTab.DISCOVER.route }),
        ) { entry ->
            val tab = HomeTab.fromRoute(entry.arguments?.getString("tab"))
            HomeScreen(
                initialTab = tab,
                onOpenMovie = { item -> nav.navigate(Routes.movieDetail(item.id, item.categoryId)) },
                onOpenSeries = { item -> nav.navigate(Routes.seriesDetail(item.id, item.categoryId)) },
                onPlay = { nav.navigate(Routes.PLAYER) { launchSingleTop = true } },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onOpenPerson = { id -> nav.navigate(Routes.person(id)) },
            )
        }

        composable(
            Routes.MOVIE_DETAIL,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("category") { type = NavType.StringType; defaultValue = "*" },
            ),
        ) { entry ->
            MovieDetailScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                category = entry.arguments?.getString("category") ?: "*",
                onPlay = { nav.navigate(Routes.PLAYER) { launchSingleTop = true } },
                onOpenPerson = { id -> nav.navigate(Routes.person(id)) },
                onOpenMovie = openMovieFromCard,
                onOpenSeries = openSeriesFromCard,
            )
        }

        composable(
            Routes.SERIES_DETAIL,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("category") { type = NavType.StringType; defaultValue = "*" },
            ),
        ) { entry ->
            SeriesDetailScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                category = entry.arguments?.getString("category") ?: "*",
                onPlay = { nav.navigate(Routes.PLAYER) { launchSingleTop = true } },
                onOpenPerson = { id -> nav.navigate(Routes.person(id)) },
                onOpenMovie = openMovieFromCard,
                onOpenSeries = openSeriesFromCard,
            )
        }

        composable(Routes.PERSON, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            com.portalcast.tv.ui.person.PersonScreen(
                id = entry.arguments?.getInt("id") ?: 0,
                onOpenMovie = openMovieFromCard,
                onOpenSeries = openSeriesFromCard,
            )
        }

        composable(Routes.PLAYER) {
            // Session comes from PlaybackHolder (set by the caller before navigating). Phase 2a may pass
            // nowNext (EPG banner) and refreshLiveChannel (401/403 recovery for live) here.
            // EPG for the live now/next banner; only fetched (cached, 30 min) for live sessions.
            val epg by produceState<EpgData?>(initialValue = null) {
                if (PlaybackHolder.current is PlaybackSession.Live) value = container.liveRepository.epg().getOrNull()
            }
            com.portalcast.tv.ui.player.PlayerScreen(
                onBack = { if (!nav.popBackStack()) nav.resetTo(Routes.home()) },
                nowNext = { ch -> epg?.nowNext(ch.id) ?: (null to null) },
                refreshLiveChannel = { ch ->
                    container.liveRepository.channels(forceRefresh = true).getOrNull()?.firstOrNull { it.id == ch.id }
                },
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
    }
}

/** Navigate and drop everything else from the back stack. */
fun NavHostController.resetTo(route: String) {
    navigate(route) {
        // Pop by graph id: findStartDestination() throws if the start route has no matching node.
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
