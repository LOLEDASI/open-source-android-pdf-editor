package com.mobile.pdfeditor.annot

import com.mobile.pdfeditor.signature.SignatureRender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureStampTest {
    private val eps = 0.01f

    @Test
    fun placeCentresAndKeepsAspect() {
        val r = StampGeometry.place(pageW = 612f, pageH = 792f, imgW = 400, imgH = 100, widthPt = 160f)
        assertEquals(160f, r.w, eps)
        assertEquals(40f, r.h, eps)
        assertEquals((612f - 160f) / 2f, r.x, eps)
        assertEquals(792f / 2f - 20f, r.y, eps)
    }

    @Test
    fun placeNearPageEdgeStaysOnPage() {
        val r = StampGeometry.place(612f, 792f, 400, 100, 160f, centerY = 785f)
        assertEquals(792f - r.h, r.y, eps)
    }

    @Test
    fun placeShrinksOnNarrowPage() {
        val r = StampGeometry.place(pageW = 100f, pageH = 792f, imgW = 400, imgH = 100, widthPt = 160f)
        assertEquals(80f, r.w, eps) // 80% of page width
    }

    @Test
    fun moveIsClampedToPage() {
        val r = PageRect(10f, 10f, 100f, 50f)
        assertEquals(PageRect(0f, 0f, 100f, 50f), StampGeometry.move(r, -50f, -50f, 612f, 792f))
        assertEquals(PageRect(512f, 742f, 100f, 50f), StampGeometry.move(r, 1000f, 1000f, 612f, 792f))
    }

    @Test
    fun resizeKeepsAspectAndLimits() {
        val r = PageRect(100f, 100f, 100f, 50f)
        val bigger = StampGeometry.resize(r, 60f, 612f, 792f)
        assertEquals(160f, bigger.w, eps)
        assertEquals(80f, bigger.h, eps)
        assertEquals(StampGeometry.MIN_WIDTH_PT, StampGeometry.resize(r, -500f, 612f, 792f).w, eps)
        assertEquals(512f, StampGeometry.resize(r, 5000f, 612f, 792f).w, eps) // stops at page's right edge
    }

    @Test
    fun stampEditsAreUndoable() {
        val store = AnnotationStore()
        val s = SignatureStamp(store.newId(), 0, PageRect(0f, 0f, 100f, 40f), "k")
        store.addStamp(s)
        store.moveStamp(s.id, PageRect(50f, 50f, 100f, 40f))
        assertEquals(50f, store.stamps.single().rect.x, eps)
        store.undo()
        assertEquals(0f, store.stamps.single().rect.x, eps)
        store.remove(setOf(s.id))
        assertTrue(store.stamps.isEmpty())
        store.undo()
        assertEquals(1, store.stamps.size)
    }

    @Test
    fun movingToSamePlaceIsNotAnEdit() {
        val store = AnnotationStore()
        val s = SignatureStamp(store.newId(), 0, PageRect(0f, 0f, 100f, 40f), "k")
        store.load(emptyList(), listOf(s))
        store.moveStamp(s.id, s.rect)
        assertFalse(store.state.value.dirty)
        assertFalse(store.state.value.canUndo)
    }

    @Test
    fun photoInkThresholds() {
        assertEquals(255, SignatureRender.inkAlpha(20f))  // dark ink
        assertEquals(0, SignatureRender.inkAlpha(230f))   // white paper
        val mid = SignatureRender.inkAlpha(130f)          // anti-aliased edge
        assertTrue(mid in 1..254)
    }
}
