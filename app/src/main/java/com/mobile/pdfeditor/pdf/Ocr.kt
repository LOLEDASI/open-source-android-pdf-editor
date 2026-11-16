package com.mobile.pdfeditor.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mobile.pdfeditor.annot.PagePoint
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.roundToInt

/** A recognised line of text in displayed page points. */
data class OcrLine(val text: String, val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Makes scanned PDFs searchable: each page without real text is rendered, read with ML Kit's
 * on-device recogniser, and given an invisible text layer lined up with the words in the image.
 */
object Ocr {
    /** Returns how many pages got a text layer (0 means the PDF already had text everywhere). */
    suspend fun makeSearchable(input: File, out: File, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var processed = 0
        try {
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                        if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
                        val font = PDType1Font.HELVETICA
                        val total = doc.numberOfPages
                        for (i in 0 until total) {
                            onProgress(i, total)
                            // Pages that already carry text (not just an image) are left as they are.
                            if (TextLines.extract(doc, i).size >= 3) continue
                            val (bitmap, pageW) = renderer.openPage(i).use { p ->
                                val scale = (2200f / p.width).coerceIn(1.5f, 4f)
                                val bmp = Bitmap.createBitmap((p.width * scale).roundToInt(), (p.height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                bmp to p.width.toFloat()
                            }
                            val k = bitmap.width / pageW
                            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                            bitmap.recycle()
                            val lines = result.textBlocks.flatMap { b -> b.lines }.mapNotNull { l ->
                                val r = l.boundingBox ?: return@mapNotNull null
                                OcrLine(l.text, r.left / k, r.top / k, r.width() / k, r.height() / k)
                            }
                            if (lines.isEmpty()) continue
                            writeInvisibleText(doc, i, lines, font)
                            processed++
                        }
                        onProgress(total, total)
                        doc.save(out)
                    }
                }
            }
        } finally {
            recognizer.close()
        }
        return processed
    }

    private fun writeInvisibleText(doc: PDDocument, index: Int, lines: List<OcrLine>, font: PDType1Font) {
        val page = doc.getPage(index)
        val crop = page.cropBox
        val box = PageBox(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, PageBox.normalizeRotation(page.rotation))
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.beginText()
            cs.setRenderingMode(RenderingMode.NEITHER) // invisible, but selectable and searchable
            for (line in lines) {
                val text = line.text.map { ch -> if (encodable(font, ch)) ch else '?' }.joinToString("").trim()
                if (text.isEmpty() || line.w <= 0f || line.h <= 0f) continue
                val size = line.h * 0.85f
                val natural = font.getStringWidth(text) / 1000f * size
                if (natural <= 0f) continue
                val stretch = line.w / natural
                // Baseline sits about 80% down the line box; map displayed axes to PDF space.
                val base = PagePoint(line.x, line.y + line.h * 0.8f)
                val o = box.toPdf(base)
                val px = box.toPdf(PagePoint(base.x + 1f, base.y))
                val py = box.toPdf(PagePoint(base.x, base.y - 1f))
                cs.setFont(font, size)
                cs.setTextMatrix(
                    Matrix(
                        (px.first - o.first) * stretch, (px.second - o.second) * stretch,
                        py.first - o.first, py.second - o.second,
                        o.first, o.second,
                    ),
                )
                cs.showText(text)
            }
            cs.endText()
        }
    }

    private fun encodable(font: PDType1Font, ch: Char) = try {
        font.encode(ch.toString()); true
    } catch (_: Exception) {
        false
    }
}
