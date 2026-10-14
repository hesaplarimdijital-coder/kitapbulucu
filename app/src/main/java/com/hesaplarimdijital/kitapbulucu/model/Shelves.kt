package com.hesaplarimdijital.kitapbulucu.model

import com.hesaplarimdijital.kitapbulucu.text.TurkishText
import java.text.Collator
import java.util.Locale

/**
 * The books of one shelf; [shelf] is empty for books whose shelf is not written yet. [saved] is
 * true for a shelf the user created on this device (S-33), which may hold no books; [note] is
 * its optional description.
 */
class ShelfGroup(
    val shelf: String,
    val books: List<IndexedValue<Book>>,
    val note: String = "",
    val saved: Boolean = false,
) {
    val isUnshelved: Boolean get() = shelf.isEmpty()
    val copies: Int get() = books.sumOf { it.value.copies }

    /** Only a created shelf without books can be deleted; a shelf that books name stays. */
    val canDelete: Boolean get() = saved && books.isEmpty() && !isUnshelved
}

/** A shelf created on this device: its code ("B3") and an optional short description. */
data class ShelfEntry(val code: String, val note: String = "")

object Shelves {
    /** The same shelf written as "b3", "B3 " or "B 3" is one shelf. */
    fun key(shelf: String): String = TurkishText.upper(TurkishText.squeeze(shelf)).replace(" ", "")

    /** Shelves in natural order (A2 before A10), the unshelved group last; positions are the rows in the file. */
    fun group(books: List<Book>): List<ShelfGroup> {
        val groups = LinkedHashMap<String, MutableList<IndexedValue<Book>>>()
        val names = HashMap<String, String>()
        books.forEachIndexed { index, book ->
            val key = key(book.shelf)
            groups.getOrPut(key) { ArrayList() }.add(IndexedValue(index, book))
            names.putIfAbsent(key, TurkishText.squeeze(book.shelf))
        }
        val shelved = groups.filterKeys { it.isNotEmpty() }.entries.sortedWith(compareBy(NaturalOrder) { it.key })
        return shelved.map { ShelfGroup(names.getValue(it.key), it.value) } +
            listOfNotNull(groups[""]?.let { ShelfGroup("", it) })
    }

    fun unshelvedCount(books: List<Book>): Int = books.count { it.shelf.isBlank() }

    /** A shelf code as it is kept: spaces squeezed, otherwise as typed. */
    fun clean(code: String): String = TurkishText.squeeze(code)

    /**
     * The shelves of one list as the Shelves screen shows them: the created shelves and the
     * shelves the books name, each once, in natural order, the unshelved group last. A created
     * shelf keeps its own spelling and description.
     */
    fun overview(books: List<Book>, catalog: List<ShelfEntry>): List<ShelfGroup> {
        val used = group(books)
        val usedByKey = used.filterNot { it.isUnshelved }.associateBy { key(it.shelf) }
        val savedByKey = LinkedHashMap<String, ShelfEntry>()
        for (entry in catalog) {
            val k = key(entry.code)
            if (k.isNotEmpty()) savedByKey.putIfAbsent(k, entry)
        }
        val keys = (usedByKey.keys + savedByKey.keys).sortedWith(NaturalOrder)
        val shelves = keys.map { k ->
            val saved = savedByKey[k]
            val group = usedByKey[k]
            ShelfGroup(saved?.code ?: group!!.shelf, group?.books.orEmpty(), saved?.note.orEmpty(), saved != null)
        }
        return shelves + used.filter { it.isUnshelved }
    }

    /** Why a shelf code cannot be used, or null when it can. */
    enum class NameProblem { EMPTY, TAKEN }

    /**
     * Checks a new code against the shelves that exist ([existing], created and used). When
     * renaming, [renaming] is the shelf's current code: writing it differently (b3 → B3) is fine.
     */
    fun checkName(code: String, existing: Collection<String>, renaming: String? = null): NameProblem? {
        val k = key(code)
        if (k.isEmpty()) return NameProblem.EMPTY
        if (renaming != null && key(renaming) == k) return null
        return if (existing.any { key(it) == k }) NameProblem.TAKEN else null
    }

    /** "A2" < "A10" < "B1"; letters in Turkish order. */
    object NaturalOrder : Comparator<String> {
        private val collator = Collator.getInstance(Locale("tr", "TR"))
        private val chunk = Regex("\\d+|\\D+")

        override fun compare(a: String, b: String): Int {
            val x = chunk.findAll(a).map { it.value }.toList()
            val y = chunk.findAll(b).map { it.value }.toList()
            for (i in 0 until minOf(x.size, y.size)) {
                val p = x[i]
                val q = y[i]
                val pn = p.toBigIntegerOrNull()
                val qn = q.toBigIntegerOrNull()
                val c = if (pn != null && qn != null) pn.compareTo(qn) else collator.compare(p, q)
                if (c != 0) return c
            }
            return x.size - y.size
        }
    }
}
