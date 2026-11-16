package com.mobile.pdfeditor.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.mobile.pdfeditor.annot.PagePoint
import com.mobile.pdfeditor.annot.PageRect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Marks areas to redact on one page. Drag to draw a box; tap a box to remove it.
 * Two-finger gestures are left to the viewer for zooming and scrolling.
 */
@Composable
fun RedactLayer(
    pageWidthPt: Float,
    boxes: List<PageRect>,
    active: Boolean,
    onAdd: (PageRect) -> Unit,
    onRemove: (PageRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!active && boxes.isEmpty()) return
    var live by remember { mutableStateOf<PageRect?>(null) }
    val current by rememberUpdatedState(boxes)
    val add by rememberUpdatedState(onAdd)
    val remove by rememberUpdatedState(onRemove)
    val input = if (!active) Modifier else Modifier.pointerInput(pageWidthPt) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val k = size.width / pageWidthPt
            val start = PagePoint(down.position.x / k, down.position.y / k)
            down.consume()
            var end = start
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.size > 1) { cancelled = true; break }
                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                if (c.isConsumed) { cancelled = true; break }
                if (!c.pressed) break
                c.consume()
                end = PagePoint(c.position.x / k, c.position.y / k)
                live = rect(start, end)
            }
            live = null
            if (cancelled) return@awaitEachGesture
            if (abs(end.x - start.x) < 3f && abs(end.y - start.y) < 3f) {
                // A tap: remove the box under the finger, if any.
                current.lastOrNull { start in it }?.let(remove)
            } else {
                add(rect(start, end))
            }
        }
    }
    Canvas(modifier.fillMaxSize().then(input).testTag("redactLayer")) {
        val k = size.width / pageWidthPt
        (boxes + listOfNotNull(live)).forEach { r ->
            val topLeft = Offset(r.x * k, r.y * k)
            val sz = Size(r.w * k, r.h * k)
            drawRect(Color.Black.copy(alpha = 0.75f), topLeft, sz)
            drawRect(Color(0xFFC8352B), topLeft, sz, style = Stroke(width = 2f))
        }
    }
}

private fun rect(a: PagePoint, b: PagePoint) =
    PageRect(min(a.x, b.x), min(a.y, b.y), abs(a.x - b.x), abs(a.y - b.y)).let { r -> r.copy(w = max(r.w, 2f), h = max(r.h, 2f)) }
