package com.portalcast.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.portalcast.tv.ui.theme.AppColors

/**
 * Large dimmed (and, on Android 12+, blurred) image behind a detail screen, faded into
 * the background colour towards the left and bottom so text stays readable. Draw it
 * first inside a `Box` and put the content on top. Null [imageUrl] draws only the fade.
 *
 * @param blurred blur the image (no-op below API 31); pass false for TMDB backdrops that are already soft.
 */
@Composable
fun Backdrop(imageUrl: String?, modifier: Modifier = Modifier, blurred: Boolean = true) {
    Box(modifier.fillMaxSize()) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurred) Modifier.blur(18.dp) else Modifier)
                    .alpha(0.45f),
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
