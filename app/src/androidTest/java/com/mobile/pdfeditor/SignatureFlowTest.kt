package com.mobile.pdfeditor

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.geometry.Offset
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
import com.mobile.pdfeditor.data.RecentFilesRepository
import com.mobile.pdfeditor.data.SignatureLibrary
import com.mobile.pdfeditor.pdf.InkPdfIo
import com.mobile.pdfeditor.ui.signature.SIGNATURE_PAD_TAG
import com.mobile.pdfeditor.ui.theme.PdfEditorTheme
import com.mobile.pdfeditor.ui.viewer.ViewerScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Draw a signature, place it, move it, save, and read it back from the PDF. */
@RunWith(AndroidJUnit4::class)
class SignatureFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun makePdf(): Uri {
        val file = File(context.cacheDir, "sign_test.pdf")
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
        page.canvas.drawText("Sign below", 72f, 72f, Paint().apply { textSize = 24f })
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return Uri.fromFile(file)
    }

    @Test
    fun drawPlaceMoveSaveReload() {
        val uri = makePdf()
        val library = SignatureLibrary(context)
        compose.setContent {
            PdfEditorTheme {
                ViewerScreen(uri = uri.toString(), recents = RecentFilesRepository(context), signatures = library, onBack = {})
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Page 1")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) {
            // Signing needs the PDF structure loaded; tapping early shows a "getting ready" message.
            compose.onNodeWithText("Sign").performClick()
            compose.onAllNodes(hasText("Place on page")).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag(SIGNATURE_PAD_TAG).performTouchInput {
            swipe(start = Offset(width * 0.2f, height * 0.6f), end = Offset(width * 0.8f, height * 0.4f), durationMillis = 300)
        }
        compose.onNodeWithText("Place on page").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Signature")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Unsaved changes").assertExists()

        // Placed signatures start selected: drag it down-right.
        compose.onNodeWithContentDescription("Signature").performTouchInput {
            swipe(start = center, end = center + Offset(60f, 120f), durationMillis = 300)
        }

        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Unsaved changes")).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodes(hasText("Saving…")).fetchSemanticsNodes().isEmpty()
        }

        val saved = runBlocking { InkPdfIo.read(context, uri) }
        assertEquals(1, saved.stamps.size)
        val stamp = saved.stamps.single()
        val image = saved.images[stamp.imageKey]
        assertNotNull("signature image survives the round trip", image)
        assertTrue("image keeps transparency", image!!.hasAlpha())
        // Started centred (x = 226) and was dragged right.
        assertTrue("moved right: x=${stamp.rect.x}", stamp.rect.x > 230f)
        assertEquals(160f, stamp.rect.w, 1f)
    }
}
