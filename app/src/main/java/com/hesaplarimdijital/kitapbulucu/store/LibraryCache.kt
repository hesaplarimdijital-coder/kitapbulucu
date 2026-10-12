package com.hesaplarimdijital.kitapbulucu.store

import com.hesaplarimdijital.kitapbulucu.model.Owner
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * The last good copy of each library file in the app's private storage, so the list is on
 * screen the moment the app opens, before Drive answers. Next to each copy a small note says
 * when it was read and when its content was last seen to change.
 */
class LibraryCache(private val dir: File) {

    class Meta(
        val hash: String,
        /** When the file was last read successfully. */
        val readAt: Long,
        /** When the content was last seen to differ from the copy before it. */
        val changedAt: Long?,
        /** The provider's modification time, when it gave one. */
        val fileModifiedAt: Long?,
        val displayName: String?,
    )

    class Entry(val bytes: ByteArray, val meta: Meta)

    fun load(owner: Owner): Entry? {
        val data = dataFile(owner)
        val note = metaFile(owner)
        if (!data.isFile || !note.isFile) return null
        return try {
            val json = JSONObject(note.readText())
            val bytes = data.readBytes()
            val meta = Meta(
                hash = json.getString("hash"),
                readAt = json.getLong("readAt"),
                changedAt = json.optLong("changedAt", -1).takeIf { it > 0 },
                fileModifiedAt = json.optLong("fileModifiedAt", -1).takeIf { it > 0 },
                displayName = json.optString("displayName", "").ifEmpty { null },
            )
            if (meta.hash != hashOf(bytes)) null else Entry(bytes, meta)
        } catch (e: Exception) {
            null
        }
    }

    fun save(owner: Owner, bytes: ByteArray, meta: Meta) {
        dir.mkdirs()
        val temp = File(dir, "${owner.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(dataFile(owner))) {
            dataFile(owner).writeBytes(bytes)
            temp.delete()
        }
        saveMeta(owner, meta)
    }

    /** Refreshes the note (read time and so on) without rewriting the copy. */
    fun saveMeta(owner: Owner, meta: Meta) {
        if (!dataFile(owner).isFile) return
        val json = JSONObject()
            .put("hash", meta.hash)
            .put("readAt", meta.readAt)
            .put("changedAt", meta.changedAt ?: -1)
            .put("fileModifiedAt", meta.fileModifiedAt ?: -1)
            .put("displayName", meta.displayName ?: "")
        metaFile(owner).writeText(json.toString())
    }

    fun clear(owner: Owner) {
        dataFile(owner).delete()
        metaFile(owner).delete()
    }

    private fun dataFile(owner: Owner) = File(dir, "${owner.name}.xlsx")
    private fun metaFile(owner: Owner) = File(dir, "${owner.name}.json")

    companion object {
        /** SHA-256 of the file as hex: the only reliable way to know whether Drive's copy changed. */
        fun hashOf(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
