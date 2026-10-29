package com.portalcast.tv.ui.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back on a title in Home's rows moves focus up to the tabs with requestFocus(). The content's
 * exit rule (HomeScreen) is asked for that too: it must refuse only the remote's Down/Left/Right,
 * or Back on a title does nothing (owner, 2026-10-29).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class BackToTabsTest {
    @get:Rule val rule = createComposeRule()

    private fun run(exitRule: (FocusDirection, FocusRequester) -> FocusRequester): Pair<FocusRequester, FocusRequester> {
        val tab = FocusRequester()
        val card = FocusRequester()
        rule.setContent {
            val t = remember { tab }
            Column {
                Row(Modifier.focusGroup()) {
                    Box(Modifier.width(100.dp).height(40.dp).focusRequester(t).focusable().testTag("tab"))
                }
                Box(
                    Modifier.fillMaxWidth().height(200.dp)
                        .focusProperties { exit = { dir -> exitRule(dir, t) } }
                        .focusGroup(),
                ) {
                    Box(Modifier.width(100.dp).height(150.dp).focusRequester(card).focusable().testTag("card"))
                }
            }
        }
        return tab to card
    }

    @Test
    fun backFromATitleReachesTheTabs() {
        val (tab, card) = run { dir, t ->
            when (dir) {
                FocusDirection.Up -> t
                FocusDirection.Down, FocusDirection.Left, FocusDirection.Right -> FocusRequester.Cancel
                else -> FocusRequester.Default
            }
        }
        rule.runOnIdle { card.requestFocus() }
        rule.onNodeWithTag("card").assertIsFocused()
        rule.runOnIdle { tab.requestFocus() }
        rule.onNodeWithTag("tab").assertIsFocused()
    }

    /** The rule before the fix: focus stayed on the title (the bug). */
    @Test
    fun theOldRuleKeptFocusOnTheTitle() {
        val (tab, card) = run { dir, t -> if (dir == FocusDirection.Up) t else FocusRequester.Cancel }
        rule.runOnIdle { card.requestFocus() }
        rule.runOnIdle { tab.requestFocus() }
        rule.onNodeWithTag("tab").assertIsNotFocused()
        rule.onNodeWithTag("card").assertIsFocused()
    }
}
