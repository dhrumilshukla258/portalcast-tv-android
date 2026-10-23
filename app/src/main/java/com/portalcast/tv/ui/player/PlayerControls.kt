package com.portalcast.tv.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.portalcast.tv.playback.PlaybackSession
import com.portalcast.tv.ui.theme.AppColors

/** Material icon paths (the core icon set has none of these). */
private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.White))
    .build()

private object PlayerIcons {
    val Play = icon("play", "M8,5v14l11,-7z")
    val Pause = icon("pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")
    val Replay = icon("replay", "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z")
    val SkipNext = icon("next", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    val ClosedCaption = icon(
        "cc",
        "M19,4H5c-1.11,0 -2,0.9 -2,2v12c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM11,11H9.5v-0.5h-2v3h2V13H11v1c0,0.55 -0.45,1 -1,1H7c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1v1zM18,11h-1.5v-0.5h-2v3h2V13H18v1c0,0.55 -0.45,1 -1,1h-3c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1v1z",
    )
    val Audio = icon("audio", "M12,3v9.28c-0.47,-0.17 -0.97,-0.28 -1.5,-0.28C8.01,12 6,14.01 6,16.5S8.01,21 10.5,21c2.31,0 4.2,-1.75 4.45,-4H15V6h4V3h-7z")
}

/** Focus targets of the control row, held by the screen (it moves focus in and out). */
class PlayerControlFocus {
    val playPause = FocusRequester()
    val subtitles = FocusRequester()
    val audio = FocusRequester()
}

/**
 * Jellyfin-style control row under the progress bar: back 10 s, play/pause, forward 10 s and
 * next episode on the left; subtitles (CC) and audio on the right, each opening a popup above
 * it. Left/Right move between buttons, OK presses one, Up (or Back) returns to the video.
 *
 * @param onActivity any key while a button has focus (keeps the row up).
 * @param onLeave Up from the row: focus goes back to the video.
 */
@Composable
fun PlayerControlRow(
    state: PlayerUiState,
    vm: PlayerViewModel,
    focus: PlayerControlFocus,
    onFocusChanged: (Boolean) -> Unit,
    onActivity: () -> Unit,
    onLeave: () -> Unit,
) {
    val playing = state.status == PlayerStatus.Playing || state.status == PlayerStatus.Buffering
    val step = PlayerViewModel.SEEK_STEP_MS
    val hasNext = (state.session as? PlaybackSession.Episode)?.nextEpisode != null
    // Play, seek and next episode in the centre; subtitles and audio at the far right (owner,
    // 2026-10-22).
    Box(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                onActivity()
                if (e.key == Key.DirectionUp) { onLeave(); true } else false
            },
    ) {
      Row(Modifier.align(Alignment.TopCenter), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ControlButton("Back ${step / 1000} s", onClick = { vm.seekBy(-step) }) { SeekIcon(forward = false, seconds = step / 1000) }
        ControlButton(if (playing) "Pause" else "Play", onClick = vm::togglePlayPause, modifier = Modifier.focusRequester(focus.playPause), large = true) {
            Icon(if (playing) PlayerIcons.Pause else PlayerIcons.Play, contentDescription = null, modifier = Modifier.size(30.dp))
        }
        ControlButton("Forward ${step / 1000} s", onClick = { vm.seekBy(step) }) { SeekIcon(forward = true, seconds = step / 1000) }
        if (hasNext) {
            ControlButton("Next episode", onClick = vm::playNextEpisode) { Icon(PlayerIcons.SkipNext, contentDescription = null, modifier = Modifier.size(26.dp)) }
        }
      }
      Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ControlButton(
            if (state.externalSubtitles) "Subtitles · online" else "Subtitles",
            onClick = { vm.openSubtitlePanel() },
            modifier = Modifier.focusRequester(focus.subtitles),
            active = state.externalSubtitles,
        ) { Icon(PlayerIcons.ClosedCaption, contentDescription = null, modifier = Modifier.size(26.dp)) }
        ControlButton("Audio", onClick = { vm.openSubtitlePanel(audioOnly = true) }, modifier = Modifier.focusRequester(focus.audio)) {
            Icon(PlayerIcons.Audio, contentDescription = null, modifier = Modifier.size(24.dp))
        }
      }
    }
}

/** A round icon button with its name under it while focused (the slot keeps its height). */
@Composable
private fun ControlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val size = if (large) 56.dp else 46.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(size).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = CircleShape)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = AppColors.Text,
                focusedContainerColor = AppColors.Primary,
                focusedContentColor = AppColors.OnPrimary,
            ),
        ) {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) { content() }
        }
        // Subtitles on: a bar under the CC button, as in Jellyfin.
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(width = 18.dp, height = 3.dp)
                .background(if (active) AppColors.Primary else Color.Transparent, RoundedCornerShape(2.dp)),
        )
        Text(
            if (focused) label else "",
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.Text,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp).height(18.dp),
        )
    }
}

/** The circular arrow with the step in it; mirrored for forward. */
@Composable
private fun SeekIcon(forward: Boolean, seconds: Long) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            PlayerIcons.Replay,
            contentDescription = null,
            modifier = Modifier.size(28.dp).graphicsLayer { scaleX = if (forward) -1f else 1f },
        )
        Text(seconds.toString(), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = 2.dp))
    }
}
