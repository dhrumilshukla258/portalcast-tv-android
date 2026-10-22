package com.portalcast.tv.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** What the title menu was opened for: the card's item, and Remove on Continue Watching cards. */
data class TitleMenuTarget(
    val item: MediaItem,
    val onRemoveFromContinue: (() -> Unit)? = null,
    /** From My List > Watched: the menu offers Not watched (undo). */
    val watched: Boolean = false,
)

/**
 * Opens the title menu (hold Center on a card; owner, 2026-10-22). Provided by HomeScreen;
 * null elsewhere (cards then keep their own long-press, if any).
 */
val LocalTitleMenu = staticCompositionLocalOf<((TitleMenuTarget) -> Unit)?> { null }

/**
 * The viewer's Not interested titles, left out of every Home / Movies / Series row (the
 * server only takes them off its personal rows; What's new and genre rows are shared).
 * Loaded at sign-in (AppNavHost), changed by the title menu. IPTV categories, search and My
 * List are left as they are.
 */
object HiddenTitles {
    private val _ids = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val ids: kotlinx.coroutines.flow.StateFlow<Set<String>> = _ids
    fun set(ids: Set<String>) { _ids.value = ids }
    fun add(id: String) { _ids.value = _ids.value + id }
    fun remove(id: String) { _ids.value = _ids.value - id }
}

/** Something changed (watched, not interested, My List): screens with suggestions reload. */
object TitleChanges {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val events: SharedFlow<Unit> = _events
    fun changed() { _events.tryEmit(Unit) }
}

/** The card's catalog id ("movie_123"): Discover ids are one, else `catalogId`, else built. */
fun titleContentId(item: MediaItem): String? {
    val id = item.id
    if (id.startsWith("movie_") || id.startsWith("series_")) return id
    item.catalogId?.takeIf { it.startsWith("movie_") || it.startsWith("series_") }?.let { return it }
    if (id.isBlank()) return null
    return (if (item.isSeries == true || item.discoverType == "series") "series_" else "movie_") + item.rawId
}

/**
 * The title menu: My List, Mark as watched, Not interested (kept off your Home rows, so new
 * things show instead of the same ones), and on Continue Watching, Remove. It opens while
 * Center is still held (a long press): keys are swallowed until that key is released, or the
 * release itself would press the first button.
 */
@Composable
fun TitleMenuDialog(target: TitleMenuTarget, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    val discover = container.discoverRepository
    val scope = rememberCoroutineScope()
    val id = remember(target) { titleContentId(target.item) }
    var inList by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var armed by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    LaunchedEffect(id) {
        if (id != null) discover.myListIds().onSuccess { inList = id in it }
    }
    LaunchedEffect(Unit) { repeat(10) { withFrameNanos { }; if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect } }

    fun run(done: String, action: suspend () -> Result<Unit>) {
        if (busy) return
        busy = true
        scope.launch {
            val r = action()
            if (r.isSuccess) {
                TitleChanges.changed()
                message = done
                delay(800)
                onDismiss()
            } else {
                message = "Couldn't do that. Try again."
                busy = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .onPreviewKeyEvent { ev ->
                    if (armed) return@onPreviewKeyEvent false
                    if (ev.type == KeyEventType.KeyUp) armed = true
                    true
                }
                .width(520.dp)
                .background(AppColors.Background, RoundedCornerShape(16.dp))
                .border(1.dp, AppColors.GlassBorder, RoundedCornerShape(16.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(target.item.displayTitle, style = MaterialTheme.typography.titleLarge, color = AppColors.Text, maxLines = 2)
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.Primary) }
            if (id == null) {
                Text("Nothing to do for this one.", color = AppColors.TextMuted)
                TvButton("Close", onClick = onDismiss, modifier = Modifier.focusRequester(first))
                return@Column
            }
            val list = inList
            val buttons = buildList<Pair<String, () -> Unit>> {
                if (list != null) add((if (list) "Remove from My List" else "Add to My List") to {
                    run(if (list) "Removed from My List" else "Added to My List") { discover.setInList(id, !list) }
                })
                if (target.watched) add("Not watched" to { run("Marked as not watched") { discover.setWatched(id, false) } })
                else add("Mark as watched" to { run("Marked as watched") { discover.setWatched(id, true) } })
                if (id in HiddenTitles.ids.value) add("Show on Home again" to { run("It can show on Home again") { discover.setNotInterested(id, false).onSuccess { HiddenTitles.remove(id) } } })
                else add("Not interested" to { run("Got it: you won't see it on Home") { discover.setNotInterested(id, true).onSuccess { HiddenTitles.add(id) } } })
                target.onRemoveFromContinue?.let { remove ->
                    add("Remove from Continue Watching" to { remove(); TitleChanges.changed(); onDismiss() })
                }
                add("Cancel" to onDismiss)
            }
            buttons.forEachIndexed { i, (label, action) ->
                TvButton(
                    text = label,
                    onClick = action,
                    enabled = !busy,
                    style = if (i == 0) TvButtonStyle.Primary else TvButtonStyle.Secondary,
                    modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).fillMaxWidth(),
                )
            }
        }
    }
}
