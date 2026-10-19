package com.portalcast.tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Dark palette matching the webui (slate-900 background `#0F172A`, which is also
 * the colour the webui's pairing QR uses). Always dark: this is a TV app.
 */
object AppColors {
    val Background = Color(0xFF0F172A)
    val Surface = Color(0xFF1E293B)
    val SurfaceVariant = Color(0xFF334155)
    val Primary = Color(0xFF38BDF8)     // sky-400
    val OnPrimary = Color(0xFF082F49)
    val Secondary = Color(0xFFA78BFA)   // violet-400
    val Text = Color(0xFFF1F5F9)
    val TextMuted = Color(0xFF94A3B8)
    val Error = Color(0xFFF87171)
    val Focus = Color(0xFFFFFFFF)

    // Glass, matching the webui's panels (bg-white/10, border-white/10, rounded-xl/2xl).
    // No live blur: RenderEffect needs API 31 and blur is costly on TV GPUs; translucent fill
    // over the dimmed backdrop plus a thin light border reads the same at no cost.
    val GlassFill = Color(0x14FFFFFF)          // white 8 %
    val GlassFillFocused = Color(0x2EFFFFFF)   // white 18 %
    val GlassBorder = Color(0x1AFFFFFF)        // white 10 %
    val GlassScrim = Color(0xB30F172A)         // background 70 %, behind dialogs/panels
}

private val DarkScheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.OnPrimary,
    secondary = AppColors.Secondary,
    background = AppColors.Background,
    onBackground = AppColors.Text,
    surface = AppColors.Surface,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.SurfaceVariant,
    onSurfaceVariant = AppColors.TextMuted,
    error = AppColors.Error,
    border = AppColors.Focus,
)

@Composable
fun PortalcastTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme) {
        Box(Modifier.fillMaxSize().background(AppColors.Background)) {
            content()
        }
    }
}
