package com.mobile.pdfeditor.annot

enum class FieldKind { Text, Check, Radio, Choice }

/** One clickable box of a field. Checkboxes and radio buttons carry the value they stand for. */
data class FormWidget(val page: Int, val rect: PageRect, val onValue: String? = null)

/** A fillable field from the PDF's form, identified by its fully qualified [name]. */
data class FormField(
    val name: String,
    val label: String,
    val kind: FieldKind,
    val widgets: List<FormWidget>,
    /** Choice fields: export value to display text. */
    val options: List<Pair<String, String>> = emptyList(),
    val multiline: Boolean = false,
    val readOnly: Boolean = false,
    val maxLen: Int = -1,
    /** Text size from the field's appearance string; null means "fit to the box". */
    val fontSizePt: Float? = null,
) {
    companion object {
        const val OFF = "Off"
    }
}

object FormValues {
    /** The value a tap on [widget] of [field] produces, given the current [value]. */
    fun tap(field: FormField, widget: FormWidget, value: String?): String? = when (field.kind) {
        FieldKind.Check -> if (value != null && value != FormField.OFF) FormField.OFF else widget.onValue
        FieldKind.Radio -> widget.onValue
        else -> null
    }

    fun isOn(widget: FormWidget, value: String?) = widget.onValue != null && widget.onValue == value

    /** Text size for showing a value in [rect]: the field's own size, or one that fits the box. */
    fun displaySize(field: FormField, rect: PageRect): Float =
        field.fontSizePt?.takeIf { it > 0f } ?: (rect.h * 0.62f).coerceIn(6f, 12f)

    /** "1 of 3"-style order of editable text and choice fields, for "Next". */
    fun nextEditable(fields: List<FormField>, current: String): FormField? {
        val editable = fields.filter { !it.readOnly && (it.kind == FieldKind.Text || it.kind == FieldKind.Choice) }
        val i = editable.indexOfFirst { it.name == current }
        return editable.getOrNull(i + 1)
    }
}
