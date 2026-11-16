package com.mobile.pdfeditor.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.pdfeditor.annot.FieldKind
import com.mobile.pdfeditor.annot.FormField
import com.mobile.pdfeditor.annot.FormValues
import com.mobile.pdfeditor.annot.FormWidget
import kotlin.math.roundToInt

private val FieldTint = Color(0x262457D6)
private val FieldBorder = Color(0x992457D6)

/**
 * Form fields on one page. Values are always drawn (Android's renderer doesn't draw form fields);
 * in fill mode ([active]) fields are highlighted and tappable.
 */
@Composable
fun FormLayer(
    pageWidthPt: Float,
    fields: List<Pair<FormField, FormWidget>>,
    values: Map<String, String>,
    active: Boolean,
    editing: String?,
    onTap: (FormField, FormWidget) -> Unit,
) {
    if (fields.isEmpty()) return
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val k = constraints.maxWidth / pageWidthPt
        fun dp(pt: Float) = with(density) { (pt * k).toDp() }
        fields.forEach { (field, w) ->
            val r = w.rect
            val value = values[field.name]
            val tappable = active && !field.readOnly
            Box(
                Modifier
                    .offset { IntOffset((r.x * k).roundToInt(), (r.y * k).roundToInt()) }
                    .size(dp(r.w), dp(r.h))
                    .then(if (tappable) Modifier.background(FieldTint) else Modifier)
                    .then(
                        when {
                            editing == field.name -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                            tappable -> Modifier.border(1.dp, FieldBorder)
                            else -> Modifier
                        },
                    )
                    .then(if (tappable) Modifier.clickable { onTap(field, w) } else Modifier)
                    .clipToBounds()
                    .testTag("field-${field.name}")
                    .semantics { contentDescription = "${field.label}: ${value ?: ""}" },
                contentAlignment = if (field.multiline) Alignment.TopStart else Alignment.CenterStart,
            ) {
                when (field.kind) {
                    FieldKind.Text, FieldKind.Choice -> {
                        val shown = if (field.kind == FieldKind.Choice) {
                            field.options.firstOrNull { it.first == value }?.second ?: value
                        } else value
                        if (!shown.isNullOrEmpty()) {
                            val size = FormValues.displaySize(field, r)
                            BasicText(
                                shown,
                                maxLines = if (field.multiline) Int.MAX_VALUE else 1,
                                style = TextStyle(
                                    color = Color(0xFF15171A),
                                    fontSize = with(density) { (size * k).toSp() },
                                    letterSpacing = 0.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                ),
                                modifier = Modifier.padding(horizontal = dp(2f)),
                            )
                        }
                    }
                    FieldKind.Check -> if (FormValues.isOn(w, value)) {
                        Icon(Icons.Outlined.Check, null, tint = Color(0xFF15171A), modifier = Modifier.fillMaxSize().padding(dp(1f)))
                    }
                    FieldKind.Radio -> if (FormValues.isOn(w, value)) {
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(dp(minOf(r.w, r.h) * 0.5f))
                                .background(Color(0xFF15171A), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

const val FORM_FIELD_TAG = "formField"

/** Keyboard bar for typing into a text field. */
@Composable
fun FormTextBar(field: FormField, value: String, hasNext: Boolean, onChange: (String) -> Unit, onNext: () -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(field.name) { focus.requestFocus() }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, modifier = Modifier.imePadding()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { onChange(if (field.maxLen > 0) it.take(field.maxLen) else it) },
                label = { Text(field.label) },
                singleLine = !field.multiline,
                maxLines = if (field.multiline) 4 else 1,
                keyboardOptions = KeyboardOptions(imeAction = if (hasNext && !field.multiline) ImeAction.Next else ImeAction.Done),
                keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(FORM_FIELD_TAG),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f))
                if (hasNext) TextButton(onClick = onNext) { Text("Next field") }
                Button(onClick = onDone) { Text("Done") }
            }
        }
    }
}

/** Pick one option of a dropdown field. */
@Composable
fun ChoiceDialog(field: FormField, value: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.label) },
        text = {
            LazyColumn {
                items(field.options) { (export, display) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(export == value, role = Role.RadioButton, onClick = { onPick(export) })
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = export == value, onClick = null)
                        Text(display.ifEmpty { "(blank)" }, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
