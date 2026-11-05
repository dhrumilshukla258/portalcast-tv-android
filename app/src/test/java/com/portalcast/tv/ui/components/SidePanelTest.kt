package com.portalcast.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The side panel lays out as designed on a 960×540 dp TV screen (no screenshots needed). */
private val LONG = "A very long value ".repeat(30)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SidePanelTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun aFullHeightPanelOnTheRightWithRowsThatFit() {
        rule.setContent {
            Box(Modifier.fillMaxSize().testTag("screen")) {
                SidePanel(title = "Filters", subtitle = "Picks apply at once", onDismiss = {}, top = 60.dp, modifier = Modifier.testTag("panel")) {
                    PanelRow(label = "Genre", value = "Any", chevron = true, onClick = {})
                    PanelRow(label = "Language", value = LONG, valueSet = true, chevron = true, onClick = {})
                    PanelSection("Sources")
                    PanelRow(label = "My library", detail = "Files on your NAS", chevron = true, onClick = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(400) // past the slide-in
        val screen = rule.onNodeWithTag("screen").getBoundsInRoot()
        val panel = rule.onNodeWithTag("panel").getBoundsInRoot()
        assertEquals("flush with the right edge", screen.right, panel.right)
        assertEquals("460 wide", 460f, (panel.right - panel.left).value, 0.5f)
        assertEquals("under the bar, down to the bottom", 60f, panel.top.value, 0.5f)
        assertEquals(screen.bottom, panel.bottom)

        val label = rule.onNodeWithText("Language", useUnmergedTree = true).getBoundsInRoot()
        val value = rule.onNodeWithText(LONG, useUnmergedTree = true).getBoundsInRoot()
        // Robolectric measures text roughly (about 0.5 dp a letter), so room is checked, not text widths.
        assertTrue("the label keeps room: ${value.left - label.left}", (value.left - label.left).value > 150f)
        assertTrue("the value is capped at 180", (value.right - value.left).value <= 180.5f)
        assertTrue("the value sits right of the label", value.left >= label.right)
        assertTrue("the value stays inside the panel", value.right <= panel.right)
        rule.onNodeWithText("SOURCES").getBoundsInRoot()
        rule.onNodeWithText("Files on your NAS", useUnmergedTree = true).getBoundsInRoot()
    }
}
