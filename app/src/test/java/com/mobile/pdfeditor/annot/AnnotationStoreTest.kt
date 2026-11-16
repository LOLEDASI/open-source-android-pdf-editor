package com.mobile.pdfeditor.annot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationStoreTest {
    private fun stroke(store: AnnotationStore, page: Int = 0, vararg pts: Pair<Float, Float>) =
        InkStroke(store.newId(), page, InkTool.Pen, InkDefaults.Pen, pts.map { PagePoint(it.first, it.second) })

    @Test
    fun addUndoRedo() {
        val store = AnnotationStore()
        val a = stroke(store, 0, 0f to 0f, 10f to 10f)
        store.add(a)
        assertEquals(listOf(a), store.strokes)
        assertTrue(store.state.value.canUndo)
        assertTrue(store.state.value.dirty)

        store.undo()
        assertEquals(emptyList<InkStroke>(), store.strokes)
        assertFalse(store.state.value.dirty) // back to the loaded state
        assertTrue(store.state.value.canRedo)

        store.redo()
        assertEquals(listOf(a), store.strokes)
    }

    @Test
    fun newEditClearsRedo() {
        val store = AnnotationStore()
        store.add(stroke(store))
        store.undo()
        store.add(stroke(store))
        assertFalse(store.state.value.canRedo)
    }

    @Test
    fun eraseIsOneUndoStep() {
        val store = AnnotationStore()
        val a = stroke(store, 0, 0f to 0f)
        val b = stroke(store, 0, 50f to 50f)
        store.add(a); store.add(b)
        store.remove(setOf(a.id, b.id))
        assertTrue(store.strokes.isEmpty())
        store.undo()
        assertEquals(listOf(a, b), store.strokes)
    }

    @Test
    fun loadAndSaveTrackDirty() {
        val store = AnnotationStore()
        val loaded = InkStroke(7, 0, InkTool.Pen, InkDefaults.Pen, listOf(PagePoint(1f, 1f)))
        store.load(listOf(loaded))
        assertFalse(store.state.value.dirty)
        assertFalse(store.state.value.canUndo)
        assertTrue("ids continue after loaded ones", store.newId() > 7)

        store.add(stroke(store))
        assertTrue(store.state.value.dirty)
        store.markSaved()
        assertFalse(store.state.value.dirty)
    }

    @Test
    fun eraserHitTest() {
        val s = InkStroke(1, 0, InkTool.Pen, InkStyle(0, 2f, 1f), listOf(PagePoint(0f, 0f), PagePoint(100f, 0f)))
        assertTrue(s.isNear(PagePoint(50f, 5f), 8f))   // beside the middle of the line
        assertFalse(s.isNear(PagePoint(50f, 20f), 8f))
        assertFalse(s.isNear(PagePoint(120f, 0f), 8f)) // past the end
    }
}
