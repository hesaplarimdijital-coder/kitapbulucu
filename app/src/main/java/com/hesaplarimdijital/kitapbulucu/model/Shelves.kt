package com.hesaplarimdijital.kitapbulucu.model

import com.hesaplarimdijital.kitapbulucu.text.TurkishText
import java.text.Collator
import java.util.Locale

/** The books of one shelf; [shelf] is empty for books whose shelf is not written yet. */
class ShelfGroup(val shelf: String, val books: List<IndexedValue<Book>>) {
    val isUnshelved: Boolean get() = shelf.isEmpty()
    val copies: Int get() = books.sumOf { it.value.copies }
}

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
