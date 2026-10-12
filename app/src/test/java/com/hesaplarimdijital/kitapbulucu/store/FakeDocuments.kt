package com.hesaplarimdijital.kitapbulucu.store

import android.net.Uri

/** Documents in memory for tests: a uri maps to bytes, or to a failure. */
class FakeDocuments : Documents {
    val bytes = HashMap<Uri, ByteArray>()
    val failures = HashMap<Uri, DocumentFailure>()
    val infos = HashMap<Uri, DocumentInfo>()
    val kept = ArrayList<Uri>()
    var children = listOf<String>()
    var reads = 0
    var writeFailure: DocumentFailure? = null

    override fun info(uri: Uri): DocumentInfo? = infos[uri]

    override fun readBytes(uri: Uri, maxBytes: Int): ByteArray {
        reads++
        failures[uri]?.let { throw DocumentException(it, "test failure") }
        return bytes[uri] ?: throw DocumentException(DocumentFailure.NOT_FOUND, "no such document")
    }

    override fun childNames(treeUri: Uri): List<String> = children

    override fun writeBytes(uri: Uri, bytes: ByteArray) {
        writeFailure?.let { throw DocumentException(it, "test failure") }
        this.bytes[uri] = bytes
    }

    override fun keepPermission(uri: Uri): Boolean {
        kept.add(uri)
        return true
    }
}
