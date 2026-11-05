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
 * One result row. What happened to it comes first (a long release name is cut at the end), then
 * where it is from: on 2026-11-04 OpenSubtitles' files failed while Gestdown's loaded, and a
 * failure said only "· didn't load" after the name, out of sight.
 */
internal fun subtitleRowLabel(
    source: String?, releaseName: String?, language: String?, downloadCount: Int, exactMatch: Boolean,
    loading: Boolean, active: Boolean, failed: Boolean, kept: Int = 0,
): String = buildString {
    when {
        loading -> append("Loading…  ")
        active -> append("✓ ")
        failed -> append("Didn't load  ·  ")
    }
    append(subtitleSourceName(source)).append("  ·  ")
    if (kept > 0) append("Kept on by ").append(kept).append(if (kept == 1) " viewer" else " viewers").append("  ·  ")
    append(releaseName ?: "Subtitle")
    language?.takeIf { it.isNotBlank() }?.let { append("  ·  ").append(it.uppercase(Locale.US)) }
    if (downloadCount > 0) append("  ·  ").append(downloadCount).append("↓")
    if (exactMatch) append("  ·  exact match")
}

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
 * The popup the CC button (subtitles) or the speaker button (audio) opens, Jellyfin-style:
 * a card just above the control row at the bottom right. Choosing a track closes it; online
 * results stay so the timing can be adjusted. Back closes it and focus returns to the button.
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
    Column(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 40.dp, bottom = POPUP_BOTTOM)
            .width(440.dp)
            .heightIn(max = 380.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF00F172A))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (panel.audioOnly) "Audio" else "Subtitles", style = MaterialTheme.typography.titleLarge, color = AppColors.Text, fontWeight = FontWeight.SemiBold)
        if (!panel.audioOnly) Text("Now: ${panel.activeLabel}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Above the list, so it shows wherever the list is scrolled to (it was a row above the
        // results, out of view while the focus was down among them).
        if (!panel.audioOnly) panel.message?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = AppColors.Rating, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }

        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (panel.audioOnly) {
                if (panel.audio.isEmpty()) item { Text("No audio tracks reported yet.", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted) }
                // The first focus lands on the track playing now.
                val focusIndex = panel.audio.indexOfFirst { it.selected }.coerceAtLeast(0)
                itemsIndexed(panel.audio, key = { _, t -> "aud:" + t.id }) { i, t ->
                    val mod = if (i == focusIndex) Modifier.fillMaxWidth().focusRequester(first) else Modifier.fillMaxWidth()
                    TvButton(text = (if (t.selected) "✓ " else "") + t.label, onClick = { vm.selectAudioTrack(t.id) },
                        style = TvButtonStyle.Secondary, modifier = mod)
                }
                return@LazyColumn
            }
            item {
                TvButton(text = (if (panel.activeLabel == "Off") "✓ " else "") + "Off", onClick = vm::subtitlesOff,
                    style = TvButtonStyle.Secondary, modifier = Modifier.fillMaxWidth().focusRequester(first))
            }
            items(panel.files, key = { "file:" + it.url }) { f ->
                TvButton(text = (if (panel.activeLabel == "File · ${f.label}") "✓ " else "") + f.label + " (file)", onClick = { vm.pickFileSubtitle(f) },
                    style = TvButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
            }
            items(panel.embedded, key = { "emb:" + it.id }) { t ->
                TvButton(text = (if (t.selected) "✓ " else "") + t.label + " (built-in)", onClick = { vm.selectEmbeddedSubtitle(t.id) },
                    style = TvButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
            }
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.SurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Enabled while busy too (the focus ring stays); the ViewModel ignores the press.
                    TvButton(text = if (panel.bestBusy) "Finding…" else "Best match", onClick = vm::addBestMatch,
                        style = TvButtonStyle.Primary)
                    TvButton(text = if (panel.searching) "Searching…" else "Search online", onClick = vm::searchOnlineSubtitles,
                        style = TvButtonStyle.Primary)
                    TvButton(text = "Language: $langName", onClick = vm::cycleSubtitleLanguage, style = TvButtonStyle.Secondary)
                }
            }
            items(panel.results.orEmpty(), key = { it.downloadKey }) { r ->
                val key = r.downloadKey
                val label = subtitleRowLabel(r.source, r.releaseName, r.language, r.downloadCount, r.exactMatch,
                    loading = panel.loadingFile == key, active = panel.activeKey == key, failed = key in panel.failed, kept = r.kept)
                // Stays enabled while a file loads: a disabled button drops its focus colours and
                // ring, so the highlight vanished from the row just pressed. The ViewModel ignores
                // presses until the load ends; "Loading…" shows on the row.
                TvButton(text = label, onClick = { vm.pickOnlineSubtitle(r) }, style = TvButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Spacer(Modifier.height(2.dp))
                    Text("Timing: ${formatDelay(delayMs)}", style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
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
                }
            }
        }
    }
}

/** The popup sits just above the player's control row. */
private val POPUP_BOTTOM = 108.dp

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
