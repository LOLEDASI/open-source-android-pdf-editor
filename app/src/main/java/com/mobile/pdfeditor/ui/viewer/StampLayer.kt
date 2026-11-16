package com.mobile.pdfeditor.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobile.pdfeditor.annot.PageRect
import com.mobile.pdfeditor.annot.SignatureStamp
import com.mobile.pdfeditor.annot.StampGeometry
import kotlin.math.roundToInt

/**
 * Signatures placed on one page. Tap to select; drag a selected signature to move it, its
 * corner handle to resize, or the bin button to delete. Changes commit when the finger lifts,
 * so each move or resize is one undo step.
 */
@Composable
fun StampLayer(
    pageWidthPt: Float,
    pageHeightPt: Float,
    stamps: List<SignatureStamp>,
    images: Map<String, Bitmap>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onMove: (Long, PageRect) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (stamps.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pxPerPt = constraints.maxWidth / pageWidthPt
        stamps.forEach { stamp ->
            val bitmap = images[stamp.imageKey] ?: return@forEach
            Stamp(stamp, bitmap, pxPerPt, pageWidthPt, pageHeightPt, stamp.id == selectedId, onSelect, onMove, onDelete)
        }
    }
}

@Composable
private fun Stamp(
    stamp: SignatureStamp,
    bitmap: Bitmap,
    pxPerPt: Float,
    pageW: Float,
    pageH: Float,
    selected: Boolean,
    onSelect: (Long) -> Unit,
    onMove: (Long, PageRect) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val density = LocalDensity.current
    // Live rectangle while dragging; null means "use the stored one".
    var live by remember(stamp.rect) { mutableStateOf<PageRect?>(null) }
    val rect = live ?: stamp.rect
    val currentRect by rememberUpdatedState(rect)
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    fun dp(pt: Float) = with(density) { (pt * pxPerPt).toDp() }
    fun px(pt: Float) = (pt * pxPerPt).roundToInt()

    val gestures = if (selected) {
        Modifier.pointerInput(stamp.id, pxPerPt) {
            // Accumulate locally: several drag events can arrive before the next recomposition.
            var acc = currentRect
            detectDragGestures(
                onDragStart = { acc = currentRect },
                onDragEnd = { onMove(stamp.id, acc); live = null },
                onDragCancel = { live = null },
            ) { change, drag ->
                change.consume()
                acc = StampGeometry.move(acc, drag.x / pxPerPt, drag.y / pxPerPt, pageW, pageH)
                live = acc
            }
        }
    } else {
        Modifier.pointerInput(stamp.id) { detectTapGestures(onTap = { onSelect(stamp.id) }) }
    }

    Box(
        Modifier
            .offset { IntOffset(px(rect.x), px(rect.y)) }
            .size(dp(rect.w), dp(rect.h))
            .then(if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .then(gestures)
            .testTag("stamp-${stamp.id}")
            .semantics {
                contentDescription = "Signature"
                this.selected = selected
            },
    ) {
        Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
    }

    if (selected) {
        val handle = 28.dp
        val handlePx = with(density) { handle.roundToPx() }
        // Resize handle on the bottom-right corner.
        Box(
            Modifier
                .offset { IntOffset(px(rect.x + rect.w) - handlePx / 2, px(rect.y + rect.h) - handlePx / 2) }
                .size(handle)
                .pointerInput(stamp.id, pxPerPt) {
                    var acc = currentRect
                    detectDragGestures(
                        onDragStart = { acc = currentRect },
                        onDragEnd = { onMove(stamp.id, acc); live = null },
                        onDragCancel = { live = null },
                    ) { change, drag ->
                        change.consume()
                        acc = StampGeometry.resize(acc, drag.x / pxPerPt, pageW, pageH)
                        live = acc
                    }
                }
                .testTag("stamp-resize-${stamp.id}")
                .semantics { contentDescription = "Resize signature" },
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .offset(6.dp, 6.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        // Delete button just above the top-right corner (or below it at the page's top edge).
        val buttonPx = with(density) { 40.dp.roundToPx() }
        val above = px(rect.y) - buttonPx - with(density) { 6.dp.roundToPx() }
        FilledIconButton(
            onClick = { onDelete(stamp.id) },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .offset {
                    IntOffset(
                        px(rect.x + rect.w) - buttonPx,
                        if (above >= 0) above else px(rect.y + rect.h) + 6,
                    )
                }
                .size(40.dp),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete signature", tint = MaterialTheme.colorScheme.onError)
        }
    }
}
