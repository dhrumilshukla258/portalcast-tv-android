package com.portalcast.tv.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.ui.theme.AppColors

/** What a list screen shows when the server's cache is cold (contract §3). */
const val COLD_CACHE_MESSAGE = "Your server's library hasn't been synced yet. Ask the admin to refresh it."

/** [userMessage], with the browse-screen wording for a cold server cache. */
fun Throwable.panelMessage(): String = if (this is ApiError.ColdCache) COLD_CACHE_MESSAGE else userMessage()

/** Centered "Loading…" with three pulsing dots. Not focusable. */
@Composable
fun LoadingPanel(modifier: Modifier = Modifier, message: String = "Loading…") {
    val transition = rememberInfiniteTransition(label = "loading")
    val pulse by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .alpha((if (i == 1) 1.3f - pulse else pulse).coerceIn(0f, 1f))
                            .background(AppColors.Primary, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
        }
    }
}

/**
 * A centered error message with an optional Retry button.
 *
 * @param requestFocus move focus to Retry when shown. Off by default so an error in a
 *   content pane does not steal focus from the nav rail.
 */
@Composable
fun ErrorPanel(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
) {
    val retryFocus = remember { FocusRequester() }
    if (requestFocus && onRetry != null) {
        LaunchedEffect(message) { runCatching { retryFocus.requestFocus() } }
    }
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 560.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.Text,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(20.dp))
                TvButton(text = "Retry", onClick = onRetry, modifier = Modifier.focusRequester(retryFocus), style = TvButtonStyle.Primary)
            }
        }
    }
}

/** [ErrorPanel] for a repository failure; [ApiError.ColdCache] gets [COLD_CACHE_MESSAGE]. */
@Composable
fun ErrorPanel(
    error: Throwable,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
) = ErrorPanel(message = error.panelMessage(), onRetry = onRetry, modifier = modifier, requestFocus = requestFocus)

/** Muted centered text for an empty list ("No channels in this group"). Not focusable. */
@Composable
fun EmptyPanel(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted, textAlign = TextAlign.Center)
    }
}

/**
 * One row of a left-hand category/group list. Selected rows are tinted sky-400; the
 * focused row gets a light container and a white ring. Click selects (it does not
 * follow focus, so moving through the list never triggers loads).
 */
@Composable
fun SideListItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) AppColors.Primary.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = if (selected) AppColors.Primary else AppColors.TextMuted,
            focusedContainerColor = AppColors.SurfaceVariant,
            focusedContentColor = AppColors.Text,
            pressedContainerColor = AppColors.SurfaceVariant,
            pressedContentColor = AppColors.Text,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            }
        }
    }
}

/** A thin dark rounded panel background, for side columns over a backdrop. */
fun Modifier.panelBackground(): Modifier = this.background(AppColors.Surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))

/** Full-width section heading. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = AppColors.Text, modifier = modifier.fillMaxWidth())
}
