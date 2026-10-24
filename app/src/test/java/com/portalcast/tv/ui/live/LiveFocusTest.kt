package com.portalcast.tv.ui.live

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.portalcast.tv.ui.components.focusMemory
import com.portalcast.tv.ui.components.rememberFocusMemory
import com.portalcast.tv.ui.components.rememberedIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Live TV under the tabs, with the same focus rules as HomeScreen (tab row enter/exit and its
 * Down retry, the content's exit Up) and LiveScreen (both lists remember their last item):
 * after going up from the content to the Live TV tab, Down must go back into the content
 * (owner, 2026-10-23: "it doesn't go down then").
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class LiveFocusTest {
    @get:Rule val rule = createComposeRule()

    private val tabs = listOf("home", "live", "sports")

    @Composable
    private fun Harness() {
        val scope = rememberCoroutineScope()
        val tabRequesters = remember { tabs.associateWith { FocusRequester() } }
        val contentRequester = remember { FocusRequester() }
        var tabsFocused by remember { mutableStateOf(false) }
        val tabEdge = remember { IntArray(1) }
        var contentFocused by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .onFocusChanged { tabsFocused = it.hasFocus }
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown) when (e.key) {
                            Key.DirectionUp -> return@onPreviewKeyEvent true
                            Key.DirectionLeft -> if (tabEdge[0] < 0) return@onPreviewKeyEvent true
                            Key.DirectionRight -> if (tabEdge[0] > 0) return@onPreviewKeyEvent true
                            else -> Unit
                        }
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                            scope.launch {
                                withFrameNanos { }
                                withFrameNanos { }
                                repeat(30) {
                                    if (contentFocused || !tabsFocused) return@launch
                                    runCatching { contentRequester.requestFocus() }
                                    withFrameNanos { }
                                    if (contentFocused) return@launch
                                    delay(100)
                                }
                            }
                        }
                        false
                    }
                    .focusProperties { enter = { tabRequesters.getValue("live") } }
                    .focusGroup(),
            ) {
                for (t in tabs) Box(
                    Modifier.width(120.dp).height(48.dp).focusRequester(tabRequesters.getValue(t))
                        .onFocusChanged { if (it.isFocused) tabEdge[0] = if (t == tabs.first()) -1 else if (t == tabs.last()) 1 else 0 }
                        .focusable().testTag("tab-$t"),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onFocusChanged { contentFocused = it.hasFocus }
                    .focusRequester(contentRequester)
                    .focusProperties { exit = { dir -> if (dir == FocusDirection.Up) tabRequesters.getValue("live") else FocusRequester.Cancel } }
                    .focusGroup(),
            ) { LiveLike() }
        }
    }

    @Composable
    private fun LiveLike() {
        val groupsMemory = rememberFocusMemory()
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.width(210.dp).fillMaxHeight().focusMemory(groupsMemory)) {
                items((0 until 20).toList(), key = { it }) { g ->
                    Box(Modifier.rememberedIn(groupsMemory).fillMaxWidth().height(40.dp).focusable().testTag("g$g"))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.fillMaxWidth().height(40.dp)) // the group title row, not focusable
                val memory = rememberFocusMemory()
                LazyColumn(Modifier.fillMaxSize().focusMemory(memory)) {
                    items((0 until 80).toList(), key = { it }) { c ->
                        Box(Modifier.rememberedIn(memory).fillMaxWidth().height(70.dp).focusable().testTag("c$c"))
                    }
                }
            }
        }
    }

    private fun press(key: Key, times: Int = 1) = repeat(times) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
    }

    private fun focusedTag(): String? = runCatching {
        rule.onNode(isFocused()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.TestTag)
    }.getOrNull()

    private fun startOnLiveTab() {
        rule.setContent { Harness() }
        rule.onNodeWithTag("tab-live").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.waitForIdle()
        assertTrue("start on the Live TV tab, not ${focusedTag()}", focusedTag() == "tab-live")
    }

    @Test fun downFromTheTabGoesIntoLiveTv() {
        startOnLiveTab()
        press(Key.DirectionDown)
        val f = focusedTag()
        assertTrue("Down from Live TV went to $f", f != null && !f.startsWith("tab-"))
    }

    /** Category -> channels -> up past the first channel to the tab -> Down. */
    @Test fun downAgainAfterLeavingUpFromTheChannels() {
        startOnLiveTab()
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionDown, 5)
        press(Key.DirectionUp, 8)
        assertTrue("back on the Live TV tab, not ${focusedTag()}", focusedTag() == "tab-live")
        press(Key.DirectionDown)
        val f = focusedTag()
        assertTrue("Down after coming back up went to $f", f != null && !f.startsWith("tab-"))
    }

    /** Channels scrolled down -> left to the categories -> up through them to the tab -> Down. */
    @Test fun downAgainAfterLeavingUpThroughTheCategories() {
        startOnLiveTab()
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionDown, 12)
        press(Key.DirectionLeft)
        press(Key.DirectionUp, 25)
        assertTrue("back on the Live TV tab, not ${focusedTag()}", focusedTag() == "tab-live")
        press(Key.DirectionDown)
        val f = focusedTag()
        assertTrue("Down after coming back up went to $f", f != null && !f.startsWith("tab-"))
    }

    /** The owner's simplest repro: Down into Live TV, Right to a channel, Up to the tab, Down. */
    @Test fun downAgainAfterAChannelAndBackUp() {
        startOnLiveTab()
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionUp, 3)
        assertTrue("back on the Live TV tab, not ${focusedTag()}", focusedTag() == "tab-live")
        press(Key.DirectionDown)
        val f = focusedTag()
        assertTrue("Down after coming back up went to $f", f != null && !f.startsWith("tab-"))
    }

    /** Focus leaves the tabs only Down: Up, Left on the first and Right on the last stay put. */
    @Test fun theTabsKeepFocusAtTheirEdges() {
        startOnLiveTab()
        press(Key.DirectionUp)
        assertTrue("Up from the tabs went to ${focusedTag()}", focusedTag() == "tab-live")
        press(Key.DirectionLeft, 3)
        assertTrue("Left past the first tab went to ${focusedTag()}", focusedTag() == "tab-home")
        press(Key.DirectionRight, 5)
        assertTrue("Right past the last tab went to ${focusedTag()}", focusedTag() == "tab-sports")
    }
}
