package com.hesaplarimdijital.kitapbulucu.store

import com.hesaplarimdijital.kitapbulucu.model.Owner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A copy of each file as it was right before the app wrote it, in the app's private storage;
 * the last [KEEP] per owner stay (ARCHITECTURE 7).
 */
class Backups(private val dir: File, private val keep: Int = KEEP) {

    class Backup(val file: File, val at: Long)

    /** Saves [bytes] as a backup stamped [at] and drops the oldest beyond [keep]. */
    fun save(owner: Owner, bytes: ByteArray, at: Long): File {
        val folder = File(dir, owner.name)
        folder.mkdirs()
        val stamp = FORMAT.format(Date(at))
        var target = File(folder, "$stamp.xlsx")
        var n = 1
        while (target.exists()) target = File(folder, "$stamp-${n++}.xlsx")
        target.writeBytes(bytes)
        prune(owner)
        return target
    }

    /** Newest first. */
    fun list(owner: Owner): List<Backup> =
        File(dir, owner.name).listFiles { f -> f.isFile && f.name.endsWith(".xlsx") }
            ?.map { Backup(it, it.lastModified()) }
            ?.sortedByDescending { it.file.name }
            .orEmpty()

    private fun prune(owner: Owner) {
        list(owner).drop(keep).forEach { it.file.delete() }
    }

    companion object {
        const val KEEP = 10
        private val FORMAT get() = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.ROOT)
    }
}
