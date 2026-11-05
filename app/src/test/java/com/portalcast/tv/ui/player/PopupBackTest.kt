package com.portalcast.tv.ui.player

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.closeOnBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back in the subtitles / audio popup closed it only on the second press (owner 2026-11-04).
 * The same set-up as PlayerScreen: an always-on BackHandler that leaves the player, one for the
 * popup, the screen's own focusable box (the video, where Left/Right/OK act) and the popup's
 * buttons inside it with the focus. Keys go through the activity, as the remote's do. Compose
 * turns an unused Back into "focus out to the parent": with a focusable parent that move
 * succeeds and takes the key, so the BackHandler only saw the second press.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PopupBackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(true)
    private var exits by mutableIntStateOf(0)

    private fun show(withCloseOnBack: Boolean) {
        val first = FocusRequester()
        rule.setContent {
            BackHandler { exits++ }
            BackHandler(enabled = open) { open = false }
            Box(Modifier.focusable()) {
                if (open) {
                    Column(if (withCloseOnBack) Modifier.closeOnBack { open = false } else Modifier) {
                        LazyColumn {
                            items(4) { i ->
                                TvButton(text = "Row $i", onClick = {},
                                    modifier = if (i == 0) Modifier.focusRequester(first).testTag("row0") else Modifier)
                            }
                        }
                    }
                }
            }
        }
        rule.runOnIdle { first.requestFocus() }
        rule.onNodeWithTag("row0").assertIsFocused()
    }

    private fun pressBack() {
        rule.runOnUiThread {
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        rule.waitForIdle()
    }

    @Test
    fun withTheScreensBackHandlerAloneItTookTwoPresses() {
        show(withCloseOnBack = false)
        pressBack()
        rule.runOnIdle { assertTrue("the first Back only moved focus to the screen", open) }
        pressBack()
        rule.runOnIdle {
            assertFalse(open)
            assertEquals(0, exits)
        }
    }

    @Test
    fun theFirstBackClosesThePopupAndDoesNotLeaveThePlayer() {
        show(withCloseOnBack = true)
        pressBack()
        rule.runOnIdle {
            assertFalse(open)
            assertEquals(0, exits)
        }
    }
}
