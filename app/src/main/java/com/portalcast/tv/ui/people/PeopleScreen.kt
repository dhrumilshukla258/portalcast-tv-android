package com.portalcast.tv.ui.people

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.PersonHit
import com.portalcast.tv.data.repo.DiscoverRepository
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.ui.components.ErrorPanel
import com.portalcast.tv.ui.components.LoadingPanel
import com.portalcast.tv.ui.components.gridItem
import com.portalcast.tv.ui.components.keepColumn
import com.portalcast.tv.ui.components.rememberGridFocus
import com.portalcast.tv.ui.components.rememberImageUrl
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The People tab's roles, as the server names them (`GET /api/v2/people?role=`). */
enum class PeopleRole(val id: String, val label: String) {
    ALL("all", "All"), ACTOR("actor", "Actors"), ACTRESS("actress", "Actresses"),
    DIRECTOR("director", "Directors"), CREATOR("creator", "Creators"), WRITER("writer", "Writers"),
}

data class PeopleUiState(
    val role: PeopleRole = PeopleRole.ALL,
    val people: List<PersonHit> = emptyList(),
    val total: Int = 0,
    val loadedPages: Int = 0,
    val loading: Boolean = true,
    val error: Throwable? = null,
    /** People per role; a role with none (Actors / Actresses before genders are known) is hidden. */
    val counts: Map<String, Int> = emptyMap(),
) {
    val roles: List<PeopleRole> get() = PeopleRole.entries.filter { it == PeopleRole.ALL || counts.isEmpty() || (counts[it.id] ?: 0) > 0 }
    val hasMore: Boolean get() = people.size < total
}

/** Pages of 60, loaded as focus nears the end of the grid. */
class PeopleViewModel(private val discover: DiscoverRepository) : ViewModel() {
    private val _state = MutableStateFlow(PeopleUiState())
    val state: StateFlow<PeopleUiState> = _state.asStateFlow()
    var lastFocusedIndex = 0
    private var request = 0

    init { load(PeopleRole.ALL, 1) }

    fun selectRole(role: PeopleRole) {
        if (role == _state.value.role) return
        lastFocusedIndex = 0
        _state.update { it.copy(role = role, people = emptyList(), total = 0, loadedPages = 0) }
        load(role, 1)
    }

    fun retry() = load(_state.value.role, _state.value.loadedPages + 1)

    fun loadMoreIfNear(index: Int) {
        val s = _state.value
        if (!s.loading && s.hasMore && index >= s.people.size - 14) load(s.role, s.loadedPages + 1)
    }

    private fun load(role: PeopleRole, page: Int) {
        val id = ++request
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            discover.people(role.id, page).fold(
                { p ->
                    if (id != request) return@fold
                    _state.update {
                        it.copy(
                            people = if (page == 1) p.data else it.people + p.data,
                            total = p.total, loadedPages = page, loading = false,
                            counts = p.counts ?: it.counts,
                        )
                    }
                },
                { e -> if (id == request) _state.update { it.copy(loading = false, error = e) } },
            )
        }
    }
}

private const val COLUMNS = 7

/**
 * The People tab (design-system.md): cast and crew as posters (photo 2:3, name, what they are
 * known for), most popular first, a chip per role. A person opens their page with what can be
 * played. Searching by name is in Search.
 */
@Composable
fun PeopleScreen(
    onOpenPerson: (Int) -> Unit,
    restoreFocus: Boolean = false,
    modifier: Modifier = Modifier,
    /** Home's Movies · Series · People chips (HomeScreen), first in the top row. */
    sectionChips: (@Composable () -> Unit)? = null,
) {
    val container = LocalContext.current.appContainer
    val vm: PeopleViewModel = viewModel(key = "people", factory = viewModelFactory { initializer { PeopleViewModel(container.discoverRepository) } })
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.people.isEmpty()) {
        when {
            state.loading -> LoadingPanel(modifier)
            state.error != null -> ErrorPanel(peopleError(state.error), onRetry = vm::retry, modifier = modifier, requestFocus = true)
            else -> ErrorPanel("No people to show yet.", onRetry = vm::retry, modifier = modifier)
        }
        if (state.people.isEmpty()) return
    }

    Column(modifier.fillMaxSize().padding(start = 48.dp, end = 32.dp, top = 14.dp)) {
        // One row: the sections chips, then the roles (People's filter), then how many.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sectionChips != null) item(key = "sections") { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { sectionChips() } }
            item(key = "divider") { Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(28.dp).background(AppColors.GlassBorder)) }
            items(state.roles, key = { it.id }) { r -> RoleChip(r.label, r == state.role) { vm.selectRole(r) } }
            item(key = "count") { Text("%,d".format(state.total), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted, modifier = Modifier.padding(start = 8.dp)) }
        }
        val firstFocus = remember { FocusRequester() }
        // Back from a person's page: focus the person it was opened from.
        LaunchedEffect(state.role) {
            if (!restoreFocus) return@LaunchedEffect
            repeat(10) { withFrameNanos { }; if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect }
        }
        val gridState = rememberLazyGridState()
        val gridFocus = rememberGridFocus(gridState)
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            state = gridState,
            modifier = Modifier.fillMaxSize().keepColumn(gridFocus, state.people.size),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(state.people, key = { _, p -> p.id }) { i, p ->
                PersonPoster(
                    person = p,
                    onClick = { vm.lastFocusedIndex = i; onOpenPerson(p.id) },
                    modifier = (if (i == vm.lastFocusedIndex.coerceAtMost(state.people.lastIndex)) Modifier.focusRequester(firstFocus) else Modifier)
                        .gridItem(gridFocus, i)
                        .onFocusChanged { if (it.isFocused) vm.loadMoreIfNear(i) },
                )
            }
        }
    }
}

private fun peopleError(e: Throwable?): String = when {
    e is UnsupportedOperationException || (e is ApiError.Http && e.code == 404) ->
        "This server has no People page yet. Update the PortalCast server to get it."
    else -> e?.message ?: "Couldn't load people."
}

@Composable
private fun RoleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) AppColors.Primary else AppColors.SurfaceVariant,
            contentColor = if (selected) AppColors.OnPrimary else AppColors.Text,
            focusedContainerColor = if (selected) AppColors.Primary else AppColors.GlassFillFocused,
            focusedContentColor = if (selected) AppColors.OnPrimary else AppColors.Text,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** A person as a poster: photo 2:3 (w342), name and "Actor · 112" under it; the tile scales on focus. */
@Composable
private fun PersonPoster(person: PersonHit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val url = rememberImageUrl(person.photo?.replace("/w185/", "/w342/"))
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(AppColors.Surface), contentAlignment = Alignment.Center) {
                if (url != null) {
                    AsyncImage(model = url, contentDescription = person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(person.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""), color = AppColors.TextMuted, style = MaterialTheme.typography.headlineSmall)
                }
            }
            Text(
                person.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp),
            )
            Text(
                listOfNotNull(person.knownFor, person.titles.takeIf { it > 0 }?.toString()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
            )
        }
    }
}
