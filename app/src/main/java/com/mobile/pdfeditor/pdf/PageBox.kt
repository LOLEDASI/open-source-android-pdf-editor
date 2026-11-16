package com.mobile.pdfeditor.pdf

import com.mobile.pdfeditor.annot.PagePoint

/**
 * A page's visible box (CropBox) in PDF user space plus its /Rotate value.
 * Converts between what the user sees (top-left origin, y down, rotation applied, the same space
 * Android's PdfRenderer draws in) and PDF user space (bottom-left origin, y up, unrotated).
 */
data class PageBox(
    val llx: Float,
    val lly: Float,
    val width: Float,
    val height: Float,
    /** 0, 90, 180 or 270, clockwise. */
    val rotation: Int,
) {
    val displayWidth: Float get() = if (rotation % 180 == 0) width else height
    val displayHeight: Float get() = if (rotation % 180 == 0) height else width

    /** Displayed (top-left) point to PDF user space. */
    fun toPdf(p: PagePoint): Pair<Float, Float> = when (rotation) {
        90 -> (llx + p.y) to (lly + p.x)
        180 -> (llx + width - p.x) to (lly + p.y)
        270 -> (llx + width - p.y) to (lly + height - p.x)
        else -> (llx + p.x) to (lly + height - p.y)
    }

    /** PDF user space to displayed (top-left) point. */
    fun fromPdf(u: Float, v: Float): PagePoint = when (rotation) {
        90 -> PagePoint(v - lly, u - llx)
        180 -> PagePoint(llx + width - u, v - lly)
        270 -> PagePoint(lly + height - v, llx + width - u)
        else -> PagePoint(u - llx, lly + height - v)
    }

    companion object {
        fun normalizeRotation(r: Int): Int = ((r % 360) + 360) % 360 / 90 * 90
    }
}
