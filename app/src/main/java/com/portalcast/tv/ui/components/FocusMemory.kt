package com.portalcast.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Pin-free stand-in for `Modifier.focusRestorer()`: when focus re-enters a list it goes
 * back to the child that last had it, as long as that child is still composed, otherwise
 * to the default (first) child.
 *
 * Compose 1.7's focusRestorer pins the lazy item focus left from and crashed the app
 * ("Release should only be called once", FocusRestorerNode.onEnter) when that item had
 * been disposed under it: a Discover row whose data refreshed while a detail screen was
 * open, for instance. This keeps a plain reference instead of a pin, so a disposed child
 * simply drops out of the memory.
 */
@Stable
class FocusMemory {
    internal var last: FocusRequester? = null
}

@Composable
fun rememberFocusMemory(): FocusMemory = remember { FocusMemory() }

/** On the list container: focus entering it goes to the remembered child. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.focusMemory(memory: FocusMemory): Modifier =
    focusProperties { enter = { memory.last ?: FocusRequester.Default } }

/** On every child of that list: remembered while it has focus and is composed. */
@Composable
fun Modifier.rememberedIn(memory: FocusMemory): Modifier {
    val requester = remember { FocusRequester() }
    DisposableEffect(memory, requester) {
        onDispose { if (memory.last === requester) memory.last = null }
    }
    return focusRequester(requester).onFocusChanged { if (it.hasFocus) memory.last = requester }
}
