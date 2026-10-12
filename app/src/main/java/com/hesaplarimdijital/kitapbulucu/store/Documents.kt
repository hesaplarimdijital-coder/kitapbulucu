package com.hesaplarimdijital.kitapbulucu.store

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException

/** Why a document could not be read; the UI turns it into a sentence and a way out. */
enum class DocumentFailure { NOT_FOUND, NO_PERMISSION, IO, TOO_LARGE }

class DocumentException(val failure: DocumentFailure, message: String, cause: Throwable? = null) : Exception(message, cause)

/** What the provider knows about a document; any field may be missing. */
class DocumentInfo(val displayName: String?, val lastModified: Long?, val size: Long?)

/** The few things the app needs from the Storage Access Framework, so tests can fake them. */
interface Documents {
    fun info(uri: Uri): DocumentInfo?

    /** The whole document; throws [DocumentException] when it is gone, locked or too big. */
    fun readBytes(uri: Uri, maxBytes: Int = DEFAULT_MAX_BYTES): ByteArray

    /** Display names of the documents in a tree the user granted; empty when the tree cannot be listed. */
    fun childNames(treeUri: Uri): List<String>

    /** Replaces the whole document; throws [DocumentException]. Safe-write rules are the caller's (ARCHITECTURE 7). */
    fun writeBytes(uri: Uri, bytes: ByteArray)

    /** Keeps access to [uri] across restarts: read and write when the provider allows, else read only. */
    fun keepPermission(uri: Uri): Boolean

    companion object {
        const val DEFAULT_MAX_BYTES = 128 * 1024 * 1024
    }
}

/** Real documents through the content resolver. */
class SafDocuments(private val resolver: ContentResolver) : Documents {

    override fun info(uri: Uri): DocumentInfo? = try {
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE)
        resolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            DocumentInfo(
                displayName = cursor.getColumnIndex(columns[0]).takeIf { it >= 0 }?.let { if (cursor.isNull(it)) null else cursor.getString(it) },
                lastModified = cursor.getColumnIndex(columns[1]).takeIf { it >= 0 }?.let { if (cursor.isNull(it)) null else cursor.getLong(it) }?.takeIf { it > 0 },
                size = cursor.getColumnIndex(columns[2]).takeIf { it >= 0 }?.let { if (cursor.isNull(it)) null else cursor.getLong(it) },
            )
        }
    } catch (e: Exception) {
        null
    }

    override fun readBytes(uri: Uri, maxBytes: Int): ByteArray {
        val stream = try {
            resolver.openInputStream(uri) ?: throw DocumentException(DocumentFailure.NOT_FOUND, "No stream for $uri")
        } catch (e: FileNotFoundException) {
            throw DocumentException(DocumentFailure.NOT_FOUND, "Document is gone", e)
        } catch (e: SecurityException) {
            throw DocumentException(DocumentFailure.NO_PERMISSION, "Permission to the document was lost", e)
        }
        try {
            stream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) throw DocumentException(DocumentFailure.TOO_LARGE, "Document is larger than $maxBytes bytes")
                }
                return out.toByteArray()
            }
        } catch (e: DocumentException) {
            throw e
        } catch (e: SecurityException) {
            throw DocumentException(DocumentFailure.NO_PERMISSION, "Permission to the document was lost", e)
        } catch (e: IOException) {
            throw DocumentException(DocumentFailure.IO, "Document could not be read", e)
        }
    }

    override fun writeBytes(uri: Uri, bytes: ByteArray) {
        try {
            val stream = try {
                resolver.openOutputStream(uri, "wt")
            } catch (e: FileNotFoundException) {
                resolver.openOutputStream(uri, "w") // some providers know no "t"
            } ?: throw DocumentException(DocumentFailure.NOT_FOUND, "No output stream for $uri")
            stream.use {
                it.write(bytes)
                it.flush()
            }
        } catch (e: DocumentException) {
            throw e
        } catch (e: FileNotFoundException) {
            throw DocumentException(DocumentFailure.NOT_FOUND, "Document is gone", e)
        } catch (e: SecurityException) {
            throw DocumentException(DocumentFailure.NO_PERMISSION, "No permission to write", e)
        } catch (e: IOException) {
            throw DocumentException(DocumentFailure.IO, "Document could not be written", e)
        }
    }

    override fun keepPermission(uri: Uri): Boolean {
        val both = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            resolver.takePersistableUriPermission(uri, both)
            true
        } catch (e: SecurityException) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            } catch (inner: SecurityException) {
                false
            }
        }
    }

    override fun childNames(treeUri: Uri): List<String> = try {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            val names = ArrayList<String>()
            while (cursor.moveToNext()) cursor.getString(0)?.let(names::add)
            names
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
