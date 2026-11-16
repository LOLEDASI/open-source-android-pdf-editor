package com.mobile.pdfeditor.annot

import org.junit.Assert.assertEquals
import org.junit.Test

class TextLayoutTest {
    // Every character is 1 unit wide, so widths are just lengths.
    private val measure: (String) -> Float = { it.length.toFloat() }

    @Test
    fun wrapsAtSpaces() {
        assertEquals(listOf("the quick", "brown fox"), TextLayout.wrap("the quick brown fox", 10f, measure))
    }

    @Test
    fun keepsNewlinesAndBlankLines() {
        assertEquals(listOf("a", "", "b"), TextLayout.wrap("a\n\nb", 10f, measure))
    }

    @Test
    fun breaksOverlongWords() {
        assertEquals(listOf("abcd", "efgh", "ij"), TextLayout.wrap("abcdefghij", 4f, measure))
    }

    @Test
    fun heightGrowsWithLinesButNotBelowMinimum() {
        val box = TextBox(1, 0, PageRect(0f, 0f, 100f, 30f), "x", sizePt = 10f)
        assertEquals(30f, TextLayout.height(box, 1), 0.01f) // 1 line needs 16pt, min is 30
        assertEquals(10f * 1.2f * 5 + 4f, TextLayout.height(box, 5), 0.01f)
    }
}
