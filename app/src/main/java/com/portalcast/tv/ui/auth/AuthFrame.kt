package com.portalcast.tv.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.ui.theme.AppColors

/**
 * Centred column shared by the signed-out screens: brand line, title, optional
 * subtitle, then [content]. Scrolls when the IME or a phone screen squeezes it.
 */
@Composable
fun AuthFrame(
    title: String,
    subtitle: String? = null,
    maxWidth: Dp = 560.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("PORTALCAST", style = MaterialTheme.typography.labelLarge, color = AppColors.Primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, color = AppColors.Text, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            content()
        }
    }
}
