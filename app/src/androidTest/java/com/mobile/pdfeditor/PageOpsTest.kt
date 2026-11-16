package com.mobile.pdfeditor

import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobile.pdfeditor.annot.Annotations
import com.mobile.pdfeditor.annot.InkDefaults
import com.mobile.pdfeditor.annot.InkStroke
import com.mobile.pdfeditor.annot.InkTool
import com.mobile.pdfeditor.annot.PagePoint
import com.mobile.pdfeditor.pages.PagePlan
import com.mobile.pdfeditor.pdf.InkPdfIo
import com.mobile.pdfeditor.pdf.PageOps
import com.mobile.pdfeditor.pdf.PdfDocumentHandle
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PageOpsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Three pages with different widths (300, 400, 500) so we can tell them apart. */
    private fun makePdf(): File {
        val file = File(context.cacheDir, "pages_src.pdf")
        val doc = PdfDocument()
        listOf(300, 400, 500).forEachIndexed { i, w ->
            doc.finishPage(doc.startPage(PdfDocument.PageInfo.Builder(w, 700, i + 1).create()))
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun widths(file: File) = PDDocument.load(file).use { d -> d.pages.map { it.mediaBox.width.toInt() } }
    private fun rotations(file: File) = PDDocument.load(file).use { d -> d.pages.map { it.rotation } }

    @Test
    fun reorderRotateDuplicateDeleteInsert() = runBlocking<Unit> {
        val src = makePdf()
        var plan = PagePlan.original(3)
        plan = plan.move(0, 2)                 // 400, 500, 300
        plan = plan.rotate(setOf(1L))          // rotate the 400 page
        plan = plan.duplicate(setOf(2L))       // 400, 500, 500, 300
        plan = plan.delete(setOf(0L))          // drop the 300 page -> 400, 500, 500
        plan = plan.insertBlank(null, 250f, 250f).first // 400, 500, 500, blank
        val out = File(context.cacheDir, "pages_out.pdf")
        PageOps.apply(src, plan, out)

        assertEquals(listOf(400, 500, 500, 250), widths(out))
        assertEquals(listOf(90, 0, 0, 0), rotations(out))
        // Android's renderer can open the result (page tree is valid).
        PdfDocumentHandle.open(context, Uri.fromFile(out)).use { assertEquals(4, it.pageCount) }
    }

    @Test
    fun annotationsMoveWithTheirPage() = runBlocking<Unit> {
        val src = makePdf()
        val stroke = InkStroke(1, 0, InkTool.Pen, InkDefaults.Pen, listOf(PagePoint(10f, 10f), PagePoint(50f, 50f)))
        val annotated = InkPdfIo.write(context, Uri.fromFile(src), Annotations(strokes = listOf(stroke)))
        val out = File(context.cacheDir, "pages_ink.pdf")
        // Page 0 (with the stroke) moves to the end and is duplicated.
        PageOps.apply(annotated, PagePlan.original(3).move(0, 2).duplicate(setOf(0L)), out)

        val ink = InkPdfIo.read(context, Uri.fromFile(out))
        assertEquals(listOf(2, 3), ink.strokes.map { it.page }.sorted())
    }
}
