package com.portalcast.tv.ui.discover

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.portalcast.tv.data.model.DiscoverFacets
import com.portalcast.tv.data.model.FacetValue
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Filters panel on a 960×540 dp TV screen under Discover's top bar: every row, down to the
 * Sources at the bottom, can be reached. Series has one selector more than Movies (Channel), and
 * "IPTV categories", the last row, was below the screen edge there (owner 2026-11-04: "the series
 * section showed no IPTV categories").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class FilterMenuFitTest {
    @get:Rule val rule = createComposeRule()

    private val v = listOf(FacetValue("A", 3), FacetValue("B", 2))
    private val series = DiscoverFacets(genres = v, countries = v, themes = v, languages = v, networks = v, imdbRatings = v)

    @Test
    fun theLastSourceIsReachableWithEverySelectorShown() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                FilterMenu(
                    facets = series,
                    query = null,
                    focus = FilterDim.entries.associateWith { FocusRequester() },
                    topPx = with(rule.density) { 120.dp.roundToPx() },
                    onOpen = {},
                    onClear = {},
                    onDismiss = {},
                    sources = filterSources("series", onOpenMyLibrary = {}, onOpenCategories = {}) {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(400)
        val node = rule.onNodeWithText("IPTV categories", useUnmergedTree = true)
        // The remote moves focus down to it: it must come into view, at its full height.
        rule.onNodeWithText("IPTV categories").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.mainClock.advanceTimeBy(400)
        val b = node.getBoundsInRoot()
        assertTrue("on screen: $b", b.bottom.value <= 540.5f && b.top.value >= 0f)
        assertTrue("full height: $b", (b.bottom - b.top).value > 10f)
    }
}
