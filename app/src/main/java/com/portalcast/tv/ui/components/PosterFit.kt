package com.portalcast.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * The widest 2:3 poster whose row fits [available] height: [chrome] is everything in the row
 * that does not grow with the poster (heading, paddings, the title lines under it), [perWidth]
 * the row height each dp of poster width adds. Measured on the screen it runs on, with the
 * viewer's text size, so it holds on any TV (owner, 2026-10-22: no numbers tuned to one set).
 */
fun posterWidthToFit(available: Dp, chrome: Dp, perWidth: Float = 1.5f, min: Dp = 96.dp, max: Dp = 150.dp): Dp =
    ((available - chrome) / perWidth).coerceIn(min, max)

/** One line of [style] in dp, at the viewer's text size. */
@Composable
fun lineHeightDp(style: TextStyle): Dp = with(LocalDensity.current) {
    when {
        style.lineHeight.isSpecified -> style.lineHeight.toDp()
        style.fontSize.isSpecified -> (style.fontSize * 1.4f).toDp()
        else -> 20.dp
    }
}
