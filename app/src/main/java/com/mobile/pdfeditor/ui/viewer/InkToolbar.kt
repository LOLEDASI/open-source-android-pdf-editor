package com.mobile.pdfeditor.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.pdfeditor.annot.InkDefaults
import com.mobile.pdfeditor.annot.InkStyle
import com.mobile.pdfeditor.annot.InkTool
import com.mobile.pdfeditor.annot.PagePoint
import kotlin.math.roundToInt
import kotlin.math.sin

private val colorNames = mapOf(
    0x15171A to "Black", 0xC8352B to "Red", 0xE8762B to "Orange", 0xF2C230 to "Yellow",
    0x1F8A4C to "Green", 0x0F7B6C to "Teal", 0x2457D6 to "Blue", 0x7A3FC4 to "Purple",
)

fun inkColor(rgb: Int) = Color(0xFF000000.toInt() or rgb)

/** Floating tool picker shown over the page while annotating. */
@Composable
fun InkToolbar(
    tool: EditTool,
    onTool: (EditTool) -> Unit,
    swatch: InkStyle,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(6.dp),
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Stop annotating") }
            ToolButton(EditTool.Pen, tool, Icons.Outlined.Draw, "Pen", onTool)
            ToolButton(EditTool.Highlighter, tool, Icons.Outlined.Highlight, "Highlighter", onTool)
            ToolButton(EditTool.Eraser, tool, Icons.Outlined.CleaningServices, "Eraser", onTool)
            Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))
            IconButton(onClick = onOpenSettings) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(inkColor(swatch.color))
                        .border(2.dp, Color.White, CircleShape)
                        .semantics { contentDescription = "Color and size" },
                )
            }
        }
    }
}

@Composable
private fun ToolButton(t: EditTool, selected: EditTool, icon: ImageVector, label: String, onTool: (EditTool) -> Unit) {
    IconToggleButton(
        checked = t == selected,
        onCheckedChange = { onTool(t) },
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(icon, contentDescription = label)
    }
}

/** Color, thickness and opacity for the pen or highlighter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkSettingsSheet(
    kind: InkTool,
    onKind: (InkTool) -> Unit,
    style: InkStyle,
    onStyle: (InkStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val widthRange = if (kind == InkTool.Highlighter) 6f..30f else 0.5f..12f

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (kind == InkTool.Highlighter) "Highlighter" else "Pen",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onDismiss) { Text("Done") }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                InkTool.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = t == kind,
                        onClick = { onKind(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, InkTool.entries.size),
                    ) { Text(if (t == InkTool.Highlighter) "Highlighter" else "Pen") }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("Color")
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                ) {
                    InkDefaults.Palette.forEach { rgb ->
                        val selected = rgb == style.color
                        Box(
                            Modifier
                                .size(44.dp)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onStyle(style.copy(color = rgb)) })
                                .semantics { contentDescription = colorNames[rgb] ?: "Color" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(if (selected) 30.dp else 34.dp)
                                    .clip(CircleShape)
                                    .background(inkColor(rgb)),
                            )
                            if (selected) {
                                Box(Modifier.size(40.dp).border(2.dp, inkColor(rgb), CircleShape))
                            }
                        }
                    }
                }
            }

            Column {
                Row {
                    Label("Thickness", Modifier.weight(1f))
                    Label("${"%.1f".format(style.widthPt)} pt")
                }
                Slider(
                    value = style.widthPt.coerceIn(widthRange),
                    onValueChange = { onStyle(style.copy(widthPt = it)) },
                    valueRange = widthRange,
                )
            }

            Column {
                Row {
                    Label("Opacity", Modifier.weight(1f))
                    Label("${(style.opacity * 100).roundToInt()}%")
                }
                Slider(
                    value = style.opacity,
                    onValueChange = { onStyle(style.copy(opacity = it)) },
                    valueRange = 0.1f..1f,
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                Canvas(Modifier.fillMaxWidth().height(84.dp)) {
                    // Preview at roughly page scale (1pt ≈ 1.5dp on a phone-width page).
                    val pxPerPt = 1.5f * density
                    val wPt = size.width / pxPerPt
                    val hPt = size.height / pxPerPt
                    val pts = (0..40).map { i ->
                        val t = i / 40f
                        PagePoint(wPt * (0.08f + 0.84f * t), hPt / 2 + sin(t * 6.3f) * hPt * 0.22f)
                    }
                    drawInk(kind, style, pts, pxPerPt)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
