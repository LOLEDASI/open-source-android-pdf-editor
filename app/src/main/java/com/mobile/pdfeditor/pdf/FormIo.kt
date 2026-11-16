package com.mobile.pdfeditor.pdf

import com.mobile.pdfeditor.annot.FieldKind
import com.mobile.pdfeditor.annot.FormField
import com.mobile.pdfeditor.annot.FormWidget
import com.mobile.pdfeditor.annot.PageRect
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.util.IdentityHashMap

class FormData(val fields: List<FormField>, val values: Map<String, String>)

/** Reads a PDF's AcroForm fields and writes filled-in values back into them. */
internal object FormIo {
    fun read(doc: PDDocument, boxes: List<PageBox>, displayRect: (PDRectangle, PageBox) -> PageRect): FormData {
        val form = doc.documentCatalog.acroForm ?: return FormData(emptyList(), emptyMap())
        // Widgets don't reliably point back at their page, so map them from each page's annotations.
        val pageOf = IdentityHashMap<COSDictionary, Int>()
        doc.pages.forEachIndexed { i, page ->
            try {
                page.annotations.forEach { if (it is PDAnnotationWidget) pageOf[it.cosObject] = i }
            } catch (_: Exception) {
            }
        }
        val fields = mutableListOf<FormField>()
        val values = mutableMapOf<String, String>()
        for (field in form.fieldTree) {
            val kind = when (field) {
                is PDTextField -> FieldKind.Text
                is PDCheckBox -> FieldKind.Check
                is PDRadioButton -> FieldKind.Radio
                is PDChoice -> if (field.isMultiSelect) null else FieldKind.Choice
                else -> null // signatures, push buttons, multi-select lists
            } ?: continue
            val name = field.fullyQualifiedName ?: continue
            val widgets = field.widgets.mapNotNull { w ->
                val page = pageOf[w.cosObject] ?: return@mapNotNull null
                val rect = w.rectangle ?: return@mapNotNull null
                FormWidget(page, displayRect(rect, boxes[page]), onValueOf(w))
            }
            if (widgets.isEmpty()) continue
            fields += FormField(
                name = name,
                label = field.alternateFieldName?.takeIf { it.isNotBlank() } ?: field.partialName ?: name,
                kind = kind,
                widgets = widgets,
                options = (field as? PDChoice)?.let { c ->
                    val export = c.optionsExportValues
                    val display = c.optionsDisplayValues
                    export.indices.map { i -> export[i] to (display.getOrNull(i) ?: export[i]) }
                }.orEmpty(),
                multiline = (field as? PDTextField)?.isMultiline == true,
                readOnly = field.isReadOnly,
                maxLen = (field as? PDTextField)?.maxLen ?: -1,
                fontSizePt = fontSize(field),
            )
            values[name] = currentValue(field)
        }
        return FormData(fields, values)
    }

    /** Sets every field whose value differs from [values]; returns false if any couldn't be set. */
    fun apply(doc: PDDocument, values: Map<String, String>): Boolean {
        if (values.isEmpty()) return true
        val form = doc.documentCatalog.acroForm ?: return true
        var ok = true
        for (field in form.fieldTree) {
            val name = field.fullyQualifiedName ?: continue
            val wanted = values[name] ?: continue
            if (field.isReadOnly || currentValue(field) == wanted) continue
            try {
                when (field) {
                    is PDCheckBox -> if (wanted == FormField.OFF) field.unCheck() else field.check()
                    is PDButton -> field.setValue(wanted)
                    else -> field.setValue(wanted)
                }
            } catch (_: Exception) {
                ok = false
            }
        }
        // If an appearance couldn't be built (e.g. a missing font), let other viewers draw it.
        if (!ok) form.needAppearances = true
        return ok
    }

    private fun currentValue(field: PDField): String = when (field) {
        is PDCheckBox -> if (field.isChecked) field.onValue else FormField.OFF
        is PDButton -> field.value?.takeIf { it.isNotEmpty() } ?: FormField.OFF
        is PDChoice -> field.value.firstOrNull().orEmpty()
        else -> field.valueAsString.orEmpty()
    }

    /** The "on" state name of a checkbox or radio widget (its appearance key other than Off). */
    private fun onValueOf(w: PDAnnotationWidget): String? = try {
        val normal = w.appearance?.normalAppearance
        if (normal != null && normal.isSubDictionary) {
            normal.subDictionary.keys.map { it.name }.firstOrNull { it != FormField.OFF }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun fontSize(field: PDField): Float? {
        val da = (field.cosObject.getDictionaryObject(COSName.DA) as? com.tom_roush.pdfbox.cos.COSString)?.string
            ?: return null
        return Regex("""([\d.]+)\s+Tf""").find(da)?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it > 0f }
    }
}
