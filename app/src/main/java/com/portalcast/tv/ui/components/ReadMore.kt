package com.portalcast.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * The whole of a text a page shows cut ("…"): a biography, a series' overview, cast and crew.
 * Up / Down scroll it, Back closes (owner, 2026-10-22: text cut off could not be read).
 */
@Composable
fun ReadMoreDialog(title: String, text: String, onDismiss: () -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(10) { withFrameNanos { }; if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 900.dp)
                .fillMaxHeight(0.85f)
                .background(AppColors.Surface, RoundedCornerShape(16.dp))
                .padding(32.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = AppColors.Text)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(260f) }; true }
                            Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-260f) }; true }
                            else -> false
                        }
                    }
                    .verticalScroll(scroll),
            ) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = AppColors.Text)
            }
            Spacer(Modifier.height(12.dp))
            Text("Up / Down to scroll · Back to close", style = MaterialTheme.typography.labelMedium, color = AppColors.TextMuted)
        }
    }
}
