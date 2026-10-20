package com.portalcast.tv.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.portalcast.tv.util.ImageUrl

/**
 * The TV channel a title airs on ("Zee5 : Manemagalu" is shown as "Manemagalu" with this
 * beside it): its logo on a dark pill, or the channel's name when there is no logo or it
 * fails to load.
 */
@Composable
internal fun ChannelBadge(channel: String, logoPath: String?, serverUrl: String?, modifier: Modifier = Modifier, height: Dp = 22.dp) {
    val context = LocalContext.current
    val url = remember(serverUrl, logoPath) { ImageUrl.resolve(serverUrl, logoPath) }
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        if (url != null && !failed) {
            val request = remember(url) { ImageRequest.Builder(context).data(url).size(240, 96).build() }
            AsyncImage(
                model = request,
                contentDescription = channel,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.height(height).widthIn(max = height * 3),
            )
        } else {
            Text(channel, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
        }
    }
}
