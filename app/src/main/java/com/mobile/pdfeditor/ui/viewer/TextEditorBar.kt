package com.mobile.pdfeditor.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mobile.pdfeditor.annot.TextBox
import kotlin.math.roundToInt

private val textColors = listOf(0x15171A to "Black", 0x1A2B6B to "Navy", 0x2457D6 to "Blue", 0xC8352B to "Red")

const val TEXT_FIELD_TAG = "textEditorField"

/** Shown above the keyboard while a text box is being typed into. */
@Composable
fun TextEditorBar(draft: TextBox, onChange: (TextBox) -> Unit, onDone: () -> Unit, onDelete: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(draft.id) { focus.requestFocus() }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, modifier = Modifier.imePadding()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = { onChange(draft.copy(sizePt = (draft.sizePt - 1f).coerceAtLeast(TextBox.MIN_SIZE))) }) {
                    Icon(Icons.Outlined.Remove, "Smaller text")
                }
                Text("${draft.sizePt.roundToInt()} pt", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { onChange(draft.copy(sizePt = (draft.sizePt + 1f).coerceAtMost(TextBox.MAX_SIZE))) }) {
                    Icon(Icons.Outlined.Add, "Larger text")
                }
                FilledIconToggleButton(checked = draft.bold, onCheckedChange = { onChange(draft.copy(bold = it)) }) {
                    Icon(Icons.Outlined.FormatBold, "Bold")
                }
                FilledIconToggleButton(
                    checked = draft.cover != null,
                    onCheckedChange = { onChange(draft.copy(cover = if (it) 0xFFFFFF else null)) },
                ) { Icon(Icons.Outlined.FormatColorFill, "White background (covers text underneath)") }
                Box(Modifier.weight(1f))
                textColors.forEach { (rgb, name) ->
                    val selected = draft.color == rgb
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .selectable(selected, role = Role.RadioButton, onClick = { onChange(draft.copy(color = rgb)) })
                            .semantics { contentDescription = name },
                    ) {
                        if (selected) Box(Modifier.size(30.dp).border(2.dp, Color(0xFF000000.toInt() or rgb), CircleShape))
                        Box(Modifier.size(22.dp).background(Color(0xFF000000.toInt() or rgb), CircleShape))
                    }
                }
            }
            OutlinedTextField(
                value = draft.text,
                onValueChange = { onChange(draft.copy(text = it)) },
                placeholder = { Text("Type your text") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(TEXT_FIELD_TAG),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                Box(Modifier.weight(1f))
                Button(onClick = onDone) { Text("Done") }
            }
        }
    }
}
