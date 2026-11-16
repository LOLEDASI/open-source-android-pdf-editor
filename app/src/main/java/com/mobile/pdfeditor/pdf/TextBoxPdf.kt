package com.mobile.pdfeditor.pdf

import com.mobile.pdfeditor.annot.PagePoint
import com.mobile.pdfeditor.annot.PageRect
import com.mobile.pdfeditor.annot.TextBox
import com.mobile.pdfeditor.annot.TextLayout
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

/**
 * Text boxes are stored as FreeText annotations with an appearance stream (so other apps show
 * them) plus a few private keys holding the box's settings (so this app can edit them again).
 */
internal object TextBoxPdf {
    private val KEY = COSName.getPDFName("PdfEditorText")
    private val SIZE = COSName.getPDFName("PdfEditorSize")
    private val COLOR = COSName.getPDFName("PdfEditorColor")
    private val BOLD = COSName.getPDFName("PdfEditorBold")
    private val COVER = COSName.getPDFName("PdfEditorCover")
    private val MIN_H = COSName.getPDFName("PdfEditorMinHeight")

    fun isOurs(a: PDAnnotation) = a.subtype == "FreeText" && a.cosObject.getBoolean(KEY, false)

    fun read(a: PDAnnotation, page: Int, box: PageBox, displayRect: (PDRectangle) -> PageRect, id: Long): TextBox {
        val d = a.cosObject
        val r = displayRect(a.rectangle)
        val minH = d.getFloat(MIN_H, r.h)
        return TextBox(
            id = id,
            page = page,
            rect = r.copy(h = minH),
            text = a.contents.orEmpty(),
            sizePt = d.getFloat(SIZE, 12f),
            color = d.getInt(COLOR, 0x15171A),
            bold = d.getBoolean(BOLD, false),
            cover = d.getInt(COVER, -1).takeIf { it >= 0 },
        )
    }

    /** Fonts are embedded once per save and shared by every text box. */
    class Fonts(private val doc: PDDocument) {
        private var regular: PDFont? = null
        private var bold: PDFont? = null

        fun get(isBold: Boolean): PDFont = if (isBold) {
            bold ?: load(BOLD_FILES, PDType1Font.HELVETICA_BOLD).also { bold = it }
        } else {
            regular ?: load(REGULAR_FILES, PDType1Font.HELVETICA).also { regular = it }
        }

        /** The phone's own sans-serif (matches the on-screen preview); Helvetica if none loads. */
        private fun load(candidates: List<String>, fallback: PDFont): PDFont {
            for (path in candidates) {
                val f = File(path)
                if (!f.exists()) continue
                try {
                    return PDType0Font.load(doc, f)
                } catch (_: Exception) {
                    // Variable or unusual font file: try the next one.
                }
            }
            return fallback
        }

        private companion object {
            val REGULAR_FILES = listOf(
                "/system/fonts/RobotoStatic-Regular.ttf", "/system/fonts/DroidSans.ttf", "/system/fonts/Roboto-Regular.ttf",
            )
            val BOLD_FILES = listOf(
                "/system/fonts/RobotoStatic-Bold.ttf", "/system/fonts/DroidSans-Bold.ttf", "/system/fonts/Roboto-Bold.ttf",
            )
        }
    }

    fun build(doc: PDDocument, t: TextBox, box: PageBox, fonts: Fonts): PDAnnotationMarkup {
        val font = fonts.get(t.bold)
        val pad = TextBox.PAD
        val innerW = (t.rect.w - pad * 2).coerceAtLeast(1f)
        val safeText = printable(font, t.text)
        val lines = TextLayout.wrap(safeText, innerW) { s -> width(font, s, t.sizePt) }
        val h = TextLayout.height(t, lines.size)

        // Corners of the (possibly grown) box in PDF space; the local system has its origin at the
        // displayed bottom-left with x right and y up, so text stays upright on rotated pages.
        val bl = box.toPdf(PagePoint(t.rect.x, t.rect.y + h))
        val br = box.toPdf(PagePoint(t.rect.x + t.rect.w, t.rect.y + h))
        val tl = box.toPdf(PagePoint(t.rect.x, t.rect.y))
        val tr = box.toPdf(PagePoint(t.rect.x + t.rect.w, t.rect.y))
        val xs = listOf(bl.first, br.first, tl.first, tr.first)
        val ys = listOf(bl.second, br.second, tl.second, tr.second)
        val rect = PDRectangle(xs.min(), ys.min(), xs.max() - xs.min(), ys.max() - ys.min())

        val annot = PDAnnotationMarkup()
        val d = annot.cosObject
        d.setName(COSName.SUBTYPE, "FreeText")
        d.setBoolean(KEY, true)
        d.setFloat(SIZE, t.sizePt)
        d.setInt(COLOR, t.color)
        d.setBoolean(BOLD, t.bold)
        d.setFloat(MIN_H, t.rect.h)
        t.cover?.let { d.setInt(COVER, it) }
        annot.rectangle = rect
        annot.contents = t.text
        annot.isPrinted = true
        d.setString(COSName.DA, "/Helv ${t.sizePt} Tf ${rgb(t.color).toList().joinToString(" ")} rg")

        val ap = PDAppearanceStream(doc)
        ap.bBox = rect
        ap.resources = PDResources()
        PDPageContentStream(doc, ap).use { cs ->
            val ux = br.first - bl.first to br.second - bl.second
            val uy = tl.first - bl.first to tl.second - bl.second
            cs.transform(Matrix(ux.first / t.rect.w, ux.second / t.rect.w, uy.first / h, uy.second / h, bl.first, bl.second))
            t.cover?.let { c ->
                val (r, g, b) = rgb(c)
                cs.setNonStrokingColor(r, g, b)
                cs.addRect(0f, 0f, t.rect.w, h)
                cs.fill()
            }
            val (r, g, b) = rgb(t.color)
            cs.setNonStrokingColor(r, g, b)
            cs.beginText()
            cs.setFont(font, t.sizePt)
            val lineH = t.sizePt * TextBox.LINE_HEIGHT
            // First baseline: top padding plus roughly the font's ascent.
            cs.newLineAtOffset(pad, h - pad - t.sizePt * 0.93f)
            lines.forEachIndexed { i, line ->
                if (i > 0) cs.newLineAtOffset(0f, -lineH)
                if (line.isNotEmpty()) cs.showText(line)
            }
            cs.endText()
        }
        annot.appearance = PDAppearanceDictionary().apply { setNormalAppearance(ap) }
        return annot
    }

    private fun width(font: PDFont, s: String, size: Float) = try {
        font.getStringWidth(s) / 1000f * size
    } catch (_: Exception) {
        s.length * size * 0.5f
    }

    /** Drops characters the chosen font can't encode instead of failing the whole save. */
    private fun printable(font: PDFont, s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val ch = String(Character.toChars(cp))
            if (ch == "\n") sb.append(ch) else try {
                font.encode(ch)
                sb.append(ch)
            } catch (_: Exception) {
                sb.append('?')
            }
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun rgb(c: Int) = Triple((c shr 16 and 0xFF) / 255f, (c shr 8 and 0xFF) / 255f, (c and 0xFF) / 255f)
}
