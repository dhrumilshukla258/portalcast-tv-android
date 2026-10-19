package com.portalcast.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.playback.SubtitlePrefs
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.util.Locale

/** One selectable built-in text track. */
data class TextTrackOption(val id: String, val label: String, val selected: Boolean)

/** State of the subtitles side panel (null in [PlayerUiState] = closed). */
data class SubtitlePanelState(
    val embedded: List<TextTrackOption> = emptyList(),
    /** What is on now: "Off", "English (built-in)", "Online · <release>". */
    val activeLabel: String = "Off",
    val language: String = "en",
    val searching: Boolean = false,
    /** null = not searched yet. */
    val results: List<com.portalcast.tv.data.model.SubtitleResult>? = null,
    val message: String? = null,
    val loadingFile: Long? = null,
)

/** "+0.50 s" / "−1.25 s" / "In sync". */
fun formatDelay(ms: Long): String = when {
    ms == 0L -> "In sync"
    ms > 0 -> "Later by " + String.format(Locale.US, "%.2f s", ms / 1000.0)
    else -> "Earlier by " + String.format(Locale.US, "%.2f s", -ms / 1000.0)
}

@Composable
fun BoxScope.SubtitlePanel(
    panel: SubtitlePanelState,
    delayMs: Long,
    externalActive: Boolean,
    vm: PlayerViewModel,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    val langName = SubtitlePrefs.LANGUAGES.firstOrNull { it.first == panel.language }?.second ?: panel.language
    Column(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(460.dp)
            .background(Color(0xF00F172A))
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Subtitles", style = MaterialTheme.typography.headlineSmall, color = AppColors.Text, fontWeight = FontWeight.SemiBold)
        Text("Now: ${panel.activeLabel}", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))

        LazyColumn(Modifier.weight(1f, fill = true), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                TvButton(text = "Off", onClick = vm::subtitlesOff, style = TvButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth().focusRequester(first))
            }
            items(panel.embedded, key = { "emb:" + it.id }) { t ->
                TvButton(text = (if (t.selected) "✓ " else "") + t.label + " (built-in)", onClick = { vm.selectEmbeddedSubtitle(t.id) },
                    style = TvButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvButton(text = if (panel.searching) "Searching…" else "Search online", onClick = vm::searchOnlineSubtitles,
                        style = TvButtonStyle.Primary, enabled = !panel.searching)
                    TvButton(text = "Language: $langName", onClick = vm::cycleSubtitleLanguage, style = TvButtonStyle.Secondary)
                }
            }
            panel.message?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted) }
            }
            items(panel.results.orEmpty(), key = { "os:" + it.fileId }) { r ->
                val label = buildString {
                    if (panel.loadingFile == r.fileId) append("Loading… ")
                    append(r.releaseName ?: "Subtitle")
                    append("  ·  ").append((r.language ?: "").uppercase(Locale.US))
                    if (r.downloadCount > 0) append("  ·  ").append(r.downloadCount).append("↓")
                }
                TvButton(text = label, onClick = { vm.pickOnlineSubtitle(r) }, style = TvButtonStyle.Secondary,
                    enabled = panel.loadingFile == null, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(6.dp))
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
