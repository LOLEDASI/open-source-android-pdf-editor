package com.mobile.pdfeditor.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobile.pdfeditor.annot.PagePoint
import com.mobile.pdfeditor.annot.PageRect
import com.mobile.pdfeditor.annot.StampGeometry
import com.mobile.pdfeditor.annot.TextBox
import kotlin.math.roundToInt

/**
 * Text boxes on one page. Always drawn; only interactive in text mode ([active]), where a tap on
 * empty space calls [onTapEmpty], a tap on a box selects it, and a tap on the selected box edits it.
 */
@Composable
fun TextLayer(
    pageWidthPt: Float,
    pageHeightPt: Float,
    boxes: List<TextBox>,
    active: Boolean,
    selectedId: Long?,
    onTapEmpty: (PagePoint) -> Unit,
    onSelect: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onMove: (Long, PageRect) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapEmpty by rememberUpdatedState(onTapEmpty)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .then(
                if (!active) Modifier else Modifier.pointerInput(pageWidthPt) {
                    detectTapGestures(onTap = { o ->
                        val k = size.width / pageWidthPt
                        tapEmpty(PagePoint(o.x / k, o.y / k))
                    })
                },
            ),
    ) {
        val pxPerPt = constraints.maxWidth / pageWidthPt
        boxes.forEach { box ->
            TextBoxView(box, pxPerPt, pageWidthPt, pageHeightPt, active, box.id == selectedId, onSelect, onEdit, onMove, onDelete)
        }
    }
}

@Composable
private fun TextBoxView(
    box: TextBox,
    pxPerPt: Float,
    pageW: Float,
    pageH: Float,
    active: Boolean,
    selected: Boolean,
    onSelect: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onMove: (Long, PageRect) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val density = LocalDensity.current
    var live by remember(box.rect) { mutableStateOf<PageRect?>(null) }
    val rect = live ?: box.rect
    val currentRect by rememberUpdatedState(rect)
    fun px(pt: Float) = (pt * pxPerPt).roundToInt()
    fun dp(pt: Float) = with(density) { (pt * pxPerPt).toDp() }
    fun sp(pt: Float) = with(density) { (pt * pxPerPt).toSp() }
    val color = Color(0xFF000000.toInt() or box.color)

    val gestures = when {
        !active -> Modifier
        selected -> Modifier
            .pointerInput(box.id) { detectTapGestures(onTap = { onEdit(box.id) }) }
            .pointerInput(box.id, pxPerPt) {
                var acc = currentRect
                detectDragGestures(
                    onDragStart = { acc = currentRect },
                    onDragEnd = { onMove(box.id, acc); live = null },
                    onDragCancel = { live = null },
                ) { change, d ->
                    change.consume()
                    acc = StampGeometry.move(acc, d.x / pxPerPt, d.y / pxPerPt, pageW, pageH)
                    live = acc
                }
            }
        else -> Modifier.pointerInput(box.id) { detectTapGestures(onTap = { onSelect(box.id) }) }
    }

    Box(
        Modifier
            .offset { IntOffset(px(rect.x), px(rect.y)) }
            .width(dp(rect.w))
            .heightIn(min = dp(rect.h))
            .then(box.cover?.let { Modifier.background(Color(0xFF000000.toInt() or it)) } ?: Modifier)
            .then(if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .then(gestures)
            .testTag("text-${box.id}")
            .semantics { contentDescription = "Text: ${box.text}" }
            .padding(dp(TextBox.PAD)),
    ) {
        // Plain text style (no theme letter-spacing or font padding) so the preview matches
        // what gets written into the PDF.
        BasicText(
            if (box.text.isEmpty() && selected) "Type here" else box.text,
            style = TextStyle(
                color = if (box.text.isEmpty()) color.copy(alpha = 0.35f) else color,
                fontSize = sp(box.sizePt),
                lineHeight = sp(box.sizePt * TextBox.LINE_HEIGHT),
                fontWeight = if (box.bold) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = 0.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            ),
        )
    }

    if (selected && active) {
        // Width handle on the right edge, vertically centred on the box's minimum height.
        val handle = 28.dp
        val handlePx = with(density) { handle.roundToPx() }
        Box(
            Modifier
                .offset { IntOffset(px(rect.x + rect.w) - handlePx / 2, px(rect.y + rect.h / 2) - handlePx / 2) }
                .size(handle)
                .pointerInput(box.id, pxPerPt) {
                    var acc = currentRect
                    detectDragGestures(
                        onDragStart = { acc = currentRect },
                        onDragEnd = { onMove(box.id, acc); live = null },
                        onDragCancel = { live = null },
                    ) { change, d ->
                        change.consume()
                        acc = acc.copy(w = (acc.w + d.x / pxPerPt).coerceIn(20f, pageW - acc.x))
                        live = acc
                    }
                }
                .semantics { contentDescription = "Resize text box" },
        ) {
            Box(
                Modifier
                    .size(width = 8.dp, height = 22.dp)
                    .offset(10.dp, 3.dp)
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
        val buttonPx = with(density) { 40.dp.roundToPx() }
        val above = px(rect.y) - buttonPx - 8
        FilledIconButton(
            onClick = { onDelete(box.id) },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .offset { IntOffset(px(rect.x + rect.w) - buttonPx, if (above >= 0) above else px(rect.y + rect.h) + 8) }
                .size(40.dp),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete text", tint = MaterialTheme.colorScheme.onError)
        }
    }
}
