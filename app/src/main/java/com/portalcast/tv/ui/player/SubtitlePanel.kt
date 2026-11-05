package com.portalcast.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.data.model.SubtitleResult
import com.portalcast.tv.data.model.downloadKey
import com.portalcast.tv.playback.SubtitlePrefs
import com.portalcast.tv.ui.components.PanelRow
import com.portalcast.tv.ui.components.PanelSection
import com.portalcast.tv.ui.components.SidePanel
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.util.Locale

/** One selectable built-in text track. */
data class TextTrackOption(val id: String, val label: String, val selected: Boolean)

/**
 * State of the subtitles / audio popup (null in [PlayerUiState] = closed). Immutable (every change
 * is a copy), so the popup skips the player's once-a-second position ticks.
 */
@Immutable
data class SubtitlePanelState(
    /** Opened from the audio button: only the audio tracks are listed. */
    val audioOnly: Boolean = false,
    val embedded: List<TextTrackOption> = emptyList(),
    /** Subtitle files beside a library video (Jellyfin's, a folder's .srt): listed first. */
    val files: List<com.portalcast.tv.data.model.FileSubtitle> = emptyList(),
    val bestBusy: Boolean = false,
    /** Audio tracks of the file; shown only when there is more than one. */
    val audio: List<TextTrackOption> = emptyList(),
    /** What is on now: "Off", "English (built-in)", "Online · <release>". */
    val activeLabel: String = "Off",
    val language: String = "en",
    /** What the online search looks for: prefilled ("Title (2010)", "Show S01E02"), editable. */
    val query: String = "",
    val searching: Boolean = false,
    /** null = not searched yet. */
    val results: List<com.portalcast.tv.data.model.SubtitleResult>? = null,
    val message: String? = null,
    /** downloadKey of the result being loaded. */
    val loadingFile: String? = null,
    /** downloadKeys that did not load while playing this: marked in the list, skipped by Best match. */
    val failed: Set<String> = emptySet(),
    /** downloadKey of the online subtitle showing now (its row gets the ✓). */
    val activeKey: String? = null,
)

/**
 * The ViewModel as a parameter Compose can compare (one per player screen): without it the popup
 * and every row in it were redrawn on each position tick, while the remote moved through them.
 */
@Stable
class SubtitlePanelHost(val vm: PlayerViewModel)

/** "OpenSubtitles", "SubDL", "Gestdown", "Jellyfin" for a result's source. */
internal fun subtitleSourceName(source: String?): String = when (source) {
    "subdl" -> "SubDL"; "gestdown" -> "Gestdown"; "jellyfin" -> "Jellyfin"; else -> "OpenSubtitles"
}

private fun SubtitleResult.isOpenSubtitles() = downloadKey.startsWith("os:")

/**
 * The order Best match tries results in: the list's, without files that already failed; once an
 * OpenSubtitles file failed, its other files go after the other sources' (its downloads tend to
 * fail together: all day on 2026-11-04).
 */
internal fun bestMatchOrder(results: List<SubtitleResult>, failed: Set<String>): List<SubtitleResult> {
    val left = results.filterNot { it.downloadKey in failed }
    if (failed.none { it.startsWith("os:") }) return left
    return left.filterNot { it.isOpenSubtitles() } + left.filter { it.isOpenSubtitles() }
}

/** The other sources in the list (not OpenSubtitles, not failed), named for the failure message. */
internal fun otherSubtitleSources(results: List<SubtitleResult>?, failed: Set<String>): List<String> =
    results.orEmpty().filter { !it.isOpenSubtitles() && it.downloadKey !in failed }.map { subtitleSourceName(it.source) }.distinct()

/**
 * The second line of a result row: what happened to it first, then where it is from (owner
 * 2026-11-04: OpenSubtitles' files failed while Gestdown's loaded). The release name is the
 * row's first line; a check marks the one on.
 */
internal fun subtitleRowDetail(
    source: String?, language: String?, downloadCount: Int, exactMatch: Boolean,
    loading: Boolean, failed: Boolean, kept: Int = 0,
): String = buildList {
    when {
        loading -> add("Loading…")
        failed -> add("Didn't load")
    }
    add(subtitleSourceName(source))
    if (kept > 0) add("Kept on by $kept " + if (kept == 1) "viewer" else "viewers")
    language?.takeIf { it.isNotBlank() }?.let { add(it.uppercase(Locale.US)) }
    if (exactMatch) add("exact match")
    if (downloadCount > 0) add("$downloadCount↓")
}.joinToString("  ·  ")

/**
 * What a failed pick says (owner 2026-11-04: a subtitle was on, and "Couldn't load that subtitle"
 * stayed under it after other picks failed). Names OpenSubtitles when it was its file, and says
 * whether the subtitles already on stay on.
 */
internal fun subtitleFailureMessage(downloadKey: String, showing: Boolean, otherSources: List<String> = emptyList()): String {
    val os = downloadKey.startsWith("os:")
    val what = if (os) "OpenSubtitles didn't send that file." else "That subtitle didn't load."
    val next = when {
        showing -> " Your current subtitles stay on."
        os && otherSources.isNotEmpty() -> " Try one from ${otherSources.joinToString(" or ")}."
        else -> " Try another one."
    }
    return what + next
}

/** "+0.50 s" / "−1.25 s" / "In sync". */
fun formatDelay(ms: Long): String = when {
    ms == 0L -> "In sync"
    ms > 0 -> "Later by " + String.format(Locale.US, "%.2f s", ms / 1000.0)
    else -> "Earlier by " + String.format(Locale.US, "%.2f s", -ms / 1000.0)
}

/**
 * The panel the CC button (subtitles) or the speaker button (audio) opens: the same full-height
 * side panel as Filters (SidePanel; owner 2026-11-04: the small card looked out of place and Back
 * closed it only on the second press). Choosing a track closes it; online results stay so the
 * timing can be adjusted. Back closes it and focus returns to the button.
 */
@Composable
fun BoxScope.SubtitlePanel(
    panel: SubtitlePanelState,
    delayMs: Long,
    externalActive: Boolean,
    host: SubtitlePanelHost,
) {
    val vm = host.vm
    val first = remember { FocusRequester() }
    LaunchedEffect(panel.audioOnly) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    val langName = SubtitlePrefs.LANGUAGES.firstOrNull { it.first == panel.language }?.second ?: panel.language
    SidePanel(
        title = if (panel.audioOnly) "Audio" else "Subtitles",
        subtitle = if (panel.audioOnly) null else "Now: ${panel.activeLabel}",
        onDismiss = vm::closeSubtitlePanel,
        width = 540.dp,
    ) {
        // Above the list, so it shows wherever the list is scrolled to.
        if (!panel.audioOnly) panel.message?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = AppColors.Rating, maxLines = 3,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (panel.audioOnly) {
                if (panel.audio.isEmpty()) item {
                    Text("No audio tracks reported yet.", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                }
                // The first focus lands on the track playing now.
                val focusIndex = panel.audio.indexOfFirst { it.selected }.coerceAtLeast(0)
                itemsIndexed(panel.audio, key = { _, t -> "aud:" + t.id }) { i, t ->
                    PanelRow(label = t.label, selected = t.selected, onClick = { vm.selectAudioTrack(t.id) },
                        modifier = if (i == focusIndex) Modifier.focusRequester(first) else Modifier)
                }
                return@LazyColumn
            }
            item {
                PanelRow(label = "Off", selected = panel.activeLabel == "Off", onClick = vm::subtitlesOff,
                    modifier = Modifier.focusRequester(first))
            }
            items(panel.files, key = { "file:" + it.url }) { f ->
                PanelRow(label = f.label, detail = "File beside the video", selected = panel.activeLabel == "File · ${f.label}",
                    onClick = { vm.pickFileSubtitle(f) })
            }
            items(panel.embedded, key = { "emb:" + it.id }) { t ->
                PanelRow(label = t.label, detail = "Built in", selected = t.selected, onClick = { vm.selectEmbeddedSubtitle(t.id) })
            }

            item { PanelSection("Find online") }
            item {
                // The search, editable: OK opens the keyboard; its Search key searches. "S01E02"
                // and "(2010)" in the text set the episode and year.
                androidx.compose.foundation.text.BasicTextField(
                    value = panel.query,
                    onValueChange = { vm.setSubtitleQuery(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = AppColors.Text),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.Focus),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.searchOnlineSubtitles() }),
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.SurfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            item {
                // Enabled while busy too (the focus ring stays); the ViewModel ignores the press.
                Row(Modifier.padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvButton(text = if (panel.bestBusy) "Finding…" else "Best match", onClick = vm::addBestMatch,
                        style = TvButtonStyle.Primary)
                    TvButton(text = if (panel.searching) "Searching…" else "Search online", onClick = vm::searchOnlineSubtitles,
                        style = TvButtonStyle.Secondary)
                }
            }
            item { PanelRow(label = "Language", value = langName, chevron = true, onClick = vm::cycleSubtitleLanguage) }
            items(panel.results.orEmpty(), key = { it.downloadKey }) { r ->
                val key = r.downloadKey
                // Stays enabled while a file loads: a disabled button drops its focus colours, so
                // the highlight vanished from the row just pressed. The ViewModel ignores presses
                // until the load ends; "Loading…" shows on the row.
                PanelRow(
                    label = r.releaseName ?: "Subtitle",
                    detail = subtitleRowDetail(r.source, r.language, r.downloadCount, r.exactMatch,
                        loading = panel.loadingFile == key, failed = key in panel.failed, kept = r.kept),
                    selected = panel.activeKey == key,
                    onClick = { vm.pickOnlineSubtitle(r) },
                )
            }

            item { PanelSection("Timing") }
            item {
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatDelay(delayMs), style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
                    if (externalActive) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvButton(text = "− 0.25 s", onClick = { vm.adjustSubtitleDelay(-250) }, style = TvButtonStyle.Secondary)
                            TvButton(text = "+ 0.25 s", onClick = { vm.adjustSubtitleDelay(250) }, style = TvButtonStyle.Secondary)
                            TvButton(text = "Reset", onClick = vm::resetSubtitleDelay, style = TvButtonStyle.Secondary)
                        }
                        Text("− shows them earlier, + later. Saved for this title.", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
                    } else {
                        Text("Timing works with online subtitles.", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** Downloaded subtitles, drawn by the app (so their timing can be shifted both ways). */
@Composable
fun BoxScope.SubtitleLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 96.dp, end = 96.dp, bottom = 64.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
