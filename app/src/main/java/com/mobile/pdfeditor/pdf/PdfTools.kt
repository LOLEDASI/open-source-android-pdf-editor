package com.mobile.pdfeditor.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.mobile.pdfeditor.annot.PagePoint
import com.mobile.pdfeditor.annot.PageRect
import com.mobile.pdfeditor.pages.PageEntry
import com.mobile.pdfeditor.pages.PagePlan
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationPopup
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.IdentityHashMap
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class WrongPasswordException : Exception("That password isn't right.")

/** The whole-file tools: merge, split, compress, watermark, password, flatten, redact, images. */
object PdfTools {
    private fun load(file: File, password: String? = null): PDDocument {
        val doc = if (password == null) {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
        } else {
            PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly())
        }
        // An owner-only lock ("no editing") opens without a password; lift it so we can write.
        if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
        return doc
    }

    fun copyToCache(context: Context, uri: Uri, prefix: String = "in-"): File {
        val f = File.createTempFile(prefix, ".pdf", context.cacheDir)
        val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Couldn't read the file")
        input.use { i -> f.outputStream().use { i.copyTo(it) } }
        return f
    }

    fun pageCount(file: File): Int = load(file).use { it.numberOfPages }

    fun needsPassword(file: File): Boolean = try {
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).close()
        false
    } catch (_: InvalidPasswordException) {
        true
    }

    fun merge(inputs: List<File>, out: File) {
        val sources = mutableListOf<PDDocument>()
        try {
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { dest ->
                val merger = PDFMergerUtility()
                for (f in inputs) {
                    val src = load(f)
                    sources += src // must stay open until the merged file is saved
                    merger.appendDocument(dest, src)
                }
                dest.save(out)
            }
        } finally {
            sources.forEach { runCatching { it.close() } }
        }
    }

    /** One output file per page group (0-based page indexes). */
    fun split(input: File, groups: List<List<Int>>, outDir: File): List<File> =
        groups.mapIndexed { i, pages ->
            val out = File(outDir, "split-$i.pdf")
            PageOps.apply(input, PagePlan(pages.mapIndexed { k, p -> PageEntry(k.toLong(), p) }, pages.size.toLong()), out)
            out
        }

    /**
     * Re-encodes large photos as smaller JPEGs. Images with transparency are left alone
     * (JPEG can't keep it). Returns false if nothing got smaller.
     */
    fun compress(input: File, out: File, maxSide: Int = 1600, quality: Float = 0.6f): Boolean {
        var changed = false
        load(input).use { doc ->
            val replaced = IdentityHashMap<COSStream, PDImageXObject>()
            val seenForms = IdentityHashMap<COSStream, Boolean>()
            fun walk(res: PDResources?) {
                res ?: return
                for (name in res.xObjectNames.toList()) {
                    val x = try { res.getXObject(name) } catch (_: Exception) { null } ?: continue
                    when (x) {
                        is PDFormXObject -> if (seenForms.put(x.cosObject, true) == null) walk(x.resources)
                        is PDImageXObject -> {
                            replaced[x.cosObject]?.let { res.put(name, it); continue }
                            val smaller = shrink(doc, x, maxSide, quality) ?: continue
                            replaced[x.cosObject] = smaller
                            res.put(name, smaller)
                            changed = true
                        }
                    }
                }
            }
            doc.pages.forEach { walk(it.resources) }
            doc.save(out)
        }
        return changed && out.length() < input.length()
    }

    private fun shrink(doc: PDDocument, img: PDImageXObject, maxSide: Int, quality: Float): PDImageXObject? {
        if (img.width * img.height < 300 * 300) return null
        if (img.isStencil || img.cosObject.containsKey(COSName.SMASK) || img.cosObject.containsKey(COSName.MASK)) return null
        return try {
            val bmp = img.image ?: return null
            val scale = minOf(1f, maxSide.toFloat() / max(bmp.width, bmp.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt(), (bmp.height * scale).roundToInt(), true)
            } else bmp
            val jpg = JPEGFactory.createFromImage(doc, scaled, quality)
            if (jpg.cosObject.length < img.cosObject.length) jpg else null
        } catch (_: Exception) {
            null
        }
    }

    /** Diagonal text across every page, upright as displayed. */
    fun watermark(input: File, out: File, text: String, opacity: Float = 0.25f, sizePt: Float = 48f) {
        load(input).use { doc ->
            val fonts = TextBoxPdf.Fonts(doc)
            val font = fonts.get(true)
            val shown = text.filter { ch -> runCatching { font.encode(ch.toString()) }.isSuccess }.ifBlank { "?" }
            val width = font.getStringWidth(shown) / 1000f * sizePt
            doc.pages.forEach { page ->
                val box = page.displayBox()
                val angle = Math.toRadians(35.0)
                // Text direction and "up" direction in displayed coordinates (y grows downward).
                val dx = cos(angle).toFloat() to -sin(angle).toFloat()
                val ux = -sin(angle).toFloat() to -cos(angle).toFloat()
                val cx = box.displayWidth / 2f
                val cy = box.displayHeight / 2f
                val start = PagePoint(cx - dx.first * width / 2f - ux.first * sizePt / 3f, cy - dx.second * width / 2f - ux.second * sizePt / 3f)
                val o = box.toPdf(start)
                val px = box.toPdf(PagePoint(start.x + dx.first, start.y + dx.second))
                val py = box.toPdf(PagePoint(start.x + ux.first, start.y + ux.second))
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = opacity })
                    cs.setNonStrokingColor(0.45f, 0.45f, 0.45f)
                    cs.beginText()
                    cs.setFont(font, sizePt)
                    cs.setTextMatrix(Matrix(px.first - o.first, px.second - o.second, py.first - o.first, py.second - o.second, o.first, o.second))
                    cs.showText(shown)
                    cs.endText()
                }
            }
            doc.save(out)
        }
    }

    fun lock(input: File, out: File, password: String) {
        load(input).use { doc ->
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = 256
            doc.protect(policy)
            doc.save(out)
        }
    }

    /** @throws WrongPasswordException */
    fun unlock(input: File, out: File, password: String) {
        val doc = try {
            load(input, password)
        } catch (_: InvalidPasswordException) {
            throw WrongPasswordException()
        }
        doc.use { it.save(out) }
    }

    /**
     * Draws every annotation and form field into the page itself, so they can no longer be
     * moved or edited and show in every viewer. Links keep working; popups are dropped.
     */
    fun flatten(input: File, out: File) {
        load(input).use { doc ->
            doc.documentCatalog.acroForm?.let { form ->
                runCatching { form.refreshAppearances() }
                runCatching { form.flatten() }
            }
            doc.pages.forEach { page ->
                val annots = page.annotations
                if (annots.isEmpty()) return@forEach
                val keep = mutableListOf<com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation>()
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (a in annots) {
                        if (a is PDAnnotationLink) { keep += a; continue }
                        if (a is PDAnnotationPopup || a.isHidden || a.isNoView) continue
                        val ap = a.normalAppearanceStream ?: continue
                        val rect = a.rectangle ?: continue
                        val bbox = ap.bBox ?: continue
                        val m = (ap.cosObject.getDictionaryObject(COSName.MATRIX) as? COSArray)
                            ?.let { arr -> FloatArray(6) { i -> (arr.getObject(i) as? COSNumber)?.floatValue() ?: 0f } }
                            ?: floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
                        // Bounds of the appearance's box after its own matrix.
                        val xs = mutableListOf<Float>()
                        val ys = mutableListOf<Float>()
                        for ((x, y) in listOf(bbox.lowerLeftX to bbox.lowerLeftY, bbox.upperRightX to bbox.lowerLeftY, bbox.lowerLeftX to bbox.upperRightY, bbox.upperRightX to bbox.upperRightY)) {
                            xs += m[0] * x + m[2] * y + m[4]
                            ys += m[1] * x + m[3] * y + m[5]
                        }
                        val tw = (xs.max() - xs.min()).takeIf { it > 0f } ?: continue
                        val th = (ys.max() - ys.min()).takeIf { it > 0f } ?: continue
                        val sx = rect.width / tw
                        val sy = rect.height / th
                        cs.saveGraphicsState()
                        cs.transform(Matrix(sx, 0f, 0f, sy, rect.lowerLeftX - xs.min() * sx, rect.lowerLeftY - ys.min() * sy))
                        cs.drawForm(ap)
                        cs.restoreGraphicsState()
                    }
                }
                page.annotations = keep
            }
            doc.save(out)
        }
    }

    /**
     * Permanently removes everything under [boxes]: each affected page is flattened, drawn to an
     * image with black boxes painted over, and replaced by that image. The hidden text is gone,
     * not just covered (the rest of those pages becomes an image too).
     */
    fun redact(context: Context, input: File, boxes: Map<Int, List<PageRect>>, out: File) {
        val flat = File.createTempFile("flat-", ".pdf", context.cacheDir)
        try {
            flatten(input, flat)
            val renderScale = 200f / 72f // 200 dpi
            ParcelFileDescriptor.open(flat, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    load(flat).use { doc ->
                        val pages = doc.pages.toList()
                        for ((index, rects) in boxes) {
                            if (rects.isEmpty() || index !in pages.indices) continue
                            val (bmp, w, h) = renderer.openPage(index).use { p ->
                                val bmp = Bitmap.createBitmap((p.width * renderScale).roundToInt(), (p.height * renderScale).roundToInt(), Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                Triple(bmp, p.width.toFloat(), p.height.toFloat())
                            }
                            val canvas = Canvas(bmp)
                            val black = Paint().apply { color = Color.BLACK }
                            rects.forEach { r ->
                                canvas.drawRect(r.x * renderScale, r.y * renderScale, (r.x + r.w) * renderScale, (r.y + r.h) * renderScale, black)
                            }
                            val replacement = PDPage(PDRectangle(w, h))
                            val image = JPEGFactory.createFromImage(doc, bmp, 0.92f)
                            PDPageContentStream(doc, replacement).use { it.drawImage(image, 0f, 0f, w, h) }
                            doc.pages.insertBefore(replacement, pages[index])
                            doc.pages.remove(pages[index])
                            bmp.recycle()
                        }
                        doc.documentInformation = com.tom_roush.pdfbox.pdmodel.PDDocumentInformation()
                        doc.save(out)
                    }
                }
            }
        } finally {
            flat.delete()
        }
    }

    /** One page per image: fitted onto A4 with a margin, or a page the shape of the image. */
    fun imagesToPdf(images: List<Bitmap>, out: File, a4: Boolean) {
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            for (original in images) {
                // Photos have no transparency, but decoded bitmaps can still carry an alpha channel,
                // which would add a needless (and uncompressible) mask image. Put them on white.
                val bmp = if (original.hasAlpha()) {
                    Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888).also { flat ->
                        Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(original, 0f, 0f, null) }
                        flat.setHasAlpha(false)
                    }
                } else original
                val landscape = bmp.width > bmp.height
                val (pw, ph) = if (a4) {
                    if (landscape) 842f to 595f else 595f to 842f
                } else {
                    val scale = 842f / max(bmp.width, bmp.height)
                    bmp.width * scale to bmp.height * scale
                }
                val page = PDPage(PDRectangle(pw, ph))
                doc.addPage(page)
                val margin = if (a4) 24f else 0f
                val fit = minOf((pw - margin * 2) / bmp.width, (ph - margin * 2) / bmp.height)
                val w = bmp.width * fit
                val h = bmp.height * fit
                val image = JPEGFactory.createFromImage(doc, bmp, 0.85f)
                PDPageContentStream(doc, page).use { it.drawImage(image, (pw - w) / 2f, (ph - h) / 2f, w, h) }
            }
            doc.save(out)
        }
    }

    private fun PDPage.displayBox(): PageBox {
        val crop = cropBox
        return PageBox(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, PageBox.normalizeRotation(rotation))
    }
}
