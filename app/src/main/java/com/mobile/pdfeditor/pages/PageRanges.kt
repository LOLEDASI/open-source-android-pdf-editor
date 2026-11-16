package com.mobile.pdfeditor.pages

/** Parses page ranges the way people type them. Pure logic, unit-tested. */
object PageRanges {
    /**
     * "1-3, 5, 8-" on a 10-page document → [[0,1,2],[4],[7,8,9]] (0-based groups).
     * Returns null with a message when the text is invalid.
     */
    fun parse(text: String, pageCount: Int): Result<List<List<Int>>> {
        val parts = text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Result.failure(IllegalArgumentException("Enter page numbers, like 1-3, 5"))
        val groups = mutableListOf<List<Int>>()
        for (part in parts) {
            val m = Regex("""^(\d*)\s*[-–]\s*(\d*)$""").find(part)
            val (from, to) = when {
                m != null -> {
                    val a = m.groupValues[1].ifEmpty { "1" }.toInt()
                    val b = m.groupValues[2].ifEmpty { pageCount.toString() }.toInt()
                    a to b
                }
                part.all { it.isDigit() } -> part.toInt().let { it to it }
                else -> return Result.failure(IllegalArgumentException("\"$part\" isn't a page or range"))
            }
            if (from < 1 || to > pageCount || from > to) {
                return Result.failure(IllegalArgumentException("Pages must be between 1 and $pageCount"))
            }
            groups += (from - 1 until to).toList()
        }
        return Result.success(groups)
    }

    /** Every [n] pages: 7 pages, n=3 → [[0,1,2],[3,4,5],[6]]. */
    fun chunks(pageCount: Int, n: Int): List<List<Int>> = (0 until pageCount).chunked(n.coerceAtLeast(1))
}
