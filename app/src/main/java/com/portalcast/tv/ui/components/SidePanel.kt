package com.portalcast.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * Back closes this popup on the first press. Compose turns a Back no one used into "move focus
 * out to the parent"; inside a screen that is itself focusable (the player's video, where
 * Left/Right/OK act) that move succeeds and takes the key, so the screen's BackHandler only saw
 * the second press (owner 2026-11-04: subtitles and audio; PopupBackTest). Both halves of the
 * press are taken; it closes when the key comes up.
 */
fun Modifier.closeOnBack(onClose: () -> Unit): Modifier = onPreviewKeyEvent { e ->
    if (e.key != Key.Back && e.key != Key.Escape) return@onPreviewKeyEvent false
    if (e.type == KeyEventType.KeyUp) onClose()
    true
}

private val PanelShape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
private val PanelFill = Brush.verticalGradient(listOf(Color(0xFF111A2D), Color(0xFF0A1120), AppColors.Background))
private const val SLIDE_IN_MS = 240
private const val FADE_IN_MS = 140

/**
 * The full-height panel along the right edge of the screen that filters, subtitles and audio
 * open in (design-system.md: panels run the full height; radius 16). Owner 2026-11-04: they had
 * a hard 1 px border, square corners and simply appeared. Now it slides in over a shade that
 * darkens what is behind it towards its edge, with rounded inner corners, a soft edge light and a
 * header. The animation runs in graphicsLayer (draw phase: nothing recomposes per frame).
 *
 * @param slideIn false when it replaces another panel in place (the Filters menu and a selector's
 *   values): the shade stays and only the content fades in.
 * @param top room left for a bar above it (none by default: the full height of the screen).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.SidePanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    width: Dp = 460.dp,
    top: Dp = 0.dp,
    slideIn: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(if (slideIn) SLIDE_IN_MS else FADE_IN_MS, easing = FastOutSlowInEasing))
    }
    val density = LocalDensity.current
    val slidePx = with(density) { 48.dp.toPx() }
    val panelPx = with(density) { width.toPx() }
    // The remote stays inside the panel (owner 2026-11-05): moves out of it are refused, and if
    // focus is taken out all the same, it closes. Checked a frame later, so a panel leaving the
    // screen (and its focus with it) never closes the one replacing it.
    var focused by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // The shade: clear at the far side, darker up to the panel's edge (no hard line).
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { alpha = if (slideIn) appear.value else 1f }
            .drawWithCache {
                val edge = (size.width - panelPx).coerceAtLeast(1f)
                val shade = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.55f to AppColors.Background.copy(alpha = 0.35f),
                    1f to AppColors.Background.copy(alpha = 0.78f),
                    startX = 0f,
                    endX = edge,
                )
                onDrawBehind { drawRect(shade, size = Size(edge + 1f, size.height)) }
            },
    )
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = top)
            .then(modifier)
            .fillMaxHeight()
            .width(width)
            .graphicsLayer {
                alpha = appear.value
                translationX = if (slideIn) (1f - appear.value) * slidePx else 0f
            }
            .closeOnBack(onDismiss)
            .onFocusChanged { s ->
                focused = s.hasFocus
                if (s.hasFocus) hadFocus = true
                else if (hadFocus) scope.launch { withFrameNanos { }; if (!focused) onDismiss() }
            }
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .clip(PanelShape)
            .drawWithCache {
                // A faint light along the inner edge, fading out at both ends.
                val light = Brush.verticalGradient(listOf(Color.Transparent, AppColors.GlassBorder, Color.Transparent))
                onDrawBehind {
                    drawRect(PanelFill)
                    drawRect(light, topLeft = Offset.Zero, size = Size(1.dp.toPx(), size.height))
                }
            }
            .padding(start = 24.dp, end = 32.dp, top = 24.dp, bottom = 16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = AppColors.Text, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 12.dp).drawWithCache {
            onDrawBehind { drawRect(Color.White.copy(alpha = 0.06f)) }
        })
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** A small heading inside a [SidePanel] ("SOURCES", "FIND ONLINE"). */
@Composable
fun PanelSection(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
        color = AppColors.TextMuted,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp),
    )
}

/**
 * One choice in a [SidePanel]: a check when chosen, the label with an optional muted line under
 * it, a value on the right and a chevron when it opens more. Focused it turns white with dark
 * text, like Home's chips and the Play button (owner 2026-11-05: the accent fill washed out on
 * the TV and the text was hard to read).
 */
@Composable
fun PanelRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    value: String? = null,
    selected: Boolean = false,
    chevron: Boolean = false,
    /** Drawn in the check's place (the genre filter's "left out" sign). */
    leading: (@Composable () -> Unit)? = null,
    /** Highlights the value: a filter that is set. */
    valueSet: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.06f) else Color.Transparent,
            contentColor = AppColors.Text,
            focusedContainerColor = AppColors.Text,
            focusedContentColor = AppColors.Background,
            pressedContainerColor = AppColors.Text,
            pressedContentColor = AppColors.Background,
        ),
    ) {
        Row(
            Modifier.heightIn(min = 54.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                when {
                    leading != null -> leading()
                    selected -> Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.72f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (value != null) {
                val base = LocalContentColor.current
                CompositionLocalProvider(LocalContentColor provides if (valueSet) base else base.copy(alpha = 0.7f)) {
                    Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = if (valueSet) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 180.dp))
                }
            }
            if (chevron) Text("›", style = MaterialTheme.typography.titleMedium, color = LocalContentColor.current.copy(alpha = 0.7f))
        }
    }
}
