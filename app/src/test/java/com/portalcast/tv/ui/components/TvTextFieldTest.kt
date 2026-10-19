package com.portalcast.tv.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvTextFieldTest {
    @Test fun `single appended char right after opening is stray`() = assertTrue(isStrayFromOpen("", "q", 40))
    @Test fun `same char after the window is real typing`() = assertFalse(isStrayFromOpen("", "q", 900))
    @Test fun `paste or multi-char change is never stray`() = assertFalse(isStrayFromOpen("", "qw", 40))
    @Test fun `deletion is never stray`() = assertFalse(isStrayFromOpen("ab", "a", 40))
    @Test fun `insertion in the middle is not stray`() = assertFalse(isStrayFromOpen("ab", "aqb", 40))
    @Test fun `field never opened by key (huge elapsed) is not stray`() = assertFalse(isStrayFromOpen("", "q", Long.MAX_VALUE))
}
