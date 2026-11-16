package com.mobile.pdfeditor.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.mutableStateMapOf
import com.mobile.pdfeditor.annot.AnnotationStore
import com.mobile.pdfeditor.annot.SignatureStamp
import com.mobile.pdfeditor.annot.StampGeometry
import com.mobile.pdfeditor.annot.PagePoint
import com.mobile.pdfeditor.annot.TextLine
import com.mobile.pdfeditor.pdf.TextLines
import kotlinx.coroutines.sync.withLock
import com.mobile.pdfeditor.data.RecentFile
import com.mobile.pdfeditor.data.RecentFilesRepository
import com.mobile.pdfeditor.pages.PagePlan
import com.mobile.pdfeditor.pdf.InkPdfIo
import com.mobile.pdfeditor.pdf.PageOps
import com.mobile.pdfeditor.pdf.PdfDocumentHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ViewerUiState {
    data object Loading : ViewerUiState
    data class Error(val message: String) : ViewerUiState
    data class Ready(val name: String, val pageSizes: List<Size>) : ViewerUiState
}

sealed interface InkStatus {
    data object Loading : InkStatus
    data object Ready : InkStatus
    data class Unavailable(val reason: String) : InkStatus
}

sealed interface SaveResult {
    data object Saved : SaveResult
    /** The original can't be overwritten (read-only source); ask the user where to save a copy. */
    data object NeedsCopy : SaveResult
    data class Failed(val message: String) : SaveResult
}

/**
 * Owns one open PDF while the viewer is on screen. Created with `remember` and
 * released with [close] when the viewer leaves composition (the activity handles
 * rotation itself, so this survives configuration changes).
 */
class ViewerController(
    context: Context,
    private val uri: Uri,
    private val recents: RecentFilesRepository,
    scope: CoroutineScope,
) {
    private val context = context.applicationContext

    private val _state = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    private var document: PdfDocumentHandle? = null
    private var displayName: String = "Document.pdf"

    val annotations = AnnotationStore()

    private val _formFields = MutableStateFlow<List<com.mobile.pdfeditor.annot.FormField>>(emptyList())
    /** The PDF's fillable fields (empty if it has no form). */
    val formFields: StateFlow<List<com.mobile.pdfeditor.annot.FormField>> = _formFields.asStateFlow()

    /** Signature images by key. Never pruned during a session so undo can bring stamps back. */
    val stampImages = mutableStateMapOf<String, Bitmap>()

    /** Adds [bitmap] as a signature on [page], centred, and returns the new stamp's id. */
    fun placeSignature(bitmap: Bitmap, page: Int, pageW: Float, pageH: Float, widthPt: Float, centerY: Float): Long {
        val key = "sig-${System.nanoTime()}"
        stampImages[key] = bitmap
        val id = annotations.newId()
        val rect = StampGeometry.place(pageW, pageH, bitmap.width, bitmap.height, widthPt, centerY)
        annotations.addStamp(SignatureStamp(id, page, rect, key))
        return id
    }

    private val _inkStatus = MutableStateFlow<InkStatus>(InkStatus.Loading)
    val inkStatus: StateFlow<InkStatus> = _inkStatus.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    // About 1/6 of the app's heap for rendered pages.
    private val pageCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    init {
        scope.launch { open() }
    }

    private suspend fun open() {
        val meta = withContext(Dispatchers.IO) { PdfDocumentHandle.readMeta(context, uri) }
        displayName = meta.name
        try {
            val doc = PdfDocumentHandle.open(context, uri)
            document = doc
            recents.recordOpened(
                RecentFile(
                    uri = uri.toString(),
                    name = meta.name,
                    pageCount = doc.pageCount,
                    sizeBytes = meta.sizeBytes,
                    openedAt = System.currentTimeMillis(),
                ),
            )
            _state.value = ViewerUiState.Ready(meta.name, doc.pageSizes)
            loadInk(doc.pageCount)
        } catch (_: SecurityException) {
            _state.value = ViewerUiState.Error(
                "This PDF is password-protected or the app no longer has permission to open it.",
            )
        } catch (_: java.io.FileNotFoundException) {
            _state.value = ViewerUiState.Error("This file was moved or deleted.")
        } catch (_: Exception) {
            _state.value = ViewerUiState.Error("This file couldn't be opened. It may be damaged or not a PDF.")
        }
    }

    /** Reads existing ink annotations so they can be edited alongside new ones. */
    private suspend fun loadInk(pageCount: Int) {
        _inkStatus.value = try {
            val ink = InkPdfIo.read(context, uri)
            if (ink.pageBoxes.size != pageCount) {
                InkStatus.Unavailable("This PDF's page structure couldn't be read, so it can't be annotated.")
            } else {
                stampImages.putAll(ink.images)
                annotations.load(ink.strokes, ink.stamps, ink.texts, ink.form.values)
                _formFields.value = ink.form.fields
                InkStatus.Ready
            }
        } catch (_: Exception) {
            InkStatus.Unavailable("This PDF can be viewed but not annotated (it may be encrypted or damaged).")
        }
    }

    /** Writes the annotations (and page changes, if [pages] is given) back into the original file. */
    suspend fun save(pages: PagePlan? = null): SaveResult = writeTo(uri, overwriteOriginal = true, pages = pages)

    /** Writes an annotated copy to [target] (picked by the user) and adds it to recents. */
    suspend fun saveCopy(target: Uri, pages: PagePlan? = null): SaveResult =
        writeTo(target, overwriteOriginal = false, pages = pages)

    /** Writes just the pages in [pages] (with their annotations) to a new file at [target]. */
    suspend fun extract(target: Uri, pages: PagePlan): SaveResult =
        writeTo(target, overwriteOriginal = false, pages = pages, markClean = false)

    private suspend fun writeTo(
        target: Uri,
        overwriteOriginal: Boolean,
        pages: PagePlan? = null,
        markClean: Boolean = true,
    ): SaveResult {
        if (_saving.value) return SaveResult.Failed("Already saving")
        _saving.value = true
        val snapshot = annotations.current
        try {
            val temp = try {
                val annotated = InkPdfIo.write(context, uri, snapshot, stampImages.toMap())
                if (pages == null) {
                    annotated
                } else {
                    withContext(Dispatchers.IO) {
                        val reordered = java.io.File.createTempFile("pages-", ".pdf", context.cacheDir)
                        try {
                            PageOps.apply(annotated, pages, reordered)
                        } finally {
                            annotated.delete()
                        }
                        reordered
                    }
                }
            } catch (_: Exception) {
                return SaveResult.Failed("Couldn't write the changes into this PDF.")
            }
            try {
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(target, "wt")
                        ?: throw java.io.IOException("No output stream")
                    out.use { o -> temp.inputStream().use { it.copyTo(o) } }
                }
            } catch (_: Exception) {
                return if (overwriteOriginal) SaveResult.NeedsCopy
                else SaveResult.Failed("Couldn't save to that location.")
            } finally {
                temp.delete()
            }

            if (overwriteOriginal && pages != null) {
                // Pages moved: reload everything (sizes, page count, annotations by page).
                reload()
            } else if (overwriteOriginal) {
                // The renderer reads the file lazily, and the file just changed under it.
                reopenRenderer()
            }
            if (overwriteOriginal) {
                recents.recordOpened(
                    RecentFile(
                        uri = uri.toString(),
                        name = displayName,
                        pageCount = document?.pageCount ?: 0,
                        sizeBytes = withContext(Dispatchers.IO) { PdfDocumentHandle.readMeta(context, uri) }.sizeBytes,
                        openedAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                recents.persistAccess(target)
                val meta = withContext(Dispatchers.IO) { PdfDocumentHandle.readMeta(context, target) }
                recents.recordOpened(
                    RecentFile(
                        uri = target.toString(),
                        name = meta.name,
                        pageCount = document?.pageCount ?: 0,
                        sizeBytes = meta.sizeBytes,
                        openedAt = System.currentTimeMillis(),
                    ),
                )
            }
            // Only clear "unsaved" if nothing was drawn while the save ran.
            if (markClean && annotations.current == snapshot) annotations.markSaved()
            return SaveResult.Saved
        } finally {
            _saving.value = false
        }
    }

    // Existing text lines, read on demand for "Edit text".
    private var textDoc: com.tom_roush.pdfbox.pdmodel.PDDocument? = null
    private var textDocFile: java.io.File? = null
    private val textLines = HashMap<Int, List<TextLine>>()
    private val textMutex = kotlinx.coroutines.sync.Mutex()

    /** The line of existing PDF text at [p] on [page], if any. */
    suspend fun textLineAt(page: Int, p: PagePoint): TextLine? = textMutex.withLock {
        withContext(Dispatchers.IO) {
            val lines = textLines.getOrPut(page) {
                try {
                    val doc = textDoc ?: run {
                        val f = java.io.File.createTempFile("text-", ".pdf", context.cacheDir)
                        context.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
                        textDocFile = f
                        com.tom_roush.pdfbox.pdmodel.PDDocument.load(f).also { textDoc = it }
                    }
                    TextLines.extract(doc, page)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            // Prefer the line whose box contains the point; allow a little slack for fingers.
            lines.firstOrNull { p in it.rect } ?: lines
                .filter { p.y in (it.rect.y - 4f)..(it.rect.y + it.rect.h + 4f) && p.x in (it.rect.x - 6f)..(it.rect.x + it.rect.w + 6f) }
                .minByOrNull { kotlin.math.abs(p.y - (it.rect.y + it.rect.h / 2)) }
        }
    }

    private fun dropTextIndex() {
        textLines.clear()
        try { textDoc?.close() } catch (_: Exception) {}
        textDoc = null
        textDocFile?.delete()
        textDocFile = null
    }

    /** Re-reads the whole file, e.g. after its pages were reorganised. */
    private suspend fun reload() {
        val old = document
        val doc = try {
            PdfDocumentHandle.open(context, uri)
        } catch (_: Exception) {
            null
        }
        document = doc
        old?.close()
        pageCache.evictAll()
        dropTextIndex()
        if (doc != null) {
            _state.value = ViewerUiState.Ready(displayName, doc.pageSizes)
            loadInk(doc.pageCount)
        }
    }

    private suspend fun reopenRenderer() {
        val old = document
        document = try {
            PdfDocumentHandle.open(context, uri)
        } catch (_: Exception) {
            null
        }
        old?.close()
    }

    /** Returns a cached bitmap immediately if there is one. */
    fun cached(index: Int, widthPx: Int): Bitmap? = pageCache.get(key(index, widthPx))

    suspend fun render(index: Int, widthPx: Int): Bitmap? {
        pageCache.get(key(index, widthPx))?.let { return it }
        val doc = document ?: return null
        return try {
            doc.render(index, widthPx)?.also { pageCache.put(key(index, widthPx), it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun key(index: Int, widthPx: Int) = "$index@$widthPx"

    fun close() {
        dropTextIndex()
        document?.close()
        pageCache.evictAll()
    }
}
