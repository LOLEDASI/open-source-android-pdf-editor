package com.mobile.pdfeditor.pages

/**
 * One page in the edited document.
 * [source] is the original page index, or -1 for a newly inserted blank page.
 * [rotation] is extra clockwise rotation added by the user (0, 90, 180, 270).
 * [blankWidth]/[blankHeight] size a blank page in points.
 */
data class PageEntry(
    val key: Long,
    val source: Int,
    val rotation: Int = 0,
    val blankWidth: Float = 0f,
    val blankHeight: Float = 0f,
) {
    val isBlank: Boolean get() = source < 0
}

/**
 * The page list being edited in the organizer: an immutable value, so each operation returns a
 * new plan and undo is just keeping old ones. Pure logic with no Android or PDF dependencies.
 */
data class PagePlan(val pages: List<PageEntry>, private val nextKey: Long) {
    val size: Int get() = pages.size

    /** True if nothing differs from the original document. */
    val isUnchanged: Boolean
        get() = pages.withIndex().all { (i, p) -> p.source == i && p.rotation == 0 } && nextKeyIsOriginal

    private val nextKeyIsOriginal: Boolean get() = pages.size == nextKey.toInt() && pages.none { it.isBlank }

    fun rotate(keys: Set<Long>, degrees: Int = 90) = copy(
        pages = pages.map { if (it.key in keys) it.copy(rotation = ((it.rotation + degrees) % 360 + 360) % 360) else it },
    )

    /** Removes pages, but never the last remaining one. */
    fun delete(keys: Set<Long>): PagePlan {
        val left = pages.filterNot { it.key in keys }
        return if (left.isEmpty()) this else copy(pages = left)
    }

    /** Puts a copy right after each selected page. */
    fun duplicate(keys: Set<Long>): PagePlan {
        var k = nextKey
        val out = mutableListOf<PageEntry>()
        for (p in pages) {
            out += p
            if (p.key in keys) out += p.copy(key = k++)
        }
        return PagePlan(out, k)
    }

    /** Inserts a blank page after [afterKey] (or at the end), sized like its neighbour. */
    fun insertBlank(afterKey: Long?, width: Float, height: Float): Pair<PagePlan, Long> {
        val index = afterKey?.let { k -> pages.indexOfFirst { it.key == k } }?.takeIf { it >= 0 }?.plus(1) ?: pages.size
        val entry = PageEntry(nextKey, -1, 0, width, height)
        return PagePlan(pages.toMutableList().apply { add(index, entry) }, nextKey + 1) to entry.key
    }

    fun move(from: Int, to: Int): PagePlan {
        if (from !in pages.indices || to !in pages.indices || from == to) return this
        return copy(pages = pages.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Just the selected pages, in document order (for "Extract"). */
    fun subset(keys: Set<Long>) = copy(pages = pages.filter { it.key in keys })

    companion object {
        fun original(pageCount: Int) = PagePlan(List(pageCount) { PageEntry(it.toLong(), it) }, pageCount.toLong())
    }
}
