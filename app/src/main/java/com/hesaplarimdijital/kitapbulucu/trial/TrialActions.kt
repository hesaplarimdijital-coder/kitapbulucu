package com.hesaplarimdijital.kitapbulucu.trial

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.text.format.DateFormat
import java.io.FileNotFoundException
import java.util.Date

/**
 * What the Drive trial measures: picking, reading and writing a document through the Storage
 * Access Framework, listing a folder and keeping the permission. Every step reports in plain
 * words so the log can be pasted into the chat.
 */
class TrialActions(private val context: Context) {

    private val prefs = context.getSharedPreferences("trial", Context.MODE_PRIVATE)
    private val resolver get() = context.contentResolver

    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    var fileUri: Uri?
        get() = prefs.getString(KEY_FILE, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_FILE, value?.toString()).apply()

    var treeUri: Uri?
        get() = prefs.getString(KEY_TREE, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_TREE, value?.toString()).apply()

    private var writeCount: Int
        get() = prefs.getInt(KEY_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_COUNT, value).apply()

    /** Keeps the permission across restarts; reports what the provider allowed. */
    fun keepPermission(uri: Uri): String {
        val both = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            resolver.takePersistableUriPermission(uri, both)
            "Kalıcı izin alındı: okuma ve yazma"
        } catch (e: SecurityException) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                "Kalıcı izin alındı: yalnızca okuma (yazma verilmedi: ${e.message})"
            } catch (inner: SecurityException) {
                "Kalıcı izin alınamadı: ${inner.message}"
            }
        }
    }

    /** The permissions the system still remembers for this app. */
    fun permissions(): List<String> = resolver.persistedUriPermissions.map { permission ->
        val kind = when {
            permission.isReadPermission && permission.isWritePermission -> "okuma+yazma"
            permission.isWritePermission -> "yazma"
            else -> "okuma"
        }
        "$kind · ${describe(permission.uri)}"
    }

    data class FileInfo(val name: String, val size: Long, val lastModified: Long)

    fun info(uri: Uri): FileInfo? {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            return FileInfo(
                name = cursor.getString(0) ?: "?",
                size = if (cursor.isNull(1)) -1 else cursor.getLong(1),
                lastModified = if (cursor.isNull(2)) 0 else cursor.getLong(2),
            )
        }
        return null
    }

    data class ReadResult(val info: FileInfo?, val cells: List<List<String>>, val millis: Long)

    /** Reads the trial workbook and returns its cells; runs on the calling thread. */
    fun read(uri: Uri): ReadResult {
        val start = System.currentTimeMillis()
        val info = info(uri)
        val cells = resolver.openInputStream(uri)?.use(MiniXlsx::readCells)
            ?: throw FileNotFoundException("Dosya açılamadı")
        return ReadResult(info, cells, System.currentTimeMillis() - start)
    }

    data class WriteResult(val count: Int, val mode: String, val millis: Long, val stamp: String)

    /** Rewrites the trial workbook with this device's name, the time and a counter. */
    fun write(uri: Uri): WriteResult {
        val start = System.currentTimeMillis()
        val count = writeCount + 1
        val stamp = time(start)
        val bytes = MiniXlsx.build(
            listOf(
                listOf("Yazan", deviceName),
                listOf("Saat", stamp),
                listOf("Sayaç", count.toString()),
                listOf("Not", "Kitap Bulucu deneme dosyası; silinebilir."),
            ),
        )
        var mode = "wt"
        val stream = try {
            resolver.openOutputStream(uri, mode)
        } catch (e: FileNotFoundException) {
            mode = "w"
            resolver.openOutputStream(uri, mode)
        } ?: throw FileNotFoundException("Dosya yazmak için açılamadı")
        stream.use { it.write(bytes); it.flush() }
        writeCount = count
        return WriteResult(count, mode, System.currentTimeMillis() - start, stamp)
    }

    /** Names in the chosen folder, newest first; also tells whether the list came from Drive. */
    fun listFolder(treeUri: Uri): List<String> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val names = ArrayList<String>()
        resolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: "?"
                val folder = cursor.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR
                val modified = if (cursor.isNull(2)) "" else " · " + time(cursor.getLong(2))
                names.add((if (folder) "[klasör] " else "") + name + modified)
            }
        }
        return names
    }

    fun describe(uri: Uri): String = "${uri.authority ?: "?"} → ${Uri.decode(uri.lastPathSegment ?: uri.toString())}"

    fun time(millis: Long): String = DateFormat.format("dd.MM.yyyy HH:mm:ss", Date(millis)).toString()

    companion object {
        const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val SUGGESTED_NAME = "Kitap Bulucu deneme.xlsx"
        private const val KEY_FILE = "file"
        private const val KEY_TREE = "tree"
        private const val KEY_COUNT = "count"
    }
}
