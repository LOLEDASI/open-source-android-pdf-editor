package com.mobile.pdfeditor.annot

/**
 * Text placed on a page. [rect] is the box's position and its minimum size in page points;
 * the box grows downwards if the text needs more lines. [cover] (0xRRGGBB) paints the box
 * background, which is how "Edit text" hides the original words underneath.
 */
data class TextBox(
    val id: Long,
    val page: Int,
    val rect: PageRect,
    val text: String,
    val sizePt: Float = 12f,
    val color: Int = 0x15171A,
    val bold: Boolean = false,
    val cover: Int? = null,
) {
    companion object {
        /** Inner padding in points. */
        const val PAD = 2f
        const val LINE_HEIGHT = 1.2f
        const val MIN_SIZE = 4f
        const val MAX_SIZE = 72f
    }
}

/** A line of existing text found in the PDF, used to start an edit. */
data class TextLine(val text: String, val rect: PageRect, val sizePt: Float, val bold: Boolean)

object TextLayout {
    /**
     * Breaks [text] into lines no wider than [maxWidth], using [measure] for widths.
     * Keeps explicit newlines, wraps at spaces, and splits words that are too long on their own.
     */
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val out = mutableListOf<String>()
        for (para in text.split('\n')) {
            if (para.isEmpty()) { out += ""; continue }
            var line = ""
            for (word in para.split(' ')) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (measure(candidate) <= maxWidth) {
                    line = candidate
                    continue
                }
                if (line.isNotEmpty()) out += line
                // The word alone may still be too wide: break it by characters.
                var rest = word
                while (rest.isNotEmpty() && measure(rest) > maxWidth) {
                    var cut = rest.length - 1
                    while (cut > 1 && measure(rest.substring(0, cut)) > maxWidth) cut--
                    out += rest.substring(0, cut)
                    rest = rest.substring(cut)
                }
                line = rest
            }
            out += line
        }
        return out
    }

    /** Height the box needs for [lines] at [sizePt], never less than its stored minimum. */
    fun height(box: TextBox, lines: Int): Float =
        maxOf(box.rect.h, lines * box.sizePt * TextBox.LINE_HEIGHT + TextBox.PAD * 2)
}
