package com.portalcast.tv.ui.home

import com.portalcast.tv.ui.components.AmbientBackdrop
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Job
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.components.PortalcastLogo
import com.portalcast.tv.ui.components.PortalcastSymbol
import com.portalcast.tv.ui.SelectedItemHolder
import com.portalcast.tv.ui.discover.DiscoverScreen
import com.portalcast.tv.ui.search.SearchScreen
import com.portalcast.tv.ui.live.LiveScreen
import com.portalcast.tv.ui.movies.MoviesScreen
import com.portalcast.tv.ui.nav.HomeTab
import com.portalcast.tv.ui.series.SeriesScreen
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Height of the tab bar along the top. */
private val TabBarHeight = 72.dp

/** How long focus rests on a tab before the screen follows it (Center switches at once). */
private const val TAB_SWITCH_DELAY_MS = 350L

/** The tabs left to right (design-system.md, Navigation); Settings follows them. */
private val TopTabs = listOf(HomeTab.DISCOVER, HomeTab.LIVE, HomeTab.MOVIES, HomeTab.SERIES, HomeTab.SPORTS, HomeTab.SEARCH, HomeTab.MY_LIST)

/**
 * Home: text tabs along the top (Home, Live TV, Movies, Series, Search, My List, Settings; the
 * design system's Fjord-style layout) over the selected tab's content. Up from the content's
 * top reaches the tabs, Down goes back in. Resting on a tab for [TAB_SWITCH_DELAY_MS] shows its
 * screen (Center at once) and remembers it in `LocalPrefs.lastTab`. Back: first to the tabs,
 * then out of the app.
 *
 * @param initialTab the route's tab; the default (Discover) yields to the remembered tab.
 * @param onOpenMovie / [onOpenSeries] navigate to the detail routes (the item is already in [SelectedItemHolder]).
 * @param onPlay navigate to the player (the session is already in `PlaybackHolder`).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    initialTab: HomeTab,
    onOpenMovie: (MediaItem) -> Unit,
    onOpenSeries: (MediaItem) -> Unit,
    onPlay: () -> Unit,
    onOpenPerson: (Int) -> Unit = {},
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = context.appContainer.localPrefs
    val scope = rememberCoroutineScope()

    val savedTab by produceState<HomeTab?>(initialValue = null) {
        value = runCatching { prefs.lastTab.first() }.getOrNull()?.let { HomeTab.fromRoute(it) } ?: HomeTab.DISCOVER
    }
    var tab by rememberSaveable { mutableStateOf<HomeTab?>(null) }
    LaunchedEffect(savedTab) {
        val saved = savedTab
        if (tab == null && saved != null) tab = if (initialTab != HomeTab.DISCOVER) initialTab else saved
    }

    // Was Home shown before in this back-stack entry? Then we are coming back from a child
    // screen and the content should restore its own focus.
    var visited by rememberSaveable { mutableStateOf(false) }
    var restorePending by remember { mutableStateOf(visited) }
    var tabsFocused by remember { mutableStateOf(false) }
    /** The Movies or Series tab showing its per-category browse instead of its rows. */
    var categories by rememberSaveable { mutableStateOf<HomeTab?>(null) }
    /** Sports' "Find channel": a Live TV search handed to the Search tab once. */
    var searchFor by remember { mutableStateOf<String?>(null) }
    var contentFocused by remember { mutableStateOf(false) }
    val tabRequesters = remember { HomeTab.entries.associateWith { FocusRequester() } }
    val settingsRequester = remember { FocusRequester() }

    val current = tab
    fun focusTabs() {
        val target = current?.let { tabRequesters[it] } ?: return
        runCatching { target.requestFocus() }
    }

    val contentRequester = remember { FocusRequester() }
    LaunchedEffect(current != null) {
        if (current == null) return@LaunchedEffect
        if (restorePending) {
            delay(400)
            restorePending = false
            // Back from a child screen: the tab should have restored its own focus. If it didn't,
            // go into the content, not the tabs (landing on the tabs on Back looked like a bug).
            if (!contentFocused && !tabsFocused) {
                runCatching { contentRequester.requestFocus() }
                withFrameNanos { }
                if (!contentFocused && !tabsFocused) focusTabs()
            }
        } else {
            withFrameNanos { }
            focusTabs()
        }
        visited = true
    }

    // Focus recovery: when the focused element disappears (e.g. a Retry button replaced by the
    // content it loaded) nothing on screen has focus and the remote does nothing visible.
    // Move focus into the content (first focusable child), falling back to the tabs.
    LaunchedEffect(contentFocused, tabsFocused, restorePending) {
        if (current == null || contentFocused || tabsFocused || restorePending) return@LaunchedEffect
        delay(250)
        runCatching { contentRequester.requestFocus() }
        withFrameNanos { }
        if (!contentFocused && !tabsFocused) focusTabs()
    }

    BackHandler {
        if (tabsFocused) (context as? Activity)?.finish() else focusTabs()
    }

    if (current == null) return

    val openMovie: (MediaItem) -> Unit = { item -> SelectedItemHolder.put(item); onOpenMovie(item) }
    val openSeries: (MediaItem) -> Unit = { item -> SelectedItemHolder.put(item); onOpenSeries(item) }

    fun select(t: HomeTab) {
        if (t == tab) return
        tab = t
        restorePending = false
        scope.launch { runCatching { prefs.setLastTab(t.route) } }
    }

    // A tab switch follows focus once it rests; a newer focus cancels the pending one. Held
    // outside Compose state: a d-pad step across the tabs recomposes nothing.
    val pendingSwitch = remember { arrayOfNulls<Job>(1) }
    fun focusOnTab(t: HomeTab) {
        pendingSwitch[0]?.cancel()
        if (t == tab) return
        pendingSwitch[0] = scope.launch { delay(TAB_SWITCH_DELAY_MS); select(t) }
    }

    com.portalcast.tv.ui.components.IptvStatusPoller()
    Box(Modifier.fillMaxSize()) {
        // Discover has its own focus-following hero; every other tab gets the rotating
        // ambient backdrop (the webui's AmbientBackdrop).
        if (current != HomeTab.DISCOVER && !(current == HomeTab.MOVIES && categories == null) && !(current == HomeTab.SERIES && categories == null)) AmbientBackdrop()
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(TabBarHeight)
                    .background(Brush.verticalGradient(0f to AppColors.Background, 1f to AppColors.Background.copy(alpha = 0.6f)))
                    .onFocusChanged { tabsFocused = it.hasFocus }
                    // Entering the tabs (Up from the content) always lands on the CURRENT tab.
                    .focusProperties {
                        enter = { tabRequesters.getValue(current) }
                        exit = { dir -> if (dir == FocusDirection.Down) FocusRequester.Default else FocusRequester.Cancel }
                    }
                    .focusGroup()
                    .padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PortalcastLogo(height = 26.dp)
                Spacer(Modifier.width(28.dp))
                for (t in TopTabs) {
                    TopTab(
                        label = t.title,
                        selected = t == current,
                        // Center switches at once; resting on a tab switches after a moment.
                        onClick = { pendingSwitch[0]?.cancel(); select(t) },
                        onFocused = { focusOnTab(t) },
                        modifier = Modifier.focusRequester(tabRequesters.getValue(t)),
                    )
                }
                Spacer(Modifier.weight(1f))
                TopTab(
                    label = "Settings",
                    selected = false,
                    onClick = onOpenSettings,
                    onFocused = { pendingSwitch[0]?.cancel() },
                    modifier = Modifier.focusRequester(settingsRequester),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onFocusChanged { contentFocused = it.hasFocus }
                    .focusRequester(contentRequester)
                    // Focus leaves the content only Up, and then always onto the CURRENT tab. Without
                    // this, Up from the top landed on whichever tab was nearest above, and Down on the
                    // last row (or Left/Right at an edge) had nowhere sensible to go.
                    .focusProperties {
                        exit = { dir -> if (dir == FocusDirection.Up) tabRequesters.getValue(current) else FocusRequester.Cancel }
                    }
                    .focusGroup(),
            ) {
                when (current) {
                    HomeTab.SEARCH -> SearchScreen(
                        onOpenMovie = openMovie, onOpenSeries = openSeries, onPlay = onPlay, onOpenPerson = onOpenPerson, restoreFocus = restorePending,
                        liveQuery = searchFor, onLiveQueryUsed = { searchFor = null },
                    )
                    HomeTab.SPORTS -> com.portalcast.tv.ui.sports.SportsScreen(
                        onPlay = onPlay,
                        onFindChannel = { q -> searchFor = q; select(HomeTab.SEARCH) },
                        restoreFocus = restorePending,
                    )
                    HomeTab.DISCOVER -> DiscoverScreen(onOpenMovie = openMovie, onOpenSeries = openSeries, onOpenSearch = { select(HomeTab.SEARCH) }, restoreFocus = restorePending)
                    HomeTab.LIVE -> LiveScreen(onPlay = onPlay, restoreFocus = restorePending)
                    HomeTab.MY_LIST -> com.portalcast.tv.ui.mylist.MyListScreen(onOpenMovie = openMovie, onOpenSeries = openSeries, restoreFocus = restorePending)
                    // Movies / Series: Home's layout for one type; Categories opens the per-category
                    // browse, Back returns (design-system.md, Screens).
                    HomeTab.MOVIES -> if (categories == HomeTab.MOVIES) {
                        BackHandler { categories = null }
                        MoviesScreen(onOpenMovie = openMovie, restoreFocus = restorePending)
                    } else {
                        DiscoverScreen(onOpenMovie = openMovie, onOpenSeries = openSeries, restoreFocus = restorePending, fixedType = "movie", onOpenCategories = { categories = HomeTab.MOVIES })
                    }
                    HomeTab.SERIES -> if (categories == HomeTab.SERIES) {
                        BackHandler { categories = null }
                        SeriesScreen(onOpenSeries = openSeries, restoreFocus = restorePending)
                    } else {
                        DiscoverScreen(onOpenMovie = openMovie, onOpenSeries = openSeries, restoreFocus = restorePending, fixedType = "series", onOpenCategories = { categories = HomeTab.SERIES })
                    }
                }
            }
        }

        // Internet down: IPTV cards are dimmed, local titles keep playing.
        com.portalcast.tv.ui.components.IptvOfflineBanner(
            Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = TabBarHeight + 8.dp),
        )
    }
}

/**
 * One tab: its name in `titleMedium`, the current one in `text` with a 2 high `primary` line
 * under it, the others `textMuted`; focus adds a light fill and the white ring.
 */
@Composable
private fun TopTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) AppColors.Text else AppColors.TextMuted,
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            focusedContentColor = AppColors.Text,
            pressedContainerColor = Color.White.copy(alpha = 0.12f),
            pressedContentColor = AppColors.Text,
        ),
    ) {
        Column(Modifier.width(IntrinsicSize.Max).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) AppColors.Primary else Color.Transparent))
        }
    }
}
