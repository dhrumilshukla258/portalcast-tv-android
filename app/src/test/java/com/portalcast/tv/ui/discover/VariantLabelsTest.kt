package com.portalcast.tv.ui.discover

import org.junit.Assert.assertEquals
import org.junit.Test

class VariantLabelsTest {

    private fun q(t: String) = VariantChip(t, VariantChipKind.Quality)
    private fun s(t: String) = VariantChip(t, VariantChipKind.Subtitles)

    @Test
    fun languageWithSubtitlesAndQuality() {
        assertEquals(
            VariantLabelParts("Hindi", listOf(s("English subs"), q("4K"), q("HDR"))),
            VariantLabels.parse("Hindi (+ English Subtitles) 4K HDR"),
        )
    }

    @Test
    fun originalAudioWithSubtitlesOnly() {
        assertEquals(
            VariantLabelParts("Original Audio", listOf(s("English subs"))),
            VariantLabels.parse("Original Audio (English Subtitles)"),
        )
    }

    @Test
    fun languageAndQuality() {
        assertEquals(VariantLabelParts("Marathi", listOf(q("1080p"))), VariantLabels.parse("Marathi 1080p"))
        assertEquals(VariantLabelParts("Hindi", listOf(q("Dual Audio"), q("WEB-DL"))), VariantLabels.parse("Hindi Dual Audio WEB-DL"))
    }

    @Test
    fun qualityOnlyStaysPrimary() {
        assertEquals(VariantLabelParts("4K HDR", emptyList()), VariantLabels.parse("4K HDR"))
    }

    @Test
    fun rawNamesAndBlanksAreUntouchedExceptTrailingTags() {
        assertEquals(VariantLabelParts("Narco Sub", emptyList()), VariantLabels.parse("Narco Sub"))
        assertEquals(VariantLabelParts("The Batman", listOf(q("HD"))), VariantLabels.parse("The Batman HD"))
        // "HD" inside a word is not a tag.
        assertEquals(VariantLabelParts("Shadow", emptyList()), VariantLabels.parse("Shadow"))
        assertEquals(VariantLabelParts("Version", emptyList()), VariantLabels.parse(null))
        assertEquals(VariantLabelParts("Version", emptyList()), VariantLabels.parse("  "))
    }
}
