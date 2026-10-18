package com.portalcast.tv.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.ui.theme.AppColors

/** Phase 0 stand-in for every route. Delete once all screens exist. */
@Composable
fun PlaceholderScreen(title: String, subtitle: String? = null) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = AppColors.Text)
            if (subtitle != null) {
                Spacer(Modifier.height(12.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
            }
        }
    }
}
