package com.mobile.pdfeditor.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangesTest {
    @Test
    fun parsesRangesSinglesAndOpenEnds() {
        assertEquals(
            listOf(listOf(0, 1, 2), listOf(4), listOf(7, 8, 9)),
            PageRanges.parse("1-3, 5, 8-", 10).getOrThrow(),
        )
        assertEquals(listOf(listOf(0, 1)), PageRanges.parse("-2", 10).getOrThrow())
        assertEquals(listOf(listOf(2, 3)), PageRanges.parse(" 3 – 4 ", 10).getOrThrow())
    }

    @Test
    fun rejectsBadInput() {
        assertTrue(PageRanges.parse("", 5).isFailure)
        assertTrue(PageRanges.parse("0-2", 5).isFailure)
        assertTrue(PageRanges.parse("4-2", 5).isFailure)
        assertTrue(PageRanges.parse("6", 5).isFailure)
        assertTrue(PageRanges.parse("abc", 5).isFailure)
    }

    @Test
    fun chunksEveryNPages() {
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6)), PageRanges.chunks(7, 3))
    }
}
