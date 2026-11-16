package com.mobile.pdfeditor

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobile.pdfeditor.data.RecentFilesRepository
import com.mobile.pdfeditor.data.SignatureLibrary
import com.mobile.pdfeditor.pdf.PdfTools
import com.mobile.pdfeditor.pdf.TextLines
import com.mobile.pdfeditor.ui.theme.PdfEditorTheme
import com.mobile.pdfeditor.ui.tools.OpenMode
import com.mobile.pdfeditor.ui.viewer.ViewerScreen
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RedactUnlockTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun textPdf(name: String): File {
        val file = File(context.cacheDir, name)
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 14f)
                cs.newLineAtOffset(72f, 700f); cs.showText("Secret account 12345")
                cs.newLineAtOffset(0f, -300f); cs.showText("Public footer")
                cs.endText()
            }
            doc.save(file)
        }
        return file
    }

    private fun readText(uri: Uri): String {
        val f = PdfTools.copyToCache(context, uri)
        return PDDocument.load(f).use { d -> TextLines.extract(d, 0).joinToString("|") { it.text } }.also { f.delete() }
    }

    @Test
    fun redactModeRemovesMarkedText() {
        val file = textPdf("redact_ui.pdf")
        var opened: String? = null
        compose.setContent {
            PdfEditorTheme {
                ViewerScreen(
                    uri = Uri.fromFile(file).toString(),
                    recents = RecentFilesRepository(context),
                    signatures = SignatureLibrary(context),
                    onBack = {},
                    onOpenDocument = { opened = it },
                    startMode = OpenMode.Redact,
                )
            }
        }
        compose.waitUntil(8_000) { compose.onAllNodes(hasText("Apply redactions")).fetchSemanticsNodes().isNotEmpty() }
        // Drag a box over "Secret account 12345" (x 60–260pt, y 75–100pt on a 612pt page).
        compose.onAllNodesWithTag("redactLayer").onFirst().performTouchInput {
            val k = width / 612f
            swipe(Offset(60f * k, 75f * k), Offset(260f * k, 100f * k), durationMillis = 300)
        }
        compose.onNodeWithText("Apply redactions").performClick()
        compose.waitUntil(15_000) { opened != null }
        val text = readText(Uri.parse(opened))
        assertFalse(text, text.contains("Secret"))
        // The rest of a redacted page becomes an image; the original file is untouched.
        assertTrue(PDDocument.load(file).use { d -> TextLines.extract(d, 0).any { it.text.contains("Secret") } })
        context.contentResolver.delete(Uri.parse(opened), null, null)
    }

    @Test
    fun lockedPdfAsksForPasswordAndOpensUnlockedCopy() {
        val locked = File(context.cacheDir, "locked_ui.pdf")
        PdfTools.lock(textPdf("plain_ui.pdf"), locked, "open sesame")
        var opened: String? = null
        compose.setContent {
            PdfEditorTheme {
                ViewerScreen(
                    uri = Uri.fromFile(locked).toString(),
                    recents = RecentFilesRepository(context),
                    signatures = SignatureLibrary(context),
                    onBack = {},
                    onOpenDocument = { opened = it },
                )
            }
        }
        compose.waitUntil(8_000) { compose.onAllNodes(hasText("Unlock")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput("wrong")
        compose.onNodeWithText("Unlock").performClick()
        compose.waitUntil(8_000) { compose.onAllNodes(hasText("That password isn't right.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("open sesame")
        compose.onNodeWithText("Unlock").performClick()
        compose.waitUntil(10_000) { opened != null }
        assertNotNull(opened)
        assertTrue(readText(Uri.parse(opened)).contains("Secret account"))
        context.contentResolver.delete(Uri.parse(opened), null, null)
    }
}
