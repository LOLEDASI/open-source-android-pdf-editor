package com.mobile.pdfeditor.pdf

import com.mobile.pdfeditor.annot.PagePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class PageBoxTest {
    private val eps = 0.001f

    @Test
    fun unrotatedFlipsY() {
        val box = PageBox(0f, 0f, 612f, 792f, 0)
        assertEquals(0f to 792f, box.toPdf(PagePoint(0f, 0f)))      // top-left
        assertEquals(612f to 0f, box.toPdf(PagePoint(612f, 792f)))  // bottom-right
    }

    @Test
    fun cropBoxOffsetIsApplied() {
        val box = PageBox(36f, 18f, 540f, 756f, 0)
        assertEquals(36f to 774f, box.toPdf(PagePoint(0f, 0f)))
    }

    @Test
    fun rotatedPagesSwapDisplaySize() {
        val box = PageBox(0f, 0f, 612f, 792f, 90)
        assertEquals(792f, box.displayWidth, eps)
        assertEquals(612f, box.displayHeight, eps)
        // Rotating 90° clockwise puts the original bottom-left corner at the top-left.
        assertEquals(0f to 0f, box.toPdf(PagePoint(0f, 0f)))
    }

    @Test
    fun roundTripAllRotations() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val box = PageBox(10f, 20f, 600f, 800f, rotation)
            for (p in listOf(PagePoint(0f, 0f), PagePoint(123.5f, 45.25f), PagePoint(box.displayWidth, box.displayHeight))) {
                val (u, v) = box.toPdf(p)
                // Every displayed point must land inside the crop box...
                assert(u in 10f - eps..610f + eps && v in 20f - eps..820f + eps) { "rot $rotation: $p -> ($u,$v)" }
                // ...and come back unchanged.
                val back = box.fromPdf(u, v)
                assertEquals("rot $rotation x", p.x, back.x, eps)
                assertEquals("rot $rotation y", p.y, back.y, eps)
            }
        }
    }

    @Test
    fun normalizesRotation() {
        assertEquals(270, PageBox.normalizeRotation(-90))
        assertEquals(0, PageBox.normalizeRotation(360))
        assertEquals(90, PageBox.normalizeRotation(450))
    }
}
