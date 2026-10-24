package com.portalcast.tv.ui.components

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.ui.theme.AppColors

/** Visual weight of a [TvButton]. The focus treatment is the same for all three. */
enum class TvButtonStyle { Primary, Secondary, Subtle }

/**
 * tv-material `Button` with the app's focus treatment: when focused the container
 * turns sky-400 with dark text, gains a 2 dp white ring and scales up slightly, so
 * the focused control is unmistakable from across the room. Pressing (touch) uses
 * the same colours, so the button is usable on a phone too.
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TvButtonStyle = TvButtonStyle.Secondary,
    /** An icon before the label (drawn in the button's content colour). */
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val (container, content) = when (style) {
        TvButtonStyle.Primary -> PrimaryIdle to Color.White
        TvButtonStyle.Secondary -> AppColors.SurfaceVariant to AppColors.Text
        TvButtonStyle.Subtle -> Color.Transparent to AppColors.TextMuted
    }
    val ring = Border(BorderStroke(2.dp, AppColors.Focus), inset = 2.dp, shape = shape)
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonDefaults.shape(shape = shape),
        scale = ButtonDefaults.scale(focusedScale = 1.05f, pressedScale = 1.05f),
        colors = ButtonDefaults.colors(
            containerColor = container,
            contentColor = content,
            focusedContainerColor = AppColors.Primary,
            focusedContentColor = AppColors.OnPrimary,
            pressedContainerColor = AppColors.Primary,
            pressedContentColor = AppColors.OnPrimary,
            disabledContainerColor = AppColors.Surface,
            disabledContentColor = AppColors.TextMuted,
        ),
        border = ButtonDefaults.border(focusedBorder = ring, pressedBorder = ring),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        // A button never wraps: when the row is tight the label ellipsizes instead of
        // breaking into a column of letters.
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** sky-700: an unfocused primary button still reads as the main action. */
private val PrimaryIdle = Color(0xFF0369A1)
