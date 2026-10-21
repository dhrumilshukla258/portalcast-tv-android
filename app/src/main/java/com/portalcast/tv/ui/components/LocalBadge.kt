package com.portalcast.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * "Local" (or "Local · 4K") on a card: a title you own on the NAS, which plays from the
 * home network without the IPTV portal (server docs/skill-local-library.md).
 */
@Composable
fun LocalBadge(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        modifier = modifier
            .background(Color(0xE60EA5E9), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}
