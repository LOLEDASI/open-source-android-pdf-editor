package com.mobile.pdfeditor.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DevicePdf(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    /** Folder the file sits in, relative to the phone's storage, e.g. "Download/WhatsApp". */
    val folder: String,
)

/** Finds every PDF on the phone, once the user has allowed access to all files. */
object DevicePdfs {
    fun hasAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    /** Android 11+: the system "All files access" screen for this app. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    suspend fun scan(context: Context): List<DevicePdf> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val files = MediaStore.Files.getContentUri("external")
        @Suppress("DEPRECATION")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA,
        )
        val out = mutableListOf<DevicePdf>()
        val seen = HashSet<String>()
        context.contentResolver.query(
            files,
            projection,
            "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("application/pdf", "%.pdf"),
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
        )?.use { c ->
            val name = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val size = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val date = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            @Suppress("DEPRECATION")
            val data = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            while (c.moveToNext()) {
                val path = c.getString(data) ?: continue
                val f = File(path)
                if (!f.isFile || !seen.add(path)) continue
                // Open by path: with all-files access this can be read and saved in place.
                out += DevicePdf(
                    uri = Uri.fromFile(f).toString(),
                    name = c.getString(name) ?: f.name,
                    sizeBytes = c.getLong(size),
                    modifiedAt = c.getLong(date) * 1000,
                    folder = f.parent?.removePrefix(root)?.trim('/')?.ifEmpty { "Phone storage" } ?: "",
                )
            }
        }
        out
    }
}
