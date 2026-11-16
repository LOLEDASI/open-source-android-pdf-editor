package com.mobile.pdfeditor

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobile.pdfeditor.annot.Annotations
import com.mobile.pdfeditor.annot.InkDefaults
import com.mobile.pdfeditor.annot.InkStroke
import com.mobile.pdfeditor.annot.InkTool
import com.mobile.pdfeditor.annot.PagePoint
import com.mobile.pdfeditor.data.ImportException
import com.mobile.pdfeditor.data.IncomingPdf
import com.mobile.pdfeditor.data.PdfImporter
import com.mobile.pdfeditor.pdf.InkPdfIo
import com.mobile.pdfeditor.pdf.PdfDocumentHandle
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun makePdf(name: String): File {
        val file = File(context.cacheDir, name)
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
        page.canvas.drawText("Report", 72f, 72f, Paint().apply { textSize = 24f })
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    @Test
    fun sharedFileIsSavedAsALocalCopy() = runBlocking<Unit> {
        val source = Uri.fromFile(makePdf("Shared Report.pdf"))
        val importer = PdfImporter(context)
        val saved = importer.resolve(IncomingPdf.Shared(source))
        try {
            assertNotEquals(source, saved)
            assertEquals("Shared Report.pdf", PdfDocumentHandle.readMeta(context, saved).name)
            // The copy opens and can be written back (so annotations can be saved into it).
            PdfDocumentHandle.open(context, saved).use { assertEquals(1, it.pageCount) }
            context.contentResolver.openOutputStream(saved, "wa")!!.close()
        } finally {
            context.contentResolver.delete(saved, null, null)
        }
    }

    @Test
    fun localFilesAreOpenedInPlace() = runBlocking<Unit> {
        val source = Uri.fromFile(makePdf("local.pdf"))
        assertEquals(source, PdfImporter(context).resolve(IncomingPdf.View(source)))
    }

    @Test
    fun nonPdfLinkIsRejectedClearly() = runBlocking<Unit> {
        try {
            PdfImporter(context).download("not a url")
            fail("expected an error")
        } catch (_: Exception) {
            // MalformedURLException or ImportException; either way nothing is saved.
        }
        try {
            PdfImporter(context).download("ftp://example.com/a.pdf")
            fail("expected an error")
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("web link"))
        }
    }

    @Test
    fun ownerLockedPdfCanBeAnnotatedAndSaved() = runBlocking<Unit> {
        // A "no editing" PDF: opens without a password but has an owner-password lock.
        val plain = makePdf("locked-src.pdf")
        val locked = File(context.cacheDir, "locked.pdf")
        PDDocument.load(plain).use { doc ->
            val perms = AccessPermission().apply { setCanModify(false); setCanModifyAnnotations(false) }
            doc.protect(StandardProtectionPolicy("owner-secret", "", perms).apply { encryptionKeyLength = 128 })
            doc.save(locked)
        }
        val uri = Uri.fromFile(locked)
        val stroke = InkStroke(1, 0, InkTool.Pen, InkDefaults.Pen, listOf(PagePoint(100f, 100f), PagePoint(200f, 150f)))
        val out = InkPdfIo.write(context, uri, Annotations(strokes = listOf(stroke)))
        PDDocument.load(out).use { doc ->
            assertFalse("lock removed in the saved copy", doc.isEncrypted)
        }
        val reread = InkPdfIo.read(context, Uri.fromFile(out))
        assertEquals(1, reread.strokes.size)
        out.delete()
    }
}
