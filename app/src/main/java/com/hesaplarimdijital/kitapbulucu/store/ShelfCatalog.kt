package com.hesaplarimdijital.kitapbulucu.store

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.model.ShelfEntry
import com.hesaplarimdijital.kitapbulucu.model.Shelves
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The shelves the user created, per list, kept only on this device (S-33; the user decided on
 * 15.10.2026 that they do not go to Drive). A shelf here may hold no books yet; the shelf of a
 * book is still the `Raf` column of the Excel file. One small JSON file, rewritten whole.
 */
class ShelfCatalog(private val file: File) {
    private val _entries = MutableStateFlow(load())

    /** Created shelves of each list, in the order they were made. */
    val entries: StateFlow<Map<Owner, List<ShelfEntry>>> = _entries.asStateFlow()

    fun list(owner: Owner): List<ShelfEntry> = _entries.value[owner].orEmpty()

    fun contains(owner: Owner, code: String): Boolean {
        val key = Shelves.key(code)
        return key.isNotEmpty() && list(owner).any { Shelves.key(it.code) == key }
    }

    /** Adds a shelf; false when the code is empty or the list already has it. */
    @Synchronized
    fun add(owner: Owner, code: String, note: String = ""): Boolean {
        val clean = Shelves.clean(code)
        if (Shelves.key(clean).isEmpty() || contains(owner, clean)) return false
        update(owner, list(owner) + ShelfEntry(clean, Shelves.clean(note)))
        return true
    }

    /**
     * Gives the shelf [from] the code [to] and the description [note]. A shelf that only books
     * named so far joins the list. Any other entry already called [to] is folded into it.
     */
    @Synchronized
    fun rename(owner: Owner, from: String, to: String, note: String) {
        val clean = Shelves.clean(to)
        if (Shelves.key(clean).isEmpty()) return
        val fromKey = Shelves.key(from)
        val toKey = Shelves.key(clean)
        val renamed = ShelfEntry(clean, Shelves.clean(note))
        val current = list(owner)
        val at = current.indexOfFirst { Shelves.key(it.code) == fromKey }
        val kept = current.filterIndexed { index, entry -> index != at && Shelves.key(entry.code) != toKey }
        val result = if (at < 0) {
            kept + renamed
        } else {
            val before = current.take(at).count { Shelves.key(it.code) != toKey }
            kept.take(before) + renamed + kept.drop(before)
        }
        update(owner, result)
    }

    /** Takes a created shelf off the list; the books are not touched. */
    @Synchronized
    fun remove(owner: Owner, code: String): Boolean {
        val key = Shelves.key(code)
        val current = list(owner)
        val left = current.filterNot { Shelves.key(it.code) == key }
        if (left.size == current.size) return false
        update(owner, left)
        return true
    }

    private fun update(owner: Owner, list: List<ShelfEntry>) {
        val next = _entries.value.toMutableMap()
        next[owner] = list
        _entries.value = next
        save(next)
    }

    private fun load(): Map<Owner, List<ShelfEntry>> {
        if (!file.isFile) return emptyMap()
        return try {
            val json = JSONObject(file.readText())
            Owner.entries.associateWith { owner ->
                val array = json.optJSONArray(owner.name) ?: JSONArray()
                (0 until array.length()).mapNotNull { i ->
                    val item = array.optJSONObject(i) ?: return@mapNotNull null
                    val code = Shelves.clean(item.optString("code"))
                    if (Shelves.key(code).isEmpty()) null else ShelfEntry(code, Shelves.clean(item.optString("note")))
                }.distinctBy { Shelves.key(it.code) }
            }
        } catch (e: Exception) {
            // an unreadable file is kept aside rather than silently overwritten
            file.renameTo(File(file.parentFile, file.name + ".bozuk-" + System.currentTimeMillis()))
            emptyMap()
        }
    }

    private fun save(all: Map<Owner, List<ShelfEntry>>) {
        val json = JSONObject()
        for ((owner, list) in all) {
            json.put(owner.name, JSONArray().apply { list.forEach { put(JSONObject().put("code", it.code).put("note", it.note)) } })
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(file)) {
            file.writeText(json.toString())
            temp.delete()
        }
    }
}

/**
 * Putting books on a shelf (S-34, S-35), worked out before anything is asked or written: which
 * books change, which of them leave another shelf, which are already there.
 */
class ShelfPlacement(
    val owner: Owner,
    val shelf: String,
    /** One Edit per book that is not on [shelf] yet. */
    val changes: List<Change.Edit>,
    /** Books that stand on another shelf now and would leave it. */
    val moving: List<Book>,
    /** Books already on [shelf]; nothing happens to them. */
    val already: List<Book>,
) {
    /** The shelves the moving books leave, each once, in the order met. */
    val fromShelves: List<String> get() = moving.map { it.shelf }.distinctBy { Shelves.key(it) }

    companion object {
        fun plan(owner: Owner, books: List<Book>, shelf: String): ShelfPlacement {
            val target = Shelves.clean(shelf)
            val key = Shelves.key(target)
            val changes = ArrayList<Change.Edit>()
            val moving = ArrayList<Book>()
            val already = ArrayList<Book>()
            for (book in books) {
                val current = Shelves.key(book.shelf)
                when {
                    current == key && book.shelf == target -> already.add(book)
                    current == key -> {
                        // the same shelf written differently: tidy the spelling, no question
                        already.add(book)
                        changes.add(Change.Edit(owner, book, book.copy(shelf = target)))
                    }
                    else -> {
                        if (current.isNotEmpty()) moving.add(book)
                        changes.add(Change.Edit(owner, book, book.copy(shelf = target)))
                    }
                }
            }
            return ShelfPlacement(owner, target, changes, moving, already)
        }

        /** Every book of [owner]'s list on shelf [from] gets the code [to]. */
        fun rename(owner: Owner, books: List<Book>, from: String, to: String): List<Change.Edit> {
            val fromKey = Shelves.key(from)
            val target = Shelves.clean(to)
            if (fromKey.isEmpty()) return emptyList()
            return books.filter { Shelves.key(it.shelf) == fromKey && it.shelf != target }.map { Change.Edit(owner, it, it.copy(shelf = target)) }
        }
    }
}
