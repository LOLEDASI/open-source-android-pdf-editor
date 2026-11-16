package com.mobile.pdfeditor

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobile.pdfeditor.annot.Annotations
import com.mobile.pdfeditor.annot.InkDefaults
import com.mobile.pdfeditor.annot.InkStroke
import com.mobile.pdfeditor.annot.InkTool
import com.mobile.pdfeditor.annot.PagePoint
import com.mobile.pdfeditor.annot.PageRect
import com.mobile.pdfeditor.pdf.InkPdfIo
import com.mobile.pdfeditor.pdf.PdfDocumentHandle
import com.mobile.pdfeditor.pdf.PdfTools
import com.mobile.pdfeditor.pdf.TextLines
import com.mobile.pdfeditor.pdf.WrongPasswordException
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfToolsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun tmp(name: String) = File(context.cacheDir, name).also { it.delete() }

    /** [pages] letter pages, each with a line "Secret account 12345" and "Page N". */
    private fun textPdf(name: String, pages: Int): File {
        val file = tmp(name)
        PDDocument().use { doc ->
            repeat(pages) { i ->
                val page = PDPage(PDRectangle.LETTER)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 14f)
                    cs.newLineAtOffset(72f, 700f); cs.showText("Secret account 12345")
                    cs.newLineAtOffset(0f, -40f); cs.showText("Page ${i + 1}")
                    cs.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    private fun pages(f: File) = PDDocument.load(f).use { it.numberOfPages }
    private fun text(f: File, page: Int) = PDDocument.load(f).use { d -> TextLines.extract(d, page).joinToString("|") { it.text } }

    @Test
    fun mergeAndSplit() {
        val merged = tmp("merged.pdf")
        PdfTools.merge(listOf(textPdf("a.pdf", 2), textPdf("b.pdf", 3)), merged)
        assertEquals(5, pages(merged))
        val parts = PdfTools.split(merged, listOf(listOf(0, 1), listOf(4)), context.cacheDir)
        assertEquals(listOf(2, 1), parts.map(::pages))
        assertTrue(text(parts[1], 0).contains("Page 3")) // 5th page of merged = page 3 of b
    }

    @Test
    fun compressShrinksBigPhotos() = runBlocking<Unit> {
        val big = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)
        for (y in 0 until 1800 step 2) for (x in 0 until 2400 step 2) big.setPixel(x, y, Color.rgb(x % 256, y % 256, (x + y) % 256))
        val src = tmp("photo.pdf")
        PdfTools.imagesToPdf(listOf(big), src, a4 = false)
        val out = tmp("photo-small.pdf")
        assertTrue(PdfTools.compress(src, out, maxSide = 800, quality = 0.5f))
        assertTrue("${out.length()} < ${src.length()}", out.length() < src.length())
        PdfDocumentHandle.open(context, Uri.fromFile(out)).use { assertEquals(1, it.pageCount) }
    }

    @Test
    fun passwordLockAndUnlock() {
        val src = textPdf("plain.pdf", 1)
        val locked = tmp("locked.pdf")
        PdfTools.lock(src, locked, "s3cret!")
        assertTrue(PdfTools.needsPassword(locked))
        try {
            PdfTools.unlock(locked, tmp("x.pdf"), "wrong")
            fail("wrong password accepted")
        } catch (_: WrongPasswordException) {
        }
        val unlocked = tmp("unlocked.pdf")
        PdfTools.unlock(locked, unlocked, "s3cret!")
        assertFalse(PdfTools.needsPassword(unlocked))
        assertTrue(text(unlocked, 0).contains("Secret account"))
    }

    @Test
    fun watermarkAddsText() = runBlocking<Unit> {
        val src = textPdf("w.pdf", 2)
        val out = tmp("wm.pdf")
        PdfTools.watermark(src, out, "CONFIDENTIAL")
        // The diagonal text is drawn in light grey across the middle of every page.
        suspend fun greyPixels(f: File) = PdfDocumentHandle.open(context, Uri.fromFile(f)).use { doc ->
            val bmp = doc.render(1, 612)!!
            var n = 0
            for (y in 250 until 550 step 2) for (x in 100 until 512 step 2) {
                val p = bmp.getPixel(x, y)
                if (Color.red(p) in 150..240 && Color.red(p) == Color.green(p)) n++
            }
            n
        }
        assertEquals(0, greyPixels(src))
        assertTrue("watermark visible", greyPixels(out) > 200)
    }

    @Test
    fun flattenBakesAnnotationsIn() = runBlocking<Unit> {
        val src = textPdf("f.pdf", 1)
        val stroke = InkStroke(1, 0, InkTool.Pen, InkDefaults.Pen, listOf(PagePoint(100f, 300f), PagePoint(400f, 300f)))
        val annotated = InkPdfIo.write(context, Uri.fromFile(src), Annotations(strokes = listOf(stroke)))
        val out = tmp("flat.pdf")
        PdfTools.flatten(annotated, out)
        PDDocument.load(out).use { assertEquals(0, it.getPage(0).annotations.size) }
        // The ink is now part of the page, so Android's renderer draws it.
        PdfDocumentHandle.open(context, Uri.fromFile(out)).use { doc ->
            val bmp = doc.render(0, 612)!!
            val px = bmp.getPixel(250, 300)
            assertTrue("ink drawn at 250,300: ${Integer.toHexString(px)}", Color.red(px) > 150 && Color.green(px) < 120)
        }
    }

    @Test
    fun redactRemovesTextUnderBox() {
        val src = textPdf("r.pdf", 2)
        val out = tmp("red.pdf")
        // "Secret account 12345" sits at baseline y = 792-700 = 92pt from the top.
        PdfTools.redact(context, src, mapOf(0 to listOf(PageRect(60f, 70f, 250f, 30f))), out)
        assertEquals(2, pages(out))
        assertFalse("redacted page has no extractable text", text(out, 0).contains("Secret"))
        assertTrue("other pages untouched", text(out, 1).contains("Secret account"))
    }

    @Test
    fun imagesBecomePages() {
        val a = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val b = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val out = tmp("imgs.pdf")
        PdfTools.imagesToPdf(listOf(a, b), out, a4 = true)
        PDDocument.load(out).use { d ->
            assertEquals(2, d.numberOfPages)
            assertEquals(842f, d.getPage(0).mediaBox.width, 0.5f) // landscape image → landscape A4
            assertEquals(595f, d.getPage(1).mediaBox.width, 0.5f)
        }
    }
}
