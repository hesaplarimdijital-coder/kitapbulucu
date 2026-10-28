package com.hesaplarimdijital.kitapbulucu.store

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.text.TitleCase

/**
 * The one-time fix of titles and authors typed in capitals ("FYODOR DOSTOYEVSKİ" ->
 * "Fyodor Dostoyevski", [TitleCase]): one [Change.Edit] per book whose title or author changes.
 * Every other field, unknown columns included, stays as it is.
 */
object TitleCaseFix {

    /** What the fix would do: the edits per list and a few before -> after pairs to show. */
    class Plan(val edits: Map<Owner, List<Change.Edit>>, val examples: List<Pair<String, String>>) {
        fun count(owner: Owner): Int = edits[owner].orEmpty().size
        val total: Int get() = edits.values.sumOf { it.size }
        val changes: List<Change> get() = Owner.entries.flatMap { edits[it].orEmpty() }
    }

    /** The book with its title and author fixed. */
    fun fixed(book: Book): Book = book.copy(title = TitleCase.fix(book.title), author = TitleCase.fix(book.author))

    /** The edits for [owner]'s [books], in list order; books that would stay the same are left out. */
    fun edits(owner: Owner, books: List<Book>): List<Change.Edit> {
        val result = ArrayList<Change.Edit>()
        for (book in books) {
            val after = fixed(book)
            if (after != book) result.add(Change.Edit(owner, book, after))
        }
        return result
    }

    /** The edits for every list in [books] and up to [exampleCount] distinct examples, titles and authors alternating. */
    fun plan(books: Map<Owner, List<Book>>, exampleCount: Int = 3): Plan {
        val edits = Owner.entries.associateWith { edits(it, books[it].orEmpty()) }.filterValues { it.isNotEmpty() }
        val examples = LinkedHashMap<String, String>()
        val all = Owner.entries.flatMap { edits[it].orEmpty() }
        val titles = all.asSequence().filter { it.before.title != it.after.title }.map { it.before.title to it.after.title }.iterator()
        val authors = all.asSequence().filter { it.before.author != it.after.author }.map { it.before.author to it.after.author }.iterator()
        var turn = 0
        while (examples.size < exampleCount && (titles.hasNext() || authors.hasNext())) {
            val source = if ((turn++ % 2 == 0 && titles.hasNext()) || !authors.hasNext()) titles else authors
            val (before, after) = source.next()
            examples.putIfAbsent(before, after)
        }
        return Plan(edits, examples.toList())
    }
}
