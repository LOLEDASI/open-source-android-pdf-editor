package com.mobile.pdfeditor.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class RecentFile(
    val uri: String,
    val name: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val openedAt: Long,
    val starred: Boolean = false,
)

/**
 * Keeps the list of recently opened PDFs in SharedPreferences.
 * Small list (capped at [MAX_ITEMS]), so a JSON blob is simpler than a database.
 */
class RecentFilesRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("recent_files", Context.MODE_PRIVATE)
    private val _files = MutableStateFlow(load())
    val files: StateFlow<List<RecentFile>> = _files.asStateFlow()

    /**
     * Ask Android to keep access to [uri] after a restart: read+write when the source allows
     * (so edits can be saved in place), otherwise read-only. Not every source allows either.
     */
    fun persistAccess(uri: Uri) {
        val resolver = appContext.contentResolver
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(uri, rw)
        } catch (_: SecurityException) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Source didn't grant a persistable permission (e.g. a one-off "Open with" from email).
            }
        }
    }

    fun recordOpened(file: RecentFile) = mutate { list ->
        val previous = list.firstOrNull { it.uri == file.uri }
        listOf(file.copy(starred = previous?.starred ?: file.starred)) +
            list.filterNot { it.uri == file.uri }
    }

    fun toggleStar(uri: String) = mutate { list ->
        list.map { if (it.uri == uri) it.copy(starred = !it.starred) else it }
    }

    fun remove(uri: String) = mutate { list ->
        try {
            val parsed = Uri.parse(uri)
            appContext.contentResolver.persistedUriPermissions
                .firstOrNull { it.uri == parsed }
                ?.let { p ->
                    var flags = 0
                    if (p.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    if (p.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    appContext.contentResolver.releasePersistableUriPermission(parsed, flags)
                }
        } catch (_: SecurityException) {
        }
        list.filterNot { it.uri == uri }
    }

    private fun mutate(transform: (List<RecentFile>) -> List<RecentFile>) {
        _files.update { transform(it).take(MAX_ITEMS) }
        save(_files.value)
    }

    private fun load(): List<RecentFile> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                RecentFile(
                    uri = o.getString("uri"),
                    name = o.getString("name"),
                    pageCount = o.optInt("pages"),
                    sizeBytes = o.optLong("size"),
                    openedAt = o.optLong("opened"),
                    starred = o.optBoolean("starred"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(list: List<RecentFile>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("uri", it.uri)
                    .put("name", it.name)
                    .put("pages", it.pageCount)
                    .put("size", it.sizeBytes)
                    .put("opened", it.openedAt)
                    .put("starred", it.starred),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "files"
        const val MAX_ITEMS = 50
    }
}
