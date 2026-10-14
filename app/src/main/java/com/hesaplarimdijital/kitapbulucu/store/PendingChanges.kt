package com.hesaplarimdijital.kitapbulucu.store

import com.hesaplarimdijital.kitapbulucu.model.Owner
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Changes not yet written to their file, kept on disk so that nothing is lost when the app is
 * closed before a write succeeds (ARCHITECTURE 7). The file is rewritten whole on every change;
 * it holds at most a handful of records.
 */
class PendingChanges(private val file: File) {
    private val changes = ArrayList<Change>()
    private var loaded = false

    @Synchronized
    fun all(): List<Change> {
        ensureLoaded()
        return changes.toList()
    }

    @Synchronized
    fun forOwner(owner: Owner): List<Change> = all().filter { it.owner == owner }

    @Synchronized
    fun add(change: Change) {
        ensureLoaded()
        changes.add(change)
        save()
    }

    /** Many changes with one save: a merge of thousands of books writes the file once. */
    @Synchronized
    fun addAll(added: Collection<Change>) {
        ensureLoaded()
        changes.addAll(added)
        save()
    }

    @Synchronized
    fun remove(ids: Collection<String>) {
        ensureLoaded()
        if (changes.removeAll { it.id in ids }) save()
    }

    @Synchronized
    fun clear(owner: Owner) {
        ensureLoaded()
        if (changes.removeAll { it.owner == owner }) save()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!file.isFile) return
        try {
            val array = JSONObject(file.readText()).optJSONArray("changes") ?: return
            for (i in 0 until array.length()) {
                ChangeJson.fromJson(array.getJSONObject(i))?.let(changes::add)
            }
        } catch (e: Exception) {
            // an unreadable pending file is kept aside rather than silently dropped
            file.renameTo(File(file.parentFile, file.name + ".bozuk-" + System.currentTimeMillis()))
        }
    }

    private fun save() {
        file.parentFile?.mkdirs()
        if (changes.isEmpty()) {
            file.delete()
            return
        }
        val json = JSONObject().put("changes", JSONArray().apply { changes.forEach { put(ChangeJson.toJson(it)) } })
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(file)) {
            file.writeText(json.toString())
            temp.delete()
        }
    }
}
