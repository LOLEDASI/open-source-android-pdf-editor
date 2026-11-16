package com.mobile.pdfeditor.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.roundToInt

data class FileMeta(val name: String, val sizeBytes: Long)

/**
 * Wraps Android's [PdfRenderer]. The renderer only allows one open page at a time,
 * so every call goes through [mutex] on the IO dispatcher.
 */
class PdfDocumentHandle private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** Page sizes in PDF points (1/72 inch). */
    val pageSizes: List<Size>,
) : Closeable {
    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int get() = pageSizes.size

    /** Renders page [index] to a bitmap [widthPx] wide, keeping the page's aspect ratio. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = mutex.withLock {
        if (closed) return@withLock null
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val heightPx = (widthPx.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE) // PDF pages render onto transparent by default
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    override fun close() {
        // Wait for any in-flight render before releasing native resources.
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    renderer.close()
                    fd.close()
                }
            }
        }
    }

    companion object {
        /** @throws SecurityException for password-protected files, IOException for broken ones. */
        suspend fun open(context: Context, uri: Uri): PdfDocumentHandle = withContext(Dispatchers.IO) {
            val fd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Couldn't open this file")
            try {
                val renderer = PdfRenderer(fd)
                val sizes = (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { Size(it.width, it.height) }
                }
                PdfDocumentHandle(fd, renderer, sizes)
            } catch (e: Exception) {
                fd.close()
                throw e
            }
        }

        fun readMeta(context: Context, uri: Uri): FileMeta {
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Document.pdf"
            var size = 0L
            try {
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }
                            ?.let { name = c.getString(it) }
                        c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }
                            ?.let { size = c.getLong(it) }
                    }
                }
            } catch (_: Exception) {
            }
            return FileMeta(name, size)
        }
    }
}

/** First-page thumbnails for the file list, cached in memory. */
object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024) { // KB
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val mutex = Mutex()

    suspend fun get(context: Context, uri: String, widthPx: Int): Bitmap? {
        cache.get(uri)?.let { return it }
        return mutex.withLock {
            cache.get(uri) ?: try {
                val doc = PdfDocumentHandle.open(context, Uri.parse(uri))
                try {
                    doc.render(0, widthPx)
                } finally {
                    doc.close()
                }
            } catch (_: Exception) {
                null
            }?.also { cache.put(uri, it) }
        }
    }
}
