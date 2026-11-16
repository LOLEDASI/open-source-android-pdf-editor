package com.mobile.pdfeditor

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobile.pdfeditor.annot.InkTool
import com.mobile.pdfeditor.data.RecentFilesRepository
import com.mobile.pdfeditor.data.SignatureLibrary
import com.mobile.pdfeditor.pdf.InkPdfIo
import com.mobile.pdfeditor.ui.theme.PdfEditorTheme
import com.mobile.pdfeditor.ui.viewer.ViewerScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Draw on a page, save, and check the stroke is in the file as an Ink annotation. */
@RunWith(AndroidJUnit4::class)
class AnnotateSaveTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun makePdf(): Uri {
        val file = File(context.cacheDir, "annotate_test.pdf")
        val doc = PdfDocument()
        repeat(2) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(612, 792, i + 1).create())
            page.canvas.drawText("Page ${i + 1}", 72f, 72f, Paint().apply { textSize = 24f })
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return Uri.fromFile(file)
    }

    @Test
    fun drawSaveAndReload() {
        val uri = makePdf()
        compose.setContent {
            PdfEditorTheme {
                ViewerScreen(uri = uri.toString(), recents = RecentFilesRepository(context), signatures = SignatureLibrary(context), onBack = {})
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Page 1")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Annotate").performClick()
        compose.onNodeWithContentDescription("Stop annotating").assertExists()

        compose.onNodeWithTag("ink-0").performTouchInput {
            swipe(start = centerLeft + androidx.compose.ui.geometry.Offset(40f, 0f), end = center, durationMillis = 300)
        }
        compose.onNodeWithText("Unsaved changes").assertExists()

        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Unsaved changes")).fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodes(hasText("Saving…")).fetchSemanticsNodes().isEmpty() }

        val saved = runBlocking { InkPdfIo.read(context, uri) }
        assertEquals(1, saved.strokes.size)
        val stroke = saved.strokes.single()
        assertEquals(0, stroke.page)
        assertEquals(InkTool.Pen, stroke.tool)
        assertTrue("stroke kept its shape", stroke.points.size > 2)
    }
}
