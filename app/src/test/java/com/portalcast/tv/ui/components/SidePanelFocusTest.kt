package com.portalcast.tv.ui.components

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * While a side panel is open the remote stays in it, and nothing else can take the focus
 * (owner 2026-11-05: "when filter section is on don't allow them to go back; if they do go out
 * of filter section then close the filter"). Focus lost all the same closes it (SidePanel).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SidePanelFocusTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(true)
    private var genreShown by mutableStateOf(true)
    private val page = FocusRequester()
    private val row = FocusRequester()

    private fun show() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                // Something on the page behind, left of the panel.
                TvButton(text = "Page", onClick = {}, modifier = Modifier.align(Alignment.CenterStart).focusRequester(page).testTag("page"))
                if (open) {
                    SidePanel(title = "Filters", onDismiss = { open = false }) {
                        if (genreShown) PanelRow(label = "Genre", value = "Any", onClick = {}, modifier = Modifier.focusRequester(row).testTag("row"))
                        PanelRow(label = "Language", value = "Any", onClick = {}, modifier = Modifier.testTag("lang"))
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(400)
        rule.runOnIdle { row.requestFocus() }
        rule.onNodeWithTag("row").assertIsFocused()
    }

    private fun press(code: Int) {
        rule.runOnUiThread {
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        rule.waitForIdle()
    }

    @Test
    fun theRemoteCannotLeaveThePanel() {
        show()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        rule.onNodeWithTag("row").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithTag("row").assertIsFocused()
        rule.runOnIdle { assertTrue(open) }
    }

    @Test
    fun nothingElseCanTakeTheFocus() {
        show()
        rule.runOnIdle { page.requestFocus() }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("row").assertIsFocused()
        rule.runOnIdle { assertTrue(open) }
    }

    @Test
    fun aRowThatGoesHandsTheFocusToTheNextOne() {
        show()
        // The focused row goes (a list that changed under it): the focus stays in the panel, on
        // the next row, and the panel stays open (no dead remote, no surprise close).
        rule.runOnIdle { genreShown = false }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("lang").assertIsFocused()
        rule.runOnIdle { assertTrue(open) }
    }
}
