package com.portalcast.tv.ui.home

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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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

private val RailCompact = 76.dp
private val RailExpanded = 232.dp

/**
 * Home: a left nav rail (Discover, Live TV, Movies, Series, Settings) over the selected
 * tab's content. The rail is compact (icons) while content has focus and expands with
 * labels when it has focus; moving focus onto a tab swaps the content and remembers the
 * tab in `LocalPrefs.lastTab`. D-pad Left from the content's leftmost column reaches the
 * rail. Back: first to the rail, then out of the app.
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
    var railFocused by remember { mutableStateOf(false) }
    var contentFocused by remember { mutableStateOf(false) }
    val tabRequesters = remember { HomeTab.entries.associateWith { FocusRequester() } }
    val settingsRequester = remember { FocusRequester() }

    val current = tab
    fun focusRail() {
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
            // go into the content, not the rail (opening the rail on Back looked like a bug).
            if (!contentFocused && !railFocused) {
                runCatching { contentRequester.requestFocus() }
                withFrameNanos { }
                if (!contentFocused && !railFocused) focusRail()
            }
        } else {
            withFrameNanos { }
            focusRail()
        }
        visited = true
    }

    // Focus recovery: when the focused element disappears (e.g. a Retry button replaced by the
    // content it loaded) nothing on screen has focus and the remote does nothing visible.
    // Move focus into the content (first focusable child), falling back to the rail.
    LaunchedEffect(contentFocused, railFocused, restorePending) {
        if (current == null || contentFocused || railFocused || restorePending) return@LaunchedEffect
        delay(250)
        runCatching { contentRequester.requestFocus() }
        withFrameNanos { }
        if (!contentFocused && !railFocused) focusRail()
    }

    BackHandler {
        if (railFocused) (context as? Activity)?.finish() else focusRail()
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

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = RailCompact)
                .onFocusChanged { contentFocused = it.hasFocus }
                .focusRequester(contentRequester)
                .focusGroup(),
        ) {
            when (current) {
                HomeTab.SEARCH -> SearchScreen(onOpenMovie = openMovie, onOpenSeries = openSeries, onPlay = onPlay)
                HomeTab.DISCOVER -> DiscoverScreen(onOpenMovie = openMovie, onOpenSeries = openSeries, onOpenSearch = { select(HomeTab.SEARCH) }, restoreFocus = restorePending)
                HomeTab.LIVE -> LiveScreen(onPlay = onPlay, restoreFocus = restorePending)
                HomeTab.MOVIES -> MoviesScreen(onOpenMovie = openMovie, restoreFocus = restorePending)
                HomeTab.SERIES -> SeriesScreen(onOpenSeries = openSeries, restoreFocus = restorePending)
            }
        }

        // While the rail is open it overlays the content: dim everything behind it so text cut
        // off by the rail's edge doesn't read as broken layout (found on the emulator).
        val scrim by animateFloatAsState(if (railFocused) 0.55f else 0f, label = "railScrim")
        if (scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))

        val railWidth by animateDpAsState(if (railFocused) RailExpanded else RailCompact, label = "rail")
        Column(
            Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(
                    if (railFocused) Brush.horizontalGradient(0f to AppColors.Background, 1f to AppColors.Background)
                    else Brush.horizontalGradient(0f to AppColors.Background, 1f to AppColors.Background.copy(alpha = 0.85f)),
                )
                .onFocusChanged { railFocused = it.hasFocus }
                // Entering the rail (Left from content) always lands on the CURRENT tab, never on
                // whichever item is geometrically nearest.
                .focusProperties { enter = { tabRequesters.getValue(current) } }
                .padding(horizontal = 12.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (railFocused) "Portalcast" else "P",
                style = MaterialTheme.typography.titleLarge,
                color = AppColors.Primary,
                maxLines = 1,
                modifier = Modifier.padding(start = 14.dp, bottom = 20.dp),
            )
            for (t in HomeTab.entries) {
                RailItem(
                    icon = t.icon,
                    label = t.title,
                    selected = t == current,
                    expanded = railFocused,
                    onClick = { select(t) },
                    // Tabs switch on Center only. Switching on focus made Up/Down (or a Left that
                    // landed on another item) change tabs by itself, which felt like the UI jumping.
                    modifier = Modifier.focusRequester(tabRequesters.getValue(t)),
                )
            }
            Spacer(Modifier.weight(1f))
            RailItem(
                icon = Icons.Filled.Settings,
                label = "Settings",
                selected = false,
                expanded = railFocused,
                onClick = onOpenSettings,
                modifier = Modifier.focusRequester(settingsRequester),
            )
        }
    }
}

private val HomeTab.icon: ImageVector
    get() = when (this) {
        HomeTab.SEARCH -> Icons.Filled.Search
        HomeTab.DISCOVER -> Icons.Filled.Home
        HomeTab.LIVE -> Icons.Filled.PlayArrow
        HomeTab.MOVIES -> Icons.Filled.Star
        HomeTab.SERIES -> Icons.AutoMirrored.Filled.List
    }

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) AppColors.Primary.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = if (selected) AppColors.Primary else AppColors.TextMuted,
            focusedContainerColor = AppColors.Primary,
            focusedContentColor = AppColors.OnPrimary,
            pressedContainerColor = AppColors.Primary,
            pressedContentColor = AppColors.OnPrimary,
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
            if (expanded) {
                Spacer(Modifier.width(14.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}
