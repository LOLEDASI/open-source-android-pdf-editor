package com.mobile.pdfeditor.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mobile.pdfeditor.annot.Annotations
import com.mobile.pdfeditor.annot.InkStroke
import com.mobile.pdfeditor.annot.InkStyle
import com.mobile.pdfeditor.annot.InkTool
import com.mobile.pdfeditor.annot.PagePoint
import com.mobile.pdfeditor.annot.PageRect
import com.mobile.pdfeditor.annot.SignatureStamp
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationRubberStamp
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class InkDocument(
    val pageBoxes: List<PageBox>,
    val strokes: List<InkStroke>,
    val stamps: List<SignatureStamp> = emptyList(),
    /** Images for [stamps], keyed by [SignatureStamp.imageKey]. */
    val images: Map<String, Bitmap> = emptyMap(),
)

/**
 * Reads and writes freehand ink as standard PDF Ink annotations, and signatures as Stamp
 * annotations, both with appearance streams, so the marks show in other PDF apps and stay
 * editable in this one.
 */
object InkPdfIo {
    /** Marks annotations written by this app so pen vs highlighter survives a round trip. */
    private val TOOL_KEY: COSName = COSName.getPDFName("PdfEditorTool")

    /** Marks signature stamps written by this app (other apps' stamps are left alone). */
    private val SIGNATURE_KEY: COSName = COSName.getPDFName("PdfEditorSignature")

    suspend fun read(context: Context, uri: Uri): InkDocument = withContext(Dispatchers.IO) {
        withLocalCopy(context, uri) { file ->
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val boxes = doc.pages.map { it.pageBox() }
                var id = 1L
                val strokes = mutableListOf<InkStroke>()
                val stamps = mutableListOf<SignatureStamp>()
                val images = mutableMapOf<String, Bitmap>()
                doc.pages.forEachIndexed { index, page ->
                    for (annot in page.annotations) {
                        if (isOurSignature(annot)) {
                            val image = readStampImage(annot) ?: continue
                            val key = "pdf-$id"
                            images[key] = image
                            stamps += SignatureStamp(id++, index, displayRect(annot.rectangle, boxes[index]), key)
                            continue
                        }
                        val ink = annot as? PDAnnotationMarkup ?: continue
                        if (ink.subtype != PDAnnotationMarkup.SUB_TYPE_INK) continue
                        readInk(ink, index, boxes[index]).forEach { strokes += it.copy(id = id++) }
                    }
                }
                InkDocument(boxes, strokes, stamps, images)
            }
        }
    }

    /**
     * Writes [annotations] into a copy of the PDF at [source] and returns that temporary file.
     * Ink and signature annotations already in the file are replaced: they were loaded into
     * [annotations], so it is the complete set. [images] holds each stamp's bitmap.
     */
    suspend fun write(
        context: Context,
        source: Uri,
        annotations: Annotations,
        images: Map<String, Bitmap> = emptyMap(),
    ): File = withContext(Dispatchers.IO) {
        val out = File.createTempFile("save-", ".pdf", context.cacheDir)
        withLocalCopy(context, source) { file ->
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val strokesByPage = annotations.strokes.groupBy { it.page }
                val stampsByPage = annotations.stamps.groupBy { it.page }
                // One image object per signature, shared by every placement of it.
                val xobjects = mutableMapOf<String, PDImageXObject>()
                doc.pages.forEachIndexed { index, page ->
                    val kept = page.annotations.filterNot { a ->
                        isOurSignature(a) ||
                            (a is PDAnnotationMarkup && a.subtype == PDAnnotationMarkup.SUB_TYPE_INK &&
                                !a.inkList.isNullOrEmpty())
                    }.toMutableList<PDAnnotation>()
                    val box = page.pageBox()
                    strokesByPage[index].orEmpty().forEach { kept += buildInk(doc, it, box) }
                    stampsByPage[index].orEmpty().forEach { stamp ->
                        val bitmap = images[stamp.imageKey] ?: return@forEach
                        val image = xobjects.getOrPut(stamp.imageKey) { LosslessFactory.createFromImage(doc, bitmap) }
                        kept += buildStamp(doc, stamp, image, box)
                    }
                    page.annotations = kept
                }
                doc.save(out)
            }
        }
        out
    }

    private fun PDPage.pageBox(): PageBox {
        val crop = cropBox
        return PageBox(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, PageBox.normalizeRotation(rotation))
    }

    private fun readInk(ink: PDAnnotationMarkup, page: Int, box: PageBox): List<InkStroke> {
        val paths = ink.inkList ?: return emptyList()
        val color = try {
            ink.color?.toRGB() ?: 0x000000
        } catch (_: Exception) {
            0x000000
        }
        val width = ink.borderStyle?.width?.takeIf { it > 0f } ?: 1f
        val opacity = ink.constantOpacity.coerceIn(0.05f, 1f)
        val tool = when (ink.cosObject.getNameAsString(TOOL_KEY)) {
            InkTool.Highlighter.name -> InkTool.Highlighter
            InkTool.Pen.name -> InkTool.Pen
            else -> if (opacity < 0.95f && width >= 6f) InkTool.Highlighter else InkTool.Pen
        }
        return paths.mapNotNull { coords ->
            if (coords.size < 2) return@mapNotNull null
            val points = (0 until coords.size / 2).map { i -> box.fromPdf(coords[2 * i], coords[2 * i + 1]) }
            InkStroke(id = 0, page = page, tool = tool, style = InkStyle(color, width, opacity), points = points)
        }
    }

    private fun buildInk(doc: PDDocument, stroke: InkStroke, box: PageBox): PDAnnotationMarkup {
        val pts = stroke.points.map { box.toPdf(it) }
        val pad = stroke.style.widthPt / 2f + 1f
        val minX = pts.minOf { it.first } - pad
        val minY = pts.minOf { it.second } - pad
        val maxX = pts.maxOf { it.first } + pad
        val maxY = pts.maxOf { it.second } + pad
        val rect = PDRectangle(minX, minY, maxX - minX, maxY - minY)

        val rgb = stroke.style.color
        val r = (rgb shr 16 and 0xFF) / 255f
        val g = (rgb shr 8 and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f

        val ink = PDAnnotationMarkup()
        ink.cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)
        ink.cosObject.setName(TOOL_KEY, stroke.tool.name)
        ink.rectangle = rect
        ink.setInkList(arrayOf(FloatArray(pts.size * 2) { i -> if (i % 2 == 0) pts[i / 2].first else pts[i / 2].second }))
        ink.color = PDColor(floatArrayOf(r, g, b), PDDeviceRGB.INSTANCE)
        ink.constantOpacity = stroke.style.opacity
        ink.borderStyle = PDBorderStyleDictionary().apply { width = stroke.style.widthPt }
        ink.isPrinted = true

        val appearance = PDAppearanceStream(doc)
        appearance.bBox = rect
        appearance.resources = PDResources()
        PDPageContentStream(doc, appearance).use { cs ->
            if (stroke.style.opacity < 1f || stroke.tool == InkTool.Highlighter) {
                val gs = PDExtendedGraphicsState()
                gs.strokingAlphaConstant = stroke.style.opacity
                if (stroke.tool == InkTool.Highlighter) gs.blendMode = BlendMode.MULTIPLY
                cs.setGraphicsStateParameters(gs)
            }
            cs.setStrokingColor(r, g, b)
            cs.setLineWidth(stroke.style.widthPt)
            cs.setLineCapStyle(if (stroke.tool == InkTool.Highlighter) 2 else 1) // square vs round caps
            cs.setLineJoinStyle(1)
            cs.moveTo(pts[0].first, pts[0].second)
            if (pts.size == 1) cs.lineTo(pts[0].first + 0.01f, pts[0].second)
            for (i in 1 until pts.size) cs.lineTo(pts[i].first, pts[i].second)
            cs.stroke()
        }
        ink.appearance = PDAppearanceDictionary().apply { setNormalAppearance(appearance) }
        return ink
    }

    private fun isOurSignature(a: PDAnnotation) =
        a.subtype == "Stamp" && a.cosObject.getBoolean(SIGNATURE_KEY, false)

    private fun readStampImage(a: PDAnnotation): Bitmap? = try {
        val resources = a.normalAppearanceStream?.resources
        resources?.xObjectNames
            ?.firstNotNullOfOrNull { resources.getXObject(it) as? PDImageXObject }
            ?.image
    } catch (_: Exception) {
        null
    }

    /** A PDF-space rectangle as an upright rectangle in displayed page coordinates. */
    private fun displayRect(r: PDRectangle, box: PageBox): PageRect {
        val corners = listOf(
            box.fromPdf(r.lowerLeftX, r.lowerLeftY), box.fromPdf(r.upperRightX, r.upperRightY),
            box.fromPdf(r.lowerLeftX, r.upperRightY), box.fromPdf(r.upperRightX, r.lowerLeftY),
        )
        val x = corners.minOf { it.x }
        val y = corners.minOf { it.y }
        return PageRect(x, y, corners.maxOf { it.x } - x, corners.maxOf { it.y } - y)
    }

    private fun buildStamp(doc: PDDocument, stamp: SignatureStamp, image: PDImageXObject, box: PageBox): PDAnnotationRubberStamp {
        val r = stamp.rect
        // Displayed corners in PDF space. Mapping the image's unit square onto them keeps the
        // signature upright as displayed, whatever the page's /Rotate.
        val tl = box.toPdf(PagePoint(r.x, r.y))
        val tr = box.toPdf(PagePoint(r.x + r.w, r.y))
        val bl = box.toPdf(PagePoint(r.x, r.y + r.h))
        val br = box.toPdf(PagePoint(r.x + r.w, r.y + r.h))
        val xs = listOf(tl.first, tr.first, bl.first, br.first)
        val ys = listOf(tl.second, tr.second, bl.second, br.second)
        val rect = PDRectangle(xs.min(), ys.min(), xs.max() - xs.min(), ys.max() - ys.min())

        val annot = PDAnnotationRubberStamp()
        annot.cosObject.setBoolean(SIGNATURE_KEY, true)
        annot.rectangle = rect
        annot.isPrinted = true
        annot.contents = "Signature"

        val appearance = PDAppearanceStream(doc)
        appearance.bBox = rect
        appearance.resources = PDResources()
        PDPageContentStream(doc, appearance).use { cs ->
            cs.drawImage(
                image,
                Matrix(
                    br.first - bl.first, br.second - bl.second, // image x axis -> displayed bottom edge
                    tl.first - bl.first, tl.second - bl.second, // image y axis -> displayed left edge
                    bl.first, bl.second,
                ),
            )
        }
        annot.appearance = PDAppearanceDictionary().apply { setNormalAppearance(appearance) }
        return annot
    }

    /** PdfBox wants a seekable file; content:// streams aren't, so copy to cache first. */
    private inline fun <T> withLocalCopy(context: Context, uri: Uri, block: (File) -> T): T {
        val tmp = File.createTempFile("src-", ".pdf", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't read the file")
            input.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            return block(tmp)
        } finally {
            tmp.delete()
        }
    }
}
