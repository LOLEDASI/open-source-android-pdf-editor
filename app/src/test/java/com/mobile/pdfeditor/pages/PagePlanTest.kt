package com.mobile.pdfeditor.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagePlanTest {
    private fun PagePlan.sources() = pages.map { it.source }

    @Test
    fun originalIsUnchanged() {
        val p = PagePlan.original(3)
        assertEquals(listOf(0, 1, 2), p.sources())
        assertTrue(p.isUnchanged)
    }

    @Test
    fun moveReorders() {
        val p = PagePlan.original(4).move(0, 2)
        assertEquals(listOf(1, 2, 0, 3), p.sources())
        assertFalse(p.isUnchanged)
        assertEquals(p, p.move(1, 1))
    }

    @Test
    fun rotateWraps() {
        val p = PagePlan.original(2).rotate(setOf(0L)).rotate(setOf(0L)).rotate(setOf(0L)).rotate(setOf(0L))
        assertEquals(0, p.pages[0].rotation)
        assertEquals(90, PagePlan.original(2).rotate(setOf(1L)).pages[1].rotation)
    }

    @Test
    fun deleteKeepsAtLeastOnePage() {
        val p = PagePlan.original(3)
        assertEquals(listOf(0, 2), p.delete(setOf(1L)).sources())
        assertEquals(p, p.delete(setOf(0L, 1L, 2L)))
    }

    @Test
    fun duplicateInsertsAfterWithNewKeys() {
        val p = PagePlan.original(2).duplicate(setOf(0L))
        assertEquals(listOf(0, 0, 1), p.sources())
        assertEquals(3, p.pages.map { it.key }.toSet().size)
    }

    @Test
    fun insertBlankAfterSelection() {
        val (p, key) = PagePlan.original(2).insertBlank(afterKey = 0L, width = 612f, height = 792f)
        assertEquals(listOf(0, -1, 1), p.sources())
        assertTrue(p.pages[1].isBlank)
        assertEquals(key, p.pages[1].key)
        assertFalse(p.isUnchanged)
        val (end, _) = PagePlan.original(1).insertBlank(null, 100f, 100f)
        assertEquals(listOf(0, -1), end.sources())
    }

    @Test
    fun subsetKeepsDocumentOrder() {
        val p = PagePlan.original(4).move(3, 0) // 3,0,1,2
        assertEquals(listOf(3, 1), p.subset(setOf(1L, 3L)).sources())
    }
}
