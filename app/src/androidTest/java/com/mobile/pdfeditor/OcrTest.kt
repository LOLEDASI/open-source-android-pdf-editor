package com.mobile.pdfeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobile.pdfeditor.pdf.Ocr
import com.mobile.pdfeditor.pdf.PdfTools
import com.mobile.pdfeditor.pdf.TextLines
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OcrTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A "scanned" page: a photo of printed lines, with no real text in the PDF. */
    private fun scannedPdf(): File {
        val bmp = Bitmap.createBitmap(1700, 2200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 60f; typeface = Typeface.SERIF }
        listOf("Quarterly Report 2025", "Revenue grew by twelve percent", "Net profit reached 4.2 million").forEachIndexed { i, line ->
            canvas.drawText(line, 150f, 300f + i * 120f, paint)
        }
        val out = File(context.cacheDir, "scanned.pdf")
        PdfTools.imagesToPdf(listOf(bmp), out, a4 = false)
        return out
    }

    @Test
    fun addsSearchableTextToScannedPages() = runBlocking<Unit> {
        val src = scannedPdf()
        assertEquals(0, PDDocument.load(src).use { TextLines.extract(it, 0).size })

        val out = File(context.cacheDir, "scanned-ocr.pdf")
        val pages = Ocr.makeSearchable(src, out)
        assertEquals(1, pages)
        val text = PDDocument.load(out).use { PDFTextStripper().getText(it) }
        assertTrue(text, text.contains("Quarterly Report"))
        assertTrue(text, text.contains("Revenue grew"))
        // The recognised lines line up with the image, so "Edit text" can find them by position.
        val lines = PDDocument.load(out).use { TextLines.extract(it, 0) }
        val title = lines.first { it.text.contains("Quarterly") }
        // Title baseline is at 300px of 2200px on a page 842pt tall → ~115pt from the top.
        assertTrue("title y=${title.rect.y}", title.rect.y in 85f..120f)
    }

    @Test
    fun leavesTextPdfsAlone() = runBlocking<Unit> {
        val src = File(context.cacheDir, "texty.pdf")
        PDDocument().use { doc ->
            val page = com.tom_roush.pdfbox.pdmodel.PDPage()
            doc.addPage(page)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                cs.beginText(); cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(72f, 700f)
                repeat(4) { cs.showText("Line $it"); cs.newLineAtOffset(0f, -20f) }
                cs.endText()
            }
            doc.save(src)
        }
        assertEquals(0, Ocr.makeSearchable(src, File(context.cacheDir, "texty-ocr.pdf")))
    }
}
