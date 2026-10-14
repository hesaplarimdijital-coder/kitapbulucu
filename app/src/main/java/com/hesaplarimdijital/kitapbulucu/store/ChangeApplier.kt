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
        val books = ArrayList(table.books)
        val notes = ArrayList<Note>()
        for (change in changes) {
            val outcome = when (change) {
                is Change.Add -> add(books, change.book)
                is Change.Edit -> edit(books, change.before, change.after)
                is Change.Remove -> remove(books, change.before)
            }
            notes.add(Note(change, outcome))
        }
        return Result(BookTable(books, table.extraHeaders), notes)
    }

    /** The result of applying [changes] as the screens should show it before the file is written. */
    fun preview(table: BookTable?, changes: List<Change>): BookTable? =
        if (table == null || changes.isEmpty()) table else apply(table, changes).table

    private fun add(books: MutableList<Book>, book: Book): Outcome {
        val index = books.indexOfFirst { identity(it) == identity(book) }
        if (index < 0) {
            books.add(book)
            return Outcome.APPLIED
        }
        val existing = books[index]
        books[index] = existing.copy(copies = existing.copies + book.copies)
        return Outcome.MERGED_INTO_EXISTING
    }

    private fun edit(books: MutableList<Book>, before: Book, after: Book): Outcome {
        val exact = books.indexOf(before)
        if (exact >= 0) {
            books[exact] = after
            return Outcome.APPLIED
        }
        val loose = books.indexOfFirst { identity(it) == identity(before) }
        if (loose >= 0) {
            books[loose] = changedFields(books[loose], before, after)
            return Outcome.EDITED_LOOSE_MATCH
        }
        books.add(after)
        return Outcome.ADDED_INSTEAD_OF_EDIT
    }

    private fun remove(books: MutableList<Book>, before: Book): Outcome {
        val exact = books.indexOf(before)
        if (exact >= 0) {
            books.removeAt(exact)
            return Outcome.APPLIED
        }
        val loose = books.indexOfFirst { identity(it) == identity(before) }
        if (loose >= 0) {
            books.removeAt(loose)
            return Outcome.APPLIED
        }
        return Outcome.ALREADY_GONE
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
