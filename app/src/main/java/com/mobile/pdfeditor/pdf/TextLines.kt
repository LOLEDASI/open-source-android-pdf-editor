package com.mobile.pdfeditor.pdf

import com.mobile.pdfeditor.annot.PageRect
import com.mobile.pdfeditor.annot.TextLine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.StringWriter

/** Finds the lines of real text on a page, with their position, size and weight. */
object TextLines {
    fun extract(doc: PDDocument, pageIndex: Int): List<TextLine> {
        val collector = Collector()
        collector.sortByPosition = true
        collector.startPage = pageIndex + 1
        collector.endPage = pageIndex + 1
        collector.writeText(doc, StringWriter())
        collector.flush()
        return collector.lines
    }

    /**
     * PDFTextStripper reports text word by word with separators in between, so collect words
     * until it signals the end of a line.
     */
    private class Collector : PDFTextStripper() {
        val lines = mutableListOf<TextLine>()
        private val text = StringBuilder()
        private val positions = mutableListOf<TextPosition>()

        override fun writeString(string: String, textPositions: MutableList<TextPosition>) {
            text.append(string)
            positions += textPositions
        }

        override fun writeWordSeparator() {
            text.append(' ')
        }

        override fun writeLineSeparator() = flush()

        fun flush() {
            val s = text.toString().trim()
            if (s.isNotEmpty() && positions.isNotEmpty()) {
                // Some PDFs set size through the text matrix and report 1pt; fall back to glyph height.
                val sizes = positions.map { p -> if (p.fontSizeInPt >= 2f) p.fontSizeInPt else p.heightDir / 0.7f }.sorted()
                val size = sizes[sizes.size / 2].coerceIn(4f, 96f)
                val x0 = positions.minOf { it.xDirAdj }
                val x1 = positions.maxOf { it.xDirAdj + it.widthDirAdj }
                val baseline = positions.maxOf { it.yDirAdj }
                val bold = positions.count { it.font?.name?.contains("bold", ignoreCase = true) == true } > positions.size / 2
                lines += TextLine(
                    text = s,
                    rect = PageRect(x0, baseline - size * 0.9f, (x1 - x0).coerceAtLeast(1f), size * 1.2f),
                    sizePt = size,
                    bold = bold,
                )
            }
            text.clear()
            positions.clear()
        }
    }
}
