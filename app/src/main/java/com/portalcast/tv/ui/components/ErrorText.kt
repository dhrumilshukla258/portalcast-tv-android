package com.portalcast.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.ui.theme.AppColors

/** Red body text; renders nothing when [message] is null or blank. */
@Composable
fun ErrorText(message: String?, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Center) {
    if (message.isNullOrBlank()) return
    Text(
        text = message,
        modifier = modifier,
        color = AppColors.Error,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = textAlign,
    )
}

/**
 * Generic human-readable text for a repository failure. Screens map the cases they
 * care about (401 on login, 403 on pairing, ...) before falling back to this.
 */
fun Throwable.userMessage(): String = when (this) {
    is ApiError.NoServer -> "No server configured. Enter the server address first."
    is ApiError.Network -> "Can't reach the server. Check the address, the network, and that the server is running."
    is ApiError.RateLimited -> "Too many attempts. Wait 5 minutes and try again."
    is ApiError.ColdCache -> "The server library hasn't synced yet. Ask the admin to refresh it."
    is ApiError.Http -> message?.takeIf { it.isNotBlank() } ?: "Server error ($code)"
    else -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
}
