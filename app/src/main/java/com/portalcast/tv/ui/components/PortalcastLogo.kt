package com.portalcast.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.portalcast.tv.R

/** The Portalcast lockup (symbol + wordmark) from the shared brand kit, for dark backgrounds. */
@Composable
fun PortalcastLogo(modifier: Modifier = Modifier, height: Dp = 40.dp) {
    Image(
        painter = painterResource(R.drawable.portalcast_logo),
        contentDescription = "PortalCast",
        modifier = modifier.height(height),
    )
}

/** The PortalCast symbol alone (the portal "P"), for tight spaces like the collapsed rail. */
@Composable
fun PortalcastSymbol(modifier: Modifier = Modifier, height: Dp = 32.dp) {
    Image(
        painter = painterResource(R.drawable.portalcast_symbol),
        contentDescription = "PortalCast",
        modifier = modifier.height(height),
    )
}
