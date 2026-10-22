@file:OptIn(ExperimentalComposeUiApi::class)

package com.portalcast.tv.ui.search

import com.portalcast.tv.ui.components.PersonTile
import com.portalcast.tv.ui.components.PersonEntry
import com.portalcast.tv.data.model.PersonCredit
import com.portalcast.tv.data.model.PersonHit
import androidx.compose.foundation.lazy.LazyListScope
import com.portalcast.tv.ui.components.keepColumn
import com.portalcast.tv.ui.components.gridItem
import com.portalcast.tv.ui.components.rememberGridFocus
import com.portalcast.tv.ui.components.rememberedIn
import com.portalcast.tv.ui.components.rememberFocusMemory
import com.portalcast.tv.ui.components.focusMemory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.components.EmptyPanel
import com.portalcast.tv.ui.components.ErrorPanel
import com.portalcast.tv.ui.components.LoadingPanel
import com.portalcast.tv.ui.components.MediaCard
import com.portalcast.tv.ui.components.PosterRow
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.components.TvTextField
import com.portalcast.tv.ui.components.rememberServerUrl
import com.portalcast.tv.ui.live.ChannelRow
import com.portalcast.tv.ui.live.EpgLookup
import com.portalcast.tv.ui.theme.AppColors
import com.portalcast.tv.util.TitleKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay

/**
 * Global search across every category: movies and series (server, `category=*`), people
 * (cast and crew by name) and live channels. Versions of the same title are grouped like
 * Discover; a filter narrows the results. When the query clearly names a person, People and
 * "Movies & series with X" come first. There is no per-category search anywhere else.
 */
@Composable
fun SearchScreen(
    onOpenMovie: (MediaItem) -> Unit,
    onOpenSeries: (MediaItem) -> Unit,
    onPlay: () -> Unit,
    onOpenPerson: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    /** True when coming back from a detail/player: focus the result that was opened. */
    restoreFocus: Boolean = false,
    /** Search Live TV for this (Sports' "Find channel"), then [onLiveQueryUsed]. */
    liveQuery: String? = null,
    onLiveQueryUsed: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    container.vodRepository, container.seriesRepository, container.liveRepository, container.userRepository,
                    discover = container.discoverRepository,
                    prefs = context.getSharedPreferences("search", android.content.Context.MODE_PRIVATE),
                )
            }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(liveQuery) {
        val q = liveQuery ?: return@LaunchedEffect
        vm.setFilter(SearchViewModel.Filter.LIVE, remember = false)
        vm.submit(q)
        onLiveQueryUsed()
    }

    // Back from a result: put focus on the card it was opened from, not on the search field.
    val restoreRequester = remember { FocusRequester() }
    val restoreKey = remember { if (restoreFocus) vm.lastFocusedKey else null }
    LaunchedEffect(restoreKey) {
        if (restoreKey == null) return@LaunchedEffect
        repeat(20) {
            withFrameNanos { }
            if (runCatching { restoreRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    val cardModifier: (String) -> Modifier = { key ->
        val base = Modifier.onFocusChanged { if (it.isFocused) vm.lastFocusedKey = key }
        if (key == restoreKey) base.focusRequester(restoreRequester) else base
    }

    LaunchedEffect(state.open) {
        when (val o = state.open ?: return@LaunchedEffect) {
            is SearchViewModel.Open.Movie -> onOpenMovie(o.item)
            is SearchViewModel.Open.Series -> onOpenSeries(o.item)
            is SearchViewModel.Open.Person -> onOpenPerson(o.id)
        }
        vm.onOpenHandled()
    }

    Column(modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 28.dp)) {
        TvTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            label = "Search",
            hint = "Movies, series, actors, channels…",
            imeAction = ImeAction.Search,
            onImeAction = { vm.submit() },
            modifier = Modifier.width(640.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(SearchViewModel.Filter.entries, key = { _, f -> f.name }) { _, f ->
                TvButton(
                    text = filterLabel(f, state),
                    onClick = { vm.setFilter(f) },
                    style = if (f == state.filter) TvButtonStyle.Primary else TvButtonStyle.Secondary,
                )
            }
        }

        when {
            state.searched == null && !state.loading -> RecentSearches(state.recent, onPick = vm::submit)
            state.loading && state.searched == null -> LoadingPanel(message = "Searching…")
            state.error != null -> ErrorPanel(state.error!!, onRetry = { vm.submit() })
            state.isEmptyResult -> EmptyPanel("Nothing found for \"${state.searched}\".")
            else -> when (state.filter) {
                SearchViewModel.Filter.ALL -> AllResults(state, vm, onPlay, cardModifier)
                SearchViewModel.Filter.MOVIES -> PosterGrid(state.movies, "No movies found.", { vm.openGroup(it, isSeries = false) }, vm::loadMoreMovies, { cardModifier("m|$it") })
                SearchViewModel.Filter.SERIES -> PosterGrid(state.series, "No series found.", { vm.openGroup(it, isSeries = true) }, vm::loadMoreSeries, { cardModifier("s|$it") })
                SearchViewModel.Filter.PEOPLE -> PeopleGrid(state.people, vm::openPerson) { cardModifier("p|$it") }
                SearchViewModel.Filter.LIVE -> ChannelList(state, vm, onPlay)
            }
        }
    }

    state.discoverVariants?.let { v ->
        val serverUrl by container.sessionStore.serverUrl.collectAsStateWithLifecycle()
        com.portalcast.tv.ui.discover.VariantPickerDialog(v, serverUrl, onChoose = vm::chooseDiscoverVariant, onDismiss = vm::dismissDiscoverVariants)
    }

    state.picker?.let { (group, _) ->
        VersionPicker(group = group, onChoose = vm::chooseVariant, onDismiss = vm::dismissPicker)
    }
}

private fun filterLabel(f: SearchViewModel.Filter, s: SearchViewModel.State): String {
    if (s.searched == null) return f.label
    val n = when (f) {
        SearchViewModel.Filter.ALL -> null
        SearchViewModel.Filter.MOVIES -> s.movies.groups.size
        SearchViewModel.Filter.SERIES -> s.series.groups.size
        SearchViewModel.Filter.PEOPLE -> s.people.size
        SearchViewModel.Filter.LIVE -> s.channels.size
    }
    val more = when (f) {
        SearchViewModel.Filter.MOVIES -> s.movies.hasMore
        SearchViewModel.Filter.SERIES -> s.series.hasMore
        else -> false
    }
    return if (n == null) f.label else "${f.label} ($n${if (more) "+" else ""})"
}

@Composable
private fun RecentSearches(recent: List<String>, onPick: (String) -> Unit) {
    if (recent.isEmpty()) {
        Text(
            "Search every category at once. Press OK on the field to type.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.TextMuted,
            modifier = Modifier.padding(top = 24.dp),
        )
        return
    }
    Column(Modifier.padding(top = 12.dp)) {
        Text("Recent searches", style = MaterialTheme.typography.titleMedium, color = AppColors.TextMuted)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
            itemsIndexed(recent, key = { i, q -> "$i:$q" }) { _, q -> TvButton(text = q, onClick = { onPick(q) }) }
        }
    }
}

@Composable
private fun AllResults(state: SearchViewModel.State, vm: SearchViewModel, onPlay: () -> Unit, cardModifier: (String) -> Modifier) {
    val serverUrl = rememberServerUrl()
    val epg = remember(state.epgIndex) { EpgLookup(state.epgIndex) }
    val now = remember(state.searched) { System.currentTimeMillis() }
    val use24h = rememberIs24h()
    val memory = rememberFocusMemory()
    // People lead when the query names someone ("shah rukh") or nothing else matched;
    // otherwise they follow the titles.
    val peopleFirst = state.featured != null || (state.movies.groups.isEmpty() && state.series.groups.isEmpty())
    val people: LazyListScope.() -> Unit = {
        if (state.people.isNotEmpty()) item(key = "people") {
            PeopleResultRow(state.people, vm::openPerson, Modifier.rememberedIn(memory)) { cardModifier("p|$it") }
        }
        state.featured?.let { f ->
            item(key = "featured") {
                PosterRow(
                    title = "Movies & series with ${f.name}",
                    items = f.credits,
                    onItemClick = vm::openDiscoverTitle,
                    modifier = Modifier.rememberedIn(memory),
                    itemModifier = { _, item -> cardModifier("f|${item.id}") },
                )
            }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().focusMemory(memory),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (peopleFirst) people()
        if (state.movies.groups.isNotEmpty()) item(key = "movies") {
            GroupRow("Movies", state.movies, { vm.openGroup(it, isSeries = false) }, vm::loadMoreMovies, Modifier.rememberedIn(memory)) { cardModifier("m|$it") }
        }
        if (state.series.groups.isNotEmpty()) item(key = "series") {
            GroupRow("Series", state.series, { vm.openGroup(it, isSeries = true) }, vm::loadMoreSeries, Modifier.rememberedIn(memory)) { cardModifier("s|$it") }
        }
        if (!peopleFirst) people()
        if (state.channels.isNotEmpty()) {
            item(key = "live-title") {
                Text("Live TV · ${state.channels.size}", style = MaterialTheme.typography.titleMedium, color = AppColors.Text, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            }
            // A few rows here; the Live TV filter shows them all.
            itemsIndexed(state.channels.take(ALL_TAB_CHANNELS), key = { i, c -> "ch:$i:${c.id}" }) { _, ch ->
                ChannelRow(
                    channel = ch, epg = epg, nowMs = now, use24h = use24h, serverUrl = serverUrl, isFavorite = false,
                    onPlay = { vm.playChannel(it); onPlay() }, onToggleFavorite = {}, onFocused = {},
                    modifier = Modifier.padding(horizontal = 4.dp).rememberedIn(memory),
                )
            }
        }
    }
}

private const val ALL_TAB_CHANNELS = 5

private fun PersonHit.entry() = PersonEntry(
    PersonCredit(name = name, photo = photo, id = id),
    listOfNotNull(knownFor, titles.takeIf { it > 0 }?.let { "$it ${if (it == 1) "title" else "titles"}" }).joinToString(" · ").ifBlank { null },
)

/** People found by name; each opens their page. */
@Composable
private fun PeopleResultRow(people: List<PersonHit>, onOpen: (Int) -> Unit, modifier: Modifier, tileModifier: (Int) -> Modifier) {
    Column(modifier) {
        Text("People · ${people.size}", style = MaterialTheme.typography.titleMedium, color = AppColors.Text, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
        ) {
            itemsIndexed(people, key = { _, p -> p.id }) { _, p ->
                PersonTile(p.entry(), onClick = { onOpen(p.id) }, modifier = tileModifier(p.id))
            }
        }
    }
}

/** The People filter: everyone found, as a grid. */
@Composable
private fun PeopleGrid(people: List<PersonHit>, onOpen: (Int) -> Unit, tileModifier: (Int) -> Modifier) {
    if (people.isEmpty()) { EmptyPanel("No people found."); return }
    val memory = rememberFocusMemory()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(118.dp),
        modifier = Modifier.fillMaxSize().focusMemory(memory),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(people, key = { _, p -> p.id }) { _, p ->
            PersonTile(p.entry(), onClick = { onOpen(p.id) }, modifier = tileModifier(p.id).rememberedIn(memory))
        }
    }
}

@Composable
private fun GroupRow(
    title: String,
    section: SearchViewModel.Section,
    onOpen: (SearchViewModel.TitleGroup) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier,
    cardModifier: (String) -> Modifier,
) {
    val byId = remember(section.groups) { section.groups.associateBy { it.display.id } }
    val items = remember(section.groups) { section.groups.map { it.display } }
    PosterRow(
        title = "$title · ${section.groups.size}${if (section.hasMore) "+" else ""}",
        items = items,
        modifier = modifier,
        onItemClick = { item -> byId[item.id]?.let(onOpen) },
        subtitleFor = { item -> byId[item.id]?.versionsLabel },
        onLoadMore = if (section.hasMore) onLoadMore else null,
        itemModifier = { _, item -> byId[item.id]?.let { cardModifier(it.key) } ?: Modifier },
    )
}

@Composable
private fun PosterGrid(
    section: SearchViewModel.Section,
    emptyText: String,
    onOpen: (SearchViewModel.TitleGroup) -> Unit,
    onNearEnd: () -> Unit,
    cardModifier: (String) -> Modifier,
) {
    if (section.groups.isEmpty()) { EmptyPanel(emptyText); return }
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, section.groups.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last -> if (last >= section.groups.size - 7) onNearEnd() }
    }
    val memory = rememberFocusMemory()
    val gridFocus = rememberGridFocus(gridState)
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        state = gridState,
        modifier = Modifier.fillMaxSize().keepColumn(gridFocus, section.groups.size).focusMemory(memory),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        itemsIndexed(section.groups, key = { _, g -> g.key }) { i, g ->
            MediaCard(item = g.display, onClick = { onOpen(g) }, subtitle = g.versionsLabel, width = Dp.Unspecified, modifier = cardModifier(g.key).rememberedIn(memory).gridItem(gridFocus, i))
        }
    }
}

@Composable
private fun ChannelList(state: SearchViewModel.State, vm: SearchViewModel, onPlay: () -> Unit) {
    if (state.channels.isEmpty()) { EmptyPanel("No channels found."); return }
    val serverUrl = rememberServerUrl()
    val epg = remember(state.epgIndex) { EpgLookup(state.epgIndex) }
    val now = remember(state.searched) { System.currentTimeMillis() }
    val use24h = rememberIs24h()
    val memory = rememberFocusMemory()
    LazyColumn(
        Modifier.fillMaxSize().focusMemory(memory),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp, start = 4.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.channels, key = { i, c -> "$i:${c.id}" }) { _, ch ->
            ChannelRow(
                channel = ch, epg = epg, nowMs = now, use24h = use24h, serverUrl = serverUrl, isFavorite = false,
                onPlay = { vm.playChannel(it); onPlay() }, onToggleFavorite = {}, onFocused = {},
                modifier = Modifier.rememberedIn(memory),
            )
        }
    }
}

@Composable
private fun rememberIs24h(): Boolean {
    val context = LocalContext.current
    return remember { android.text.format.DateFormat.is24HourFormat(context) }
}

/** Glass dialog listing a title's versions (language · quality), first one focused. */
@Composable
private fun VersionPicker(group: SearchViewModel.TitleGroup, onChoose: (MediaItem) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(group.key) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(620.dp)
                .background(AppColors.Background.copy(alpha = 0.96f), RoundedCornerShape(18.dp))
                .padding(28.dp),
        ) {
            Text(group.title, style = MaterialTheme.typography.headlineSmall, color = AppColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${group.variants.size} versions · choose one", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(6.dp)) {
                itemsIndexed(group.variants, key = { i, v -> "$i:${v.id}" }) { i, v ->
                    val shape = RoundedCornerShape(12.dp)
                    Surface(
                        onClick = { onChoose(v) },
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        shape = ClickableSurfaceDefaults.shape(shape = shape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(BorderStroke(1.dp, AppColors.GlassBorder), shape = shape),
                            focusedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = shape),
                        ),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = AppColors.GlassFill, contentColor = AppColors.Text,
                            focusedContainerColor = AppColors.GlassFillFocused, focusedContentColor = AppColors.Text,
                        ),
                    ) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Text(v.versionLabel?.takeIf { it.isNotBlank() } ?: TitleKey.variantLabel(v.displayTitle), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(v.displayTitle, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
