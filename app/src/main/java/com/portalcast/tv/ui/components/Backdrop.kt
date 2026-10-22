package com.portalcast.tv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.portalcast.tv.ui.theme.AppColors
import com.portalcast.tv.util.HERO_BACKDROP
import com.portalcast.tv.util.ImageUrl

/**
 * Large dimmed (and, on Android 12+, blurred) image behind a detail screen, faded into
 * the background colour towards the left and bottom so text stays readable. Draw it
 * first inside a `Box` and put the content on top. Null [imageUrl] draws only the fade.
 *
 * Drawn into one cached offscreen layer (see HeroBackdrop) and dimmed through the
 * painter's alpha rather than an alpha modifier, which would cost a full-screen
 * saveLayer on every frame the screen scrolls.
 *
 * @param blurred blur the image (no-op below API 31); pass false for TMDB backdrops that are already soft.
 */
@Composable
fun Backdrop(imageUrl: String?, modifier: Modifier = Modifier, blurred: Boolean = true, seed: String = "") {
    Box(modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        if (imageUrl == null) {
            // No art at all: a calm two-tone wash whose hue comes from the title, so a
            // title without a backdrop still has a look of its own instead of a flat page.
            val hue = remember(seed) { ((seed.hashCode() and 0x7fffffff) % 360).toFloat() }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        0f to Color.hsv(hue, 0.55f, 0.40f),
                        0.55f to Color.hsv((hue + 35f) % 360f, 0.50f, 0.22f),
                        1f to AppColors.Background,
                        start = Offset(Float.POSITIVE_INFINITY, 0f),
                        end = Offset(0f, Float.POSITIVE_INFINITY),
                    ),
                ),
            )
        }
        if (imageUrl != null) {
            AsyncImage(
                // Full width at 1080p: w1280 instead of the original file (Phase 4).
                model = ImageUrl.downsize(imageUrl, HERO_BACKDROP),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurred) Modifier.blur(18.dp) else Modifier),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to AppColors.Background,
                        0.55f to AppColors.Background.copy(alpha = 0.75f),
                        1f to AppColors.Background.copy(alpha = 0.2f),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.5f to AppColors.Background.copy(alpha = 0f), 1f to AppColors.Background)),
        )
    }
}
