package com.mobile.pdfeditor.annot

/** Placement and resizing rules for signature stamps, in displayed page points. */
object StampGeometry {
    const val DEFAULT_WIDTH_PT = 160f
    const val INITIALS_WIDTH_PT = 70f
    const val MIN_WIDTH_PT = 24f

    /** Centre an image of [imgW]×[imgH] px on a page, [widthPt] wide (shrunk to fit narrow pages). */
    fun place(pageW: Float, pageH: Float, imgW: Int, imgH: Int, widthPt: Float, centerY: Float = pageH / 2f): PageRect {
        val aspect = imgH.toFloat() / imgW.coerceAtLeast(1)
        var w = widthPt.coerceAtMost(pageW * 0.8f)
        var h = w * aspect
        if (h > pageH * 0.5f) { h = pageH * 0.5f; w = h / aspect }
        return clamp(PageRect((pageW - w) / 2f, centerY - h / 2f, w, h), pageW, pageH)
    }

    /** Move by a delta, keeping the stamp fully on the page. */
    fun move(r: PageRect, dx: Float, dy: Float, pageW: Float, pageH: Float) =
        clamp(r.copy(x = r.x + dx, y = r.y + dy), pageW, pageH)

    /** Drag the bottom-right corner by [dx]; keeps the aspect ratio and the top-left fixed. */
    fun resize(r: PageRect, dx: Float, pageW: Float, pageH: Float): PageRect {
        val aspect = r.h / r.w
        val maxW = minOf(pageW - r.x, (pageH - r.y) / aspect)
        val w = (r.w + dx).coerceIn(MIN_WIDTH_PT.coerceAtMost(maxW), maxW)
        return r.copy(w = w, h = w * aspect)
    }

    fun clamp(r: PageRect, pageW: Float, pageH: Float): PageRect = r.copy(
        x = r.x.coerceIn(0f, (pageW - r.w).coerceAtLeast(0f)),
        y = r.y.coerceIn(0f, (pageH - r.h).coerceAtLeast(0f)),
    )
}
