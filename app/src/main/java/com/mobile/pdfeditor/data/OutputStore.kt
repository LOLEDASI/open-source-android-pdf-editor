package com.mobile.pdfeditor.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Where the app puts files it creates: PDFs in Downloads/PDF Editor and images in
 * Pictures/PDF Editor (app storage on Android 8–9, which can't write there without a permission).
 */
class OutputStore(context: Context) {
    private val context = context.applicationContext

    fun savePdf(input: InputStream, name: String): Uri =
        save(input, ImportNames.pdfName(name), "application/pdf", images = false)

    fun savePdf(file: File, name: String): Uri = file.inputStream().use { savePdf(it, name) }

    fun saveImage(bitmap: Bitmap, name: String, png: Boolean): Uri {
        val bytes = java.io.ByteArrayOutputStream()
        bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 92, bytes)
        return save(bytes.toByteArray().inputStream(), name, if (png) "image/png" else "image/jpeg", images = true)
    }

    private fun save(input: InputStream, name: String, mime: String, images: Boolean): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val collection = if (images) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val dir = if (images) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/$FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw ImportException("Couldn't create the file.")
            try {
                resolver.openOutputStream(uri)?.use { input.copyTo(it) } ?: throw IOException("no stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw if (e is ImportException) e else ImportException("Couldn't save the file.")
            }
        }
        val dir = File(context.getExternalFilesDir(null), "PDFs").apply { mkdirs() }
        val file = unique(dir, name)
        try {
            file.outputStream().use { input.copyTo(it) }
        } catch (e: Exception) {
            file.delete()
            throw if (e is ImportException) e else ImportException("Couldn't save the file.")
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        var n = 1
        val ext = name.substringAfterLast('.', "")
        val stem = if (ext.isEmpty()) name else name.dropLast(ext.length + 1)
        while (f.exists()) f = File(dir, if (ext.isEmpty()) "$stem (${n++})" else "$stem (${n++}).$ext")
        return f
    }

    companion object {
        const val FOLDER = "PDF Editor"
    }
}
