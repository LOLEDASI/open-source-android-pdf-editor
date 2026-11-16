package com.mobile.pdfeditor.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.mobile.pdfeditor.annot.InkStroke
import com.mobile.pdfeditor.annot.InkStyle
import com.mobile.pdfeditor.annot.InkTool
import com.mobile.pdfeditor.annot.PagePoint
import com.mobile.pdfeditor.annot.isNear
import kotlin.math.hypot

enum class EditTool { Pen, Highlighter, Eraser }

/** Eraser reach in page points. */
private const val ERASER_RADIUS_PT = 8f

/** Skip points closer than this (page points) to keep strokes light. */
private const val MIN_POINT_GAP_PT = 0.6f

/**
 * Draws one page's ink and, when [tool] is set, turns single-finger drags into strokes or erasing.
 * Two-finger gestures are left to the viewer (zoom / scroll) and cancel a stroke in progress.
 */
@Composable
fun InkLayer(
    pageWidthPt: Float,
    strokes: List<InkStroke>,
    tool: EditTool?,
    penStyle: InkStyle,
    highlighterStyle: InkStyle,
    onStroke: (InkTool, InkStyle, List<PagePoint>) -> Unit,
    onErase: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val livePoints = remember { mutableStateListOf<PagePoint>() }
    var erasing by remember { mutableStateOf(emptySet<Long>()) }
    val currentStrokes by rememberUpdatedState(strokes)
    val currentOnStroke by rememberUpdatedState(onStroke)
    val currentOnErase by rememberUpdatedState(onErase)

    val input = if (tool == null) Modifier else Modifier.pointerInput(tool, penStyle, highlighterStyle, pageWidthPt) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val ptPerPx = pageWidthPt / size.width
            fun toPt(o: Offset) = PagePoint(o.x * ptPerPx, o.y * ptPerPx)

            down.consume()
            var cancelled = false
            livePoints.clear()
            val first = toPt(down.position)
            if (tool == EditTool.Eraser) {
                erasing = hits(currentStrokes, first)
            } else {
                livePoints.add(first)
            }

            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.size > 1) { cancelled = true; break }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) { cancelled = true; break } // the viewer took it (pinch)
                if (!change.pressed) break
                change.consume()
                val p = toPt(change.position)
                if (tool == EditTool.Eraser) {
                    erasing = erasing + hits(currentStrokes, p)
                } else {
                    val last = livePoints.last()
                    if (hypot(p.x - last.x, p.y - last.y) >= MIN_POINT_GAP_PT) livePoints.add(p)
                }
            }

            if (!cancelled) {
                if (tool == EditTool.Eraser) {
                    if (erasing.isNotEmpty()) currentOnErase(erasing)
                } else if (livePoints.isNotEmpty()) {
                    val inkTool = if (tool == EditTool.Highlighter) InkTool.Highlighter else InkTool.Pen
                    val style = if (tool == EditTool.Highlighter) highlighterStyle else penStyle
                    currentOnStroke(inkTool, style, livePoints.toList())
                }
            }
            livePoints.clear()
            erasing = emptySet()
        }
    }

    Canvas(modifier.fillMaxSize().then(input)) {
        val pxPerPt = size.width / pageWidthPt
        strokes.forEach { s ->
            if (s.id !in erasing) drawInk(s.tool, s.style, s.points, pxPerPt)
        }
        if (livePoints.isNotEmpty() && tool != null && tool != EditTool.Eraser) {
            val inkTool = if (tool == EditTool.Highlighter) InkTool.Highlighter else InkTool.Pen
            drawInk(inkTool, if (tool == EditTool.Highlighter) highlighterStyle else penStyle, livePoints, pxPerPt)
        }
    }
}

private fun hits(strokes: List<InkStroke>, p: PagePoint): Set<Long> =
    strokes.filter { it.isNear(p, ERASER_RADIUS_PT) }.mapTo(HashSet()) { it.id }

fun DrawScope.drawInk(tool: InkTool, style: InkStyle, points: List<PagePoint>, pxPerPt: Float) {
    if (points.isEmpty()) return
    val color = Color(0xFF000000.toInt() or style.color).copy(alpha = style.opacity)
    val width = style.widthPt * pxPerPt
    val blend = if (tool == InkTool.Highlighter) BlendMode.Multiply else BlendMode.SrcOver
    val cap = if (tool == InkTool.Highlighter) StrokeCap.Square else StrokeCap.Round

    if (points.size == 1) {
        val c = Offset(points[0].x * pxPerPt, points[0].y * pxPerPt)
        if (tool == InkTool.Highlighter) {
            drawRect(color, Offset(c.x - width / 2, c.y - width / 2), Size(width, width), blendMode = blend)
        } else {
            drawCircle(color, width / 2, c, blendMode = blend)
        }
        return
    }

    // Quadratic curves through segment midpoints smooth out finger jitter.
    val path = Path()
    fun at(i: Int) = Offset(points[i].x * pxPerPt, points[i].y * pxPerPt)
    path.moveTo(at(0).x, at(0).y)
    for (i in 1 until points.size) {
        val prev = at(i - 1)
        val cur = at(i)
        path.quadraticTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
    }
    at(points.lastIndex).let { path.lineTo(it.x, it.y) }
    drawPath(path, color, style = Stroke(width = width, cap = cap, join = StrokeJoin.Round), blendMode = blend)
}
