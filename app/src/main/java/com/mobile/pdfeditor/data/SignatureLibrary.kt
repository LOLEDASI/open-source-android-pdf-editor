package com.mobile.pdfeditor.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class SignatureKind { Signature, Initials }

data class SavedSignature(val id: String, val kind: SignatureKind, val file: File)

/** Signatures the user chose to keep, stored as transparent PNGs in app-private storage. */
class SignatureLibrary(context: Context) {
    private val dir = File(context.filesDir, "signatures").apply { mkdirs() }
    private val _items = MutableStateFlow(scan())
    val items: StateFlow<List<SavedSignature>> = _items.asStateFlow()

    suspend fun add(bitmap: Bitmap, kind: SignatureKind): SavedSignature = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val file = File(dir, "${kind.name.lowercase()}_${System.currentTimeMillis()}_$id.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        _items.value = scan()
        SavedSignature(id, kind, file)
    }

    suspend fun load(item: SavedSignature): Bitmap? = withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(item.file.path)
    }

    fun delete(item: SavedSignature) {
        item.file.delete()
        _items.value = scan()
    }

    private fun scan(): List<SavedSignature> =
        dir.listFiles { f -> f.extension == "png" }.orEmpty()
            .sortedByDescending { it.name.split('_').getOrNull(1)?.toLongOrNull() ?: 0L }
            .map { f ->
                val parts = f.nameWithoutExtension.split('_')
                val kind = if (parts.firstOrNull() == "initials") SignatureKind.Initials else SignatureKind.Signature
                SavedSignature(parts.lastOrNull() ?: f.name, kind, f)
            }
}
