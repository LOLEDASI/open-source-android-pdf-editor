package com.mobile.pdfeditor.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.mobile.pdfeditor.pdf.PdfDocumentHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Something another app handed us. */
sealed interface IncomingPdf {
    /** "Open with" on a file. */
    data class View(val uri: Uri) : IncomingPdf
    /** Shared from another app's share menu. */
    data class Shared(val uri: Uri) : IncomingPdf
    /** A link to a PDF (tapped, shared as text, or pasted). */
    data class Link(val url: String) : IncomingPdf
}

class ImportException(message: String) : Exception(message)

/**
 * Turns PDFs from other apps into files the user keeps. Anything that could vanish later
 * (another app's temporary share, a web link) is saved as a real file in
 * Downloads/PDF Editor, so it stays readable and editable after the other app is gone.
 */
class PdfImporter(context: Context) {
    private val context = context.applicationContext

    /** Returns the URI the viewer should open. */
    suspend fun resolve(incoming: IncomingPdf): Uri = when (incoming) {
        is IncomingPdf.View ->
            if (isAlreadyOnDevice(incoming.uri)) incoming.uri
            else copy(incoming.uri)
        is IncomingPdf.Shared -> copy(incoming.uri)
        is IncomingPdf.Link -> download(incoming.url)
    }

    /**
     * Files from the phone's own storage can be opened where they are; files from other
     * apps' private storage only come with a temporary pass, so those get copied.
     */
    fun isAlreadyOnDevice(uri: Uri): Boolean = when {
        uri.scheme == "file" -> true
        uri.authority in LOCAL_PROVIDERS -> true
        uri.authority == "${context.packageName}.files" -> true
        else -> false
    }

    suspend fun copy(source: Uri): Uri = withContext(Dispatchers.IO) {
        val name = ImportNames.pdfName(PdfDocumentHandle.readMeta(context, source).name)
        val input = try {
            context.contentResolver.openInputStream(source)
        } catch (_: SecurityException) {
            null
        } ?: throw ImportException("The other app didn't allow reading this file.")
        input.use { save(it, name) }
    }

    suspend fun download(url: String): Uri = withContext(Dispatchers.IO) {
        var current = URL(url)
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            if (current.protocol != "http" && current.protocol != "https") throw ImportException("That isn't a web link.")
            conn = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false // handled below so http→https redirects work too
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) PdfEditor")
                setRequestProperty("Accept", "application/pdf,*/*;q=0.8")
            }
            val code = try {
                conn.responseCode
            } catch (_: IOException) {
                throw ImportException("Couldn't reach that link. Check your connection.")
            }
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                if (++redirects > 8) throw ImportException("That link redirects too many times.")
                current = URL(current, location)
                continue
            }
            if (code == 401 || code == 403) {
                conn.disconnect()
                throw ImportException("That link needs a sign-in, so it can't be downloaded here.")
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw ImportException("The server returned an error ($code).")
            }
            break
        }

        try {
            val length = conn.contentLengthLong
            if (length > MAX_DOWNLOAD_BYTES) throw ImportException("That PDF is too large (over 200 MB).")
            val name = ImportNames.nameForDownload(current.toString(), conn.getHeaderField("Content-Disposition"))
            BufferedInputStream(conn.inputStream).use { stream ->
                // Check it's really a PDF (links often lead to a login or HTML page instead).
                stream.mark(1024)
                val head = ByteArray(1024)
                val read = stream.readNBytesCompat(head)
                stream.reset()
                if (!ImportNames.looksLikePdf(head, read)) {
                    throw ImportException("That link doesn't lead to a PDF (it may need a sign-in).")
                }
                save(LimitedInputStream(stream, MAX_DOWNLOAD_BYTES), name)
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Writes [input] to Downloads/PDF Editor (or app storage on Android 8–9) and returns its URI. */
    private fun save(input: InputStream, name: String): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw ImportException("Couldn't create a file in Downloads.")
            try {
                resolver.openOutputStream(uri)?.use { input.copyTo(it) } ?: throw IOException("no stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw if (e is ImportException) e else ImportException("Couldn't save the PDF.")
            }
        }
        // Android 8–9: writing to shared Downloads needs a storage permission, so keep it in app storage.
        val dir = File(context.getExternalFilesDir(null), "PDFs").apply { mkdirs() }
        val file = uniqueFile(dir, name)
        try {
            file.outputStream().use { input.copyTo(it) }
        } catch (e: Exception) {
            file.delete()
            throw if (e is ImportException) e else ImportException("Couldn't save the PDF.")
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var n = 1
        val stem = name.removeSuffix(".pdf").removeSuffix(".PDF")
        while (f.exists()) f = File(dir, "$stem (${n++}).pdf")
        return f
    }

    private class LimitedInputStream(private val inner: InputStream, private val max: Long) : InputStream() {
        private var count = 0L
        override fun read(): Int = inner.read().also { if (it >= 0) check(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) check(it) }
        private fun check(n: Int) {
            count += n
            if (count > max) throw ImportException("That PDF is too large (over 200 MB).")
        }
    }

    companion object {
        const val FOLDER = "PDF Editor"
        private const val MAX_DOWNLOAD_BYTES = 200L * 1024 * 1024
        private val LOCAL_PROVIDERS = setOf(
            "com.android.externalstorage.documents",
            "com.android.providers.downloads.documents",
            "com.android.providers.media.documents",
            "media",
            "downloads",
        )
    }
}

private fun InputStream.readNBytesCompat(buf: ByteArray): Int {
    var total = 0
    while (total < buf.size) {
        val n = read(buf, total, buf.size - total)
        if (n < 0) break
        total += n
    }
    return total
}
