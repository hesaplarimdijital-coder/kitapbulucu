package com.hesaplarimdijital.kitapbulucu.store

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.text.Fold
import com.hesaplarimdijital.kitapbulucu.text.TurkishText

/**
 * Applies pending changes to a table freshly read from the file (ARCHITECTURE 7). Rows have no
 * lasting identity, so a change finds its row first exactly (every field equal) and then
 * loosely (same title, author and signed mark, ignoring case, Turkish letters and spacing).
 *
 * - Add: a loose match already there gets the copies added to it; otherwise the book is appended.
 * - Edit: the exact row, else the loose one, takes only the fields the user changed; when
 *   neither is there the edited book is appended, so the user's work is not lost.
 * - Remove: the exact row, else the loose one, goes; when neither is there nothing happens
 *   and the change is reported as already gone.
 */
object ChangeApplier {

    enum class Outcome { APPLIED, MERGED_INTO_EXISTING, EDITED_LOOSE_MATCH, ADDED_INSTEAD_OF_EDIT, ALREADY_GONE }

    class Note(val change: Change, val outcome: Outcome)

    class Result(val table: BookTable, val notes: List<Note>) {
        /** Notes worth telling the user: a change that did not land exactly as made. */
        val surprises: List<Note> get() = notes.filter { it.outcome != Outcome.APPLIED }
    }

    fun apply(table: BookTable, changes: List<Change>): Result {
        val rows = Rows(table.books)
        val notes = ArrayList<Note>(changes.size)
        for (change in changes) notes.add(Note(change, rows.apply(change)))
        return Result(BookTable(rows.books, table.extraHeaders), notes)
    }

    /** The result of applying [changes] as the screens should show it before the file is written. */
    fun preview(table: BookTable?, changes: List<Change>): BookTable? =
        if (table == null || changes.isEmpty()) table else apply(table, changes).table

    /**
     * The rows being changed, with every row's identity worked out once and counted, so that a
     * merge of thousands of new books stays linear: a book whose identity is not there yet is
     * appended without a search. Until a row is removed the rows of each identity are also kept
     * by position, so thousands of edits (a shelf renamed, titles fixed) find their rows at once;
     * after a removal shifts the positions, rows are looked for one by one again.
     */
    internal class Rows(source: List<Book>) {
        val books = ArrayList(source)
        private val ids = source.mapTo(ArrayList(source.size)) { identity(it) }
        private val counts = HashMap<String, Int>().apply { ids.forEach { put(it, getOrDefault(it, 0) + 1) } }
        /** Ascending row positions per identity; built on the first search, dropped for good by a removal. */
        private var byId: HashMap<String, ArrayList<Int>>? = null
        private var removed = false

        private fun has(id: String) = counts.getOrDefault(id, 0) > 0

        private fun positions(): HashMap<String, ArrayList<Int>>? {
            if (removed) return null
            return byId ?: HashMap<String, ArrayList<Int>>(ids.size * 2).also { map ->
                ids.forEachIndexed { i, id -> map.getOrPut(id) { ArrayList(1) }.add(i) }
                byId = map
            }
        }

        /** The first row with [book]'s identity, or -1. */
        fun loose(book: Book): Int {
            val id = identity(book)
            if (!has(id)) return -1
            return positions()?.get(id)?.firstOrNull() ?: ids.indexOf(id)
        }

        /** The first row equal to [book] in every field, or -1. */
        fun exact(book: Book): Int {
            val id = identity(book)
            if (!has(id)) return -1
            val known = positions()
            if (known != null) {
                for (i in known[id].orEmpty()) if (books[i] == book) return i
                return -1
            }
            for (i in ids.indices) if (ids[i] == id && books[i] == book) return i
            return -1
        }

        fun apply(change: Change): Outcome = when (change) {
            is Change.Add -> add(change.book)
            is Change.Edit -> edit(change.before, change.after)
            is Change.Remove -> remove(change.before)
        }

        private fun append(book: Book) {
            val id = identity(book)
            books.add(book)
            ids.add(id)
            counts[id] = counts.getOrDefault(id, 0) + 1
            byId?.getOrPut(id) { ArrayList(1) }?.add(ids.size - 1)
        }

        private fun set(index: Int, book: Book) {
            val old = ids[index]
            val id = identity(book)
            books[index] = book
            if (old != id) {
                counts[old] = counts.getOrDefault(old, 1) - 1
                ids[index] = id
                counts[id] = counts.getOrDefault(id, 0) + 1
                byId?.let { map ->
                    val previous = map[old]
                    val was = previous?.binarySearch(index) ?: -1
                    if (was >= 0) previous!!.removeAt(was)
                    val list = map.getOrPut(id) { ArrayList(1) }
                    val at = list.binarySearch(index)
                    list.add(if (at < 0) -at - 1 else at, index)
                }
            }
        }

        private fun removeAt(index: Int) {
            val old = ids.removeAt(index)
            books.removeAt(index)
            removed = true
            byId = null
            counts[old] = counts.getOrDefault(old, 1) - 1
        }

        private fun add(book: Book): Outcome {
            val index = loose(book)
            if (index < 0) {
                append(book)
                return Outcome.APPLIED
            }
            val existing = books[index]
            set(index, existing.copy(copies = existing.copies + book.copies))
            return Outcome.MERGED_INTO_EXISTING
        }

        private fun edit(before: Book, after: Book): Outcome {
            val exact = exact(before)
            if (exact >= 0) {
                set(exact, after)
                return Outcome.APPLIED
            }
            val loose = loose(before)
            if (loose >= 0) {
                set(loose, changedFields(books[loose], before, after))
                return Outcome.EDITED_LOOSE_MATCH
            }
            append(after)
            return Outcome.ADDED_INSTEAD_OF_EDIT
        }

        private fun remove(before: Book): Outcome {
            val exact = exact(before)
            if (exact >= 0) {
                removeAt(exact)
                return Outcome.APPLIED
            }
            val loose = loose(before)
            if (loose >= 0) {
                removeAt(loose)
                return Outcome.APPLIED
            }
            return Outcome.ALREADY_GONE
        }
    }

    /** [current] with only the fields that differ between [before] and [after] replaced. */
    fun changedFields(current: Book, before: Book, after: Book): Book = current.copy(
        title = if (after.title != before.title) after.title else current.title,
        author = if (after.author != before.author) after.author else current.author,
        shelf = if (after.shelf != before.shelf) after.shelf else current.shelf,
        copies = if (after.copies != before.copies) after.copies else current.copies,
        signed = if (after.signed != before.signed) after.signed else current.signed,
        publisher = if (after.publisher != before.publisher) after.publisher else current.publisher,
        year = if (after.year != before.year) after.year else current.year,
        isbn = if (after.isbn != before.isbn) after.isbn else current.isbn,
        note = if (after.note != before.note) after.note else current.note,
        extra = if (after.extra != before.extra) after.extra else current.extra,
    )

    /** Title, author and signed mark, flattened: what makes two rows "the same book". */
    fun identity(book: Book): String =
        Fold.ascii(TurkishText.squeeze(book.title)) + "\u0000" + Fold.ascii(TurkishText.squeeze(book.author)) + "\u0000" + (if (book.signed) "1" else "0")
}
