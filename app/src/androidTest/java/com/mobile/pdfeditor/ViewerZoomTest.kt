package com.mobile.pdfeditor

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobile.pdfeditor.data.RecentFilesRepository
import com.mobile.pdfeditor.data.SignatureLibrary
import com.mobile.pdfeditor.ui.theme.PdfEditorTheme
import com.mobile.pdfeditor.ui.viewer.PAGE_VIEWPORT_TAG
import com.mobile.pdfeditor.ui.viewer.ViewerScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ViewerZoomTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var uri: String

    @Before
    fun makePdf() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "zoom_test.pdf")
        val doc = PdfDocument()
        repeat(3) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(612, 792, i + 1).create())
            page.canvas.drawText("Page ${i + 1}", 72f, 72f, Paint().apply { textSize = 24f })
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        uri = Uri.fromFile(file).toString()

        compose.setContent {
            PdfEditorTheme {
                ViewerScreen(uri = uri, recents = RecentFilesRepository(context), signatures = SignatureLibrary(context), onBack = {})
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Page 1"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Zoom percent as announced to accessibility services, e.g. "Zoom 250%". */
    private fun zoomPercent(): Int {
        val node = compose.onNodeWithTag(PAGE_VIEWPORT_TAG).fetchSemanticsNode()
        return node.config[SemanticsProperties.StateDescription].removePrefix("Zoom ").removeSuffix("%").toInt()
    }

    @Test
    fun doubleTapZoomsInAndOut() {
        assertEquals(100, zoomPercent())
        compose.onNodeWithTag(PAGE_VIEWPORT_TAG).performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        assertEquals(250, zoomPercent())

        compose.onNodeWithTag(PAGE_VIEWPORT_TAG).performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        assertEquals(100, zoomPercent())
    }

    @Test
    fun pinchZoomsIn() {
        compose.onNodeWithTag(PAGE_VIEWPORT_TAG).performTouchInput {
            pinch(
                start0 = center - Offset(60f, 0f), end0 = center - Offset(300f, 0f),
                start1 = center + Offset(60f, 0f), end1 = center + Offset(300f, 0f),
                durationMillis = 400,
            )
        }
        compose.waitForIdle()
        val zoom = zoomPercent()
        assertTrue("expected pinch zoom-in, got $zoom%", zoom in 300..500)
    }
}
