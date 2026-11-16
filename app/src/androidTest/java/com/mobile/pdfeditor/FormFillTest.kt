package com.mobile.pdfeditor

import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobile.pdfeditor.annot.FieldKind
import com.mobile.pdfeditor.data.RecentFilesRepository
import com.mobile.pdfeditor.data.SignatureLibrary
import com.mobile.pdfeditor.pdf.InkPdfIo
import com.mobile.pdfeditor.ui.theme.PdfEditorTheme
import com.mobile.pdfeditor.ui.viewer.FORM_FIELD_TAG
import com.mobile.pdfeditor.ui.viewer.ViewerScreen
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FormFillTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A one-page form: "name" text field, "agree" checkbox, "country" dropdown. */
    private fun makeForm(): File {
        val file = File(context.cacheDir, "form.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER)
            doc.addPage(page)
            val form = PDAcroForm(doc)
            doc.documentCatalog.acroForm = form
            form.defaultResources = PDResources().apply { put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA) }
            form.defaultAppearance = "/Helv 0 Tf 0 g"

            val name = PDTextField(form).apply { partialName = "name"; defaultAppearance = "/Helv 12 Tf 0 g" }
            name.widgets[0].apply { rectangle = PDRectangle(72f, 650f, 250f, 20f); this.page = page; page.annotations.add(this) }
            form.fields.add(name)

            val agree = PDCheckBox(form).apply { partialName = "agree" }
            agree.widgets[0].apply {
                rectangle = PDRectangle(72f, 600f, 14f, 14f); this.page = page; page.annotations.add(this)
                val normal = COSDictionary()
                normal.setItem(COSName.getPDFName("Yes"), PDAppearanceStream(doc).apply { bBox = PDRectangle(14f, 14f) })
                normal.setItem(COSName.Off, PDAppearanceStream(doc).apply { bBox = PDRectangle(14f, 14f) })
                appearance = PDAppearanceDictionary().apply { cosObject.setItem(COSName.N, normal) }
                setAppearanceState("Off")
            }
            agree.cosObject.setName(COSName.V, "Off")
            form.fields.add(agree)

            val country = PDComboBox(form).apply { partialName = "country"; defaultAppearance = "/Helv 12 Tf 0 g"; options = listOf("Pakistan", "United States") }
            country.widgets[0].apply { rectangle = PDRectangle(72f, 550f, 150f, 20f); this.page = page; page.annotations.add(this) }
            form.fields.add(country)
            doc.save(file)
        }
        return file
    }

    @Test
    fun readsFields() = runBlocking<Unit> {
        val data = InkPdfIo.read(context, Uri.fromFile(makeForm())).form
        assertEquals(listOf("name", "agree", "country"), data.fields.map { it.name })
        assertEquals(listOf(FieldKind.Text, FieldKind.Check, FieldKind.Choice), data.fields.map { it.kind })
        assertEquals("Yes", data.fields[1].widgets.single().onValue)
        assertEquals("Off", data.values["agree"])
        // 72pt from the left, 792-670 = 122pt from the top.
        assertEquals(72f, data.fields[0].widgets.single().rect.x, 0.5f)
        assertEquals(122f, data.fields[0].widgets.single().rect.y, 0.5f)
    }

    @Test
    fun fillAndSave() {
        val file = makeForm()
        compose.setContent {
            PdfEditorTheme {
                ViewerScreen(uri = Uri.fromFile(file).toString(), recents = RecentFilesRepository(context), signatures = SignatureLibrary(context), onBack = {})
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Page 1")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) {
            compose.onNodeWithText("Fill form").performClick()
            compose.onAllNodes(hasText("Tap a highlighted field to fill it in")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("field-name").performClick()
        compose.onNodeWithTag(FORM_FIELD_TAG).performTextReplacement("Ayesha Khan")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("field-agree").performClick()
        compose.onNodeWithTag("field-country").performClick()
        compose.onNodeWithText("Pakistan").performClick()

        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Unsaved changes")).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodes(hasText("Saving…")).fetchSemanticsNodes().isEmpty()
        }

        PDDocument.load(file).use { doc ->
            val form = doc.documentCatalog.acroForm
            assertEquals("Ayesha Khan", form.getField("name").valueAsString)
            assertTrue((form.getField("agree") as PDCheckBox).isChecked)
            assertEquals(listOf("Pakistan"), (form.getField("country") as PDComboBox).value)
        }
    }
}
