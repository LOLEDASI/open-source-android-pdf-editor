package com.mobile.pdfeditor

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobile.pdfeditor.annot.Annotations
import com.mobile.pdfeditor.annot.PageRect
import com.mobile.pdfeditor.annot.TextBox
import com.mobile.pdfeditor.data.RecentFilesRepository
import com.mobile.pdfeditor.data.SignatureLibrary
import com.mobile.pdfeditor.pdf.InkPdfIo
import com.mobile.pdfeditor.pdf.TextLines
import com.mobile.pdfeditor.ui.theme.PdfEditorTheme
import com.mobile.pdfeditor.ui.viewer.TEXT_FIELD_TAG
import com.mobile.pdfeditor.ui.viewer.ViewerScreen
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TextEditTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A PDF with real text: "Revenue 2025: 1,200" at 14pt, baseline y=100 from the top. */
    private fun makeTextPdf(): File {
        val file = File(context.cacheDir, "text_src.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14f)
                cs.newLineAtOffset(72f, 792f - 100f)
                cs.showText("Revenue 2025: 1,200")
                cs.endText()
            }
            doc.save(file)
        }
        return file
    }

    @Test
    fun findsExistingTextLines() {
        val file = makeTextPdf()
        val lines = PDDocument.load(file).use { TextLines.extract(it, 0) }
        assertEquals(1, lines.size)
        val line = lines.single()
        assertEquals("Revenue 2025: 1,200", line.text)
        assertEquals(14f, line.sizePt, 0.5f)
        assertTrue("bold detected", line.bold)
        assertEquals(72f, line.rect.x, 1f)
        assertTrue("box covers the baseline at y=100", line.rect.y < 100f && line.rect.y + line.rect.h > 100f)
    }

    @Test
    fun textBoxSurvivesSaveAndReload() = runBlocking<Unit> {
        val file = makeTextPdf()
        val box = TextBox(1, 0, PageRect(70f, 85f, 200f, 20f), "Revenue 2025: 1,350", sizePt = 14f, bold = true, cover = 0xFFFFFF)
        val out = InkPdfIo.write(context, Uri.fromFile(file), Annotations(texts = listOf(box)))
        val back = InkPdfIo.read(context, Uri.fromFile(out)).texts.single()
        assertEquals(box.text, back.text)
        assertEquals(box.sizePt, back.sizePt, 0.01f)
        assertEquals(true, back.bold)
        assertEquals(0xFFFFFF, back.cover)
        assertEquals(box.rect.x, back.rect.x, 0.5f)
        assertEquals(box.rect.y, back.rect.y, 0.5f)
        assertEquals(box.rect.w, back.rect.w, 0.5f)
        out.delete()
    }

    @Test
    fun tapExistingTextToEditIt() {
        val file = makeTextPdf()
        compose.setContent {
            PdfEditorTheme {
                ViewerScreen(uri = Uri.fromFile(file).toString(), recents = RecentFilesRepository(context), signatures = SignatureLibrary(context), onBack = {})
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Page 1")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) {
            compose.onNodeWithText("Edit text").performClick()
            compose.onAllNodes(hasText("Tap text to change it, or tap empty space to add text")).fetchSemanticsNodes().isNotEmpty()
        }
        // Tap on the line: x=100pt, y=96pt on a 612pt-wide page.
        compose.onNodeWithTag("textLayer-0").performTouchInput {
            val k = width / 612f
            click(Offset(100f * k, 96f * k))
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Revenue 2025: 1,200")).fetchSemanticsNodes().size >= 2 }
        compose.onNodeWithTag(TEXT_FIELD_TAG).performTextReplacement("Revenue 2025: 1,350")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Unsaved changes")).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodes(hasText("Saving…")).fetchSemanticsNodes().isEmpty()
        }
        val saved = runBlocking { InkPdfIo.read(context, Uri.fromFile(file)) }
        assertEquals("Revenue 2025: 1,350", saved.texts.single().text)
        assertEquals(0xFFFFFF, saved.texts.single().cover)
    }
}
