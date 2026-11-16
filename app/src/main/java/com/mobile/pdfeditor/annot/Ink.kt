package com.mobile.pdfeditor.annot

import kotlin.math.hypot

/** A point on a page in PDF points (1/72 in), measured from the page's top-left as displayed. */
data class PagePoint(val x: Float, val y: Float)

enum class InkTool { Pen, Highlighter }

/** [color] is 0xRRGGBB (opacity is separate so it maps straight onto the PDF's /CA). */
data class InkStyle(val color: Int, val widthPt: Float, val opacity: Float)

data class InkStroke(
    val id: Long,
    val page: Int,
    val tool: InkTool,
    val style: InkStyle,
    val points: List<PagePoint>,
)

object InkDefaults {
    val Pen = InkStyle(color = 0xC8352B, widthPt = 2.5f, opacity = 1f)
    val Highlighter = InkStyle(color = 0xF2C230, widthPt = 14f, opacity = 0.4f)

    val Palette = listOf(
        0x15171A, 0xC8352B, 0xE8762B, 0xF2C230, 0x1F8A4C, 0x0F7B6C, 0x2457D6, 0x7A3FC4,
    )
}

/** True if [p] is within [radius] of any segment of [stroke]. Used by the eraser. */
fun InkStroke.isNear(p: PagePoint, radius: Float): Boolean {
    val reach = radius + style.widthPt / 2f
    if (points.size == 1) return dist(p, points[0]) <= reach
    for (i in 0 until points.lastIndex) {
        if (distToSegment(p, points[i], points[i + 1]) <= reach) return true
    }
    return false
}

private fun dist(a: PagePoint, b: PagePoint) = hypot(a.x - b.x, a.y - b.y)

private fun distToSegment(p: PagePoint, a: PagePoint, b: PagePoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    if (len2 == 0f) return dist(p, a)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
    return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
}

/** A rectangle on a page in displayed page points (top-left origin). */
data class PageRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    operator fun contains(p: PagePoint) = p.x in x..(x + w) && p.y in y..(y + h)
}

/** A placed signature image. [imageKey] points into the session's image table. */
data class SignatureStamp(
    val id: Long,
    val page: Int,
    val rect: PageRect,
    val imageKey: String,
)

/** Everything the user has added to the document. */
data class Annotations(
    val strokes: List<InkStroke> = emptyList(),
    val stamps: List<SignatureStamp> = emptyList(),
    val texts: List<TextBox> = emptyList(),
)
