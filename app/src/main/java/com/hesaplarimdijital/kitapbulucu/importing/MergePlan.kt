package com.hesaplarimdijital.kitapbulucu.importing

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookColumn
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.search.EditDistance
import com.hesaplarimdijital.kitapbulucu.store.Change
import com.hesaplarimdijital.kitapbulucu.text.Cleaning
import com.hesaplarimdijital.kitapbulucu.text.Fold
import com.hesaplarimdijital.kitapbulucu.text.TurkishText

/**
 * Merging an Excel file into a list (S-23, ARCHITECTURE 10). The incoming rows are cleaned by
 * the first cleaning's rules, then compared with what the list holds: new books, books whose
 * details differ, books the file does not have, and pairs only the user can judge ("Suç ve
 * Ceza" and "Suç ve Ceza 1"). Nothing is written until the user applies the plan.
 */
object MergePlan {

    /** A field that differs: the list's value and the file's. An empty file value never clears a field. */
    data class FieldChange(val column: BookColumn, val old: String, val new: String)

    data class Changed(val existing: Book, val incoming: Book, val fields: List<FieldChange>) {
        /** The existing row with the file's non-empty values for the changed fields. */
        val merged: Book
            get() = fields.fold(existing) { book, change ->
                when (change.column) {
                    BookColumn.TITLE -> book.copy(title = change.new)
                    BookColumn.AUTHOR -> book.copy(author = change.new)
                    BookColumn.SHELF -> book.copy(shelf = change.new)
                    BookColumn.COPIES -> book.copy(copies = change.new.toIntOrNull() ?: book.copies)
                    BookColumn.SIGNED -> book.copy(signed = change.new.isNotEmpty())
                    BookColumn.PUBLISHER -> book.copy(publisher = change.new)
                    BookColumn.YEAR -> book.copy(year = change.new)
                    BookColumn.ISBN -> book.copy(isbn = change.new)
                    BookColumn.NOTE -> book.copy(note = change.new)
                }
            }
    }

    /** An incoming book that may or may not be [existing]: the user decides. */
    data class Ambiguous(val incoming: Book, val existing: Book) {
        val key: String get() = decisionKey(incoming, existing)
    }

    class Plan(
        val owner: Owner,
        val added: List<Book>,
        val changed: List<Changed>,
        val missing: List<Book>,
        val ambiguous: List<Ambiguous>,
        /** Rows of the file that were the same book and became one row with a count. */
        val mergedDuplicates: Int,
    ) {
        val isEmpty: Boolean get() = added.isEmpty() && changed.isEmpty() && missing.isEmpty() && ambiguous.isEmpty()
    }

    /** The user's choices on the summary screen. */
    class Choices(
        val addedOff: Set<Int> = emptySet(),
        val changedOff: Set<Int> = emptySet(),
        val missingOn: Set<Int> = emptySet(),
        /** Ambiguous pair key -> true when it is the same book. Unanswered pairs block applying. */
        val sameBook: Map<String, Boolean> = emptyMap(),
    )

    /** Counts for the result line: "47 kitap eklendi, 3 kitap güncellendi, 0 kitap silindi." */
    data class Result(val added: Int, val changed: Int, val removed: Int)

    // ------------------------------------------------------------------ cleaning

    /**
     * The first cleaning's rules on an incoming table: the signed marker out of title and author
     * (İmzalı = Evet, a dedication to the note), and identical rows as one row with their count.
     */
    fun clean(table: BookTable): Pair<List<Book>, Int> {
        val cleaned = table.books.map { book ->
            val title = Cleaning.extractSigned(book.title)
            val author = Cleaning.extractSigned(book.author)
            val notes = listOfNotNull(title.note, author.note)
            val dedication = notes.filter { it != Cleaning.SIGNED_NOTE }.distinct()
            book.copy(
                title = title.text,
                author = author.text,
                signed = book.signed || notes.isNotEmpty(),
                note = (listOf(book.note).filter { it.isNotBlank() } + dedication).distinct().joinToString("; "),
            )
        }.filter { it.title.isNotBlank() || it.author.isNotBlank() }
        val merged = LinkedHashMap<String, Book>()
        var duplicates = 0
        for (book in cleaned) {
            val key = strictKey(book)
            val previous = merged[key]
            if (previous == null) {
                merged[key] = book
            } else {
                merged[key] = previous.copy(copies = previous.copies + book.copies)
                duplicates++
            }
        }
        return merged.values.toList() to duplicates
    }

    /** Every field but the count, ignoring only spacing and letter case: "the same row". */
    private fun strictKey(book: Book): String = listOf(book.title, book.author, book.shelf, book.publisher, book.year, book.isbn, book.note)
        .joinToString("\u0000") { TurkishText.lower(TurkishText.squeeze(it)) } + "\u0000" + book.signed

    // ------------------------------------------------------------------ comparing

    fun plan(owner: Owner, current: List<Book>, incomingTable: BookTable, decisions: Map<String, Boolean> = emptyMap()): Plan {
        val (incoming, duplicates) = clean(incomingTable)
        val unmatched = current.indices.toMutableSet()
        val byIdentity = HashMap<String, MutableList<Int>>()
        val byTitleAuthor = HashMap<String, MutableList<Int>>()
        // typos and doubtful pairs are only looked for among books of the same author
        val byAuthor = HashMap<String, MutableList<Int>>()
        current.forEachIndexed { index, book ->
            byIdentity.getOrPut(identity(book)) { ArrayList() }.add(index)
            byTitleAuthor.getOrPut(titleAuthor(book)) { ArrayList() }.add(index)
            byAuthor.getOrPut(authorKey(book)) { ArrayList() }.add(index)
        }
        val added = ArrayList<Book>()
        val changed = ArrayList<Changed>()
        val ambiguous = ArrayList<Ambiguous>()
        val distance = EditDistance()

        fun take(candidates: List<Int>?): Int? = candidates?.firstOrNull { it in unmatched }

        for (book in incoming) {
            // 1) the same title, author and signed mark
            var match = take(byIdentity[identity(book)])
            // 2) the same title and author, the signed mark differs: a change of İmzalı, when unique
            if (match == null) {
                val sameTitle = byTitleAuthor[titleAuthor(book)]?.filter { it in unmatched }
                if (sameTitle != null && sameTitle.size == 1) match = sameTitle.single()
            }
            // 3) a typo away (but never a different volume number)
            if (match == null) {
                match = byAuthor[authorKey(book)]?.firstOrNull { index -> index in unmatched && isTypo(book, current[index], distance) }
            }
            if (match != null) {
                unmatched.remove(match)
                val fields = differences(current[match], book)
                if (fields.isNotEmpty()) changed.add(Changed(current[match], book, fields))
                continue
            }
            // 4) the same words with another number, or one title extending the other: ask (or remember)
            val doubtful = byAuthor[authorKey(book)]?.firstOrNull { index -> index in unmatched && isDoubtful(book, current[index]) }
            if (doubtful != null) {
                val pair = Ambiguous(book, current[doubtful])
                when (decisions[pair.key]) {
                    true -> {
                        unmatched.remove(doubtful)
                        val fields = differences(current[doubtful], book).filter { it.column != BookColumn.TITLE && it.column != BookColumn.AUTHOR }
                        if (fields.isNotEmpty()) changed.add(Changed(current[doubtful], book, fields))
                    }
                    false -> added.add(book)
                    null -> ambiguous.add(pair)
                }
                continue
            }
            added.add(book)
        }
        // a pair the user is asked about keeps its existing row out of "missing"
        val asked = ambiguous.map { it.existing }.toSet()
        val missing = unmatched.sorted().map { current[it] }.filter { it !in asked }
        return Plan(owner, added, changed, missing, ambiguous, duplicates)
    }

    /** The changes [choices] make of [plan]; null while an ambiguous pair is unanswered. */
    fun changes(plan: Plan, choices: Choices): Pair<List<Change>, Result>? {
        if (plan.ambiguous.any { choices.sameBook[it.key] == null }) return null
        val changes = ArrayList<Change>()
        var added = 0
        var changed = 0
        var removed = 0
        plan.added.forEachIndexed { index, book ->
            if (index !in choices.addedOff) {
                changes.add(Change.Add(plan.owner, book))
                added++
            }
        }
        plan.changed.forEachIndexed { index, change ->
            if (index !in choices.changedOff) {
                changes.add(Change.Edit(plan.owner, change.existing, change.merged))
                changed++
            }
        }
        for (pair in plan.ambiguous) {
            if (choices.sameBook[pair.key] == true) {
                val fields = differences(pair.existing, pair.incoming).filter { it.column != BookColumn.TITLE && it.column != BookColumn.AUTHOR }
                if (fields.isNotEmpty()) {
                    changes.add(Change.Edit(plan.owner, pair.existing, Changed(pair.existing, pair.incoming, fields).merged))
                    changed++
                }
            } else {
                changes.add(Change.Add(plan.owner, pair.incoming))
                added++
            }
        }
        plan.missing.forEachIndexed { index, book ->
            if (index in choices.missingOn) {
                changes.add(Change.Remove(plan.owner, book))
                removed++
            }
        }
        return changes to Result(added, changed, removed)
    }

    /** What the file says differently; empty file values are not differences. */
    fun differences(existing: Book, incoming: Book): List<FieldChange> {
        val result = ArrayList<FieldChange>()
        fun text(column: BookColumn, old: String, new: String) {
            if (new.isNotBlank() && TurkishText.squeeze(new) != TurkishText.squeeze(old)) result.add(FieldChange(column, old, TurkishText.squeeze(new)))
        }
        // title and author only change when they differ beyond case, Turkish letters and punctuation (a typo fixed in Excel)
        if (loose(existing.title) != loose(incoming.title)) text(BookColumn.TITLE, existing.title, incoming.title)
        if (loose(existing.author) != loose(incoming.author)) text(BookColumn.AUTHOR, existing.author, incoming.author)
        text(BookColumn.SHELF, existing.shelf, incoming.shelf)
        if (incoming.copies != existing.copies) result.add(FieldChange(BookColumn.COPIES, existing.copies.toString(), incoming.copies.toString()))
        if (incoming.signed != existing.signed) result.add(FieldChange(BookColumn.SIGNED, if (existing.signed) "Evet" else "", if (incoming.signed) "Evet" else ""))
        text(BookColumn.PUBLISHER, existing.publisher, incoming.publisher)
        text(BookColumn.YEAR, existing.year, incoming.year)
        text(BookColumn.ISBN, existing.isbn, incoming.isbn)
        text(BookColumn.NOTE, existing.note, incoming.note)
        return result
    }

    // ------------------------------------------------------------------ keys

    private fun loose(text: String): String = Fold.ascii(Cleaning.fold(text))

    private fun authorKey(book: Book): String = Fold.ascii(Cleaning.fold(book.author))

    private fun titleAuthor(book: Book): String = Fold.ascii(Cleaning.fold(book.title)) + "\u0000" + Fold.ascii(Cleaning.fold(book.author))

    private fun identity(book: Book): String = titleAuthor(book) + "\u0000" + book.signed

    /** One or two letters off in a long enough title, the same author and signed mark, the same numbers. */
    private fun isTypo(a: Book, b: Book, distance: EditDistance): Boolean {
        if (a.signed != b.signed) return false
        val ta = Fold.ascii(Cleaning.fold(a.title))
        val tb = Fold.ascii(Cleaning.fold(b.title))
        if (ta.length < 6 || Cleaning.differsOnlyByNumber(a.title, b.title)) return false
        val bound = if (ta.length >= 12) 2 else 1
        val d = distance.bounded(ta, tb, bound)
        if (d > bound) return false
        // a typo must not change a number
        val numbersA = ta.split(" ").filter(Cleaning::isVolumeToken)
        val numbersB = tb.split(" ").filter(Cleaning::isVolumeToken)
        return numbersA == numbersB
    }

    /** "Suç ve Ceza" / "Suç ve Ceza 1", "Suç ve Ceza" / "Suç ve Ceza 1. Cilt": maybe the same, maybe not. */
    private fun isDoubtful(a: Book, b: Book): Boolean {
        if (a.signed != b.signed) return false
        val sameAuthor = Fold.ascii(Cleaning.fold(a.author)) == Fold.ascii(Cleaning.fold(b.author))
        if (!sameAuthor) return false
        if (Cleaning.differsOnlyByNumber(a.title, b.title)) return true
        val ta = Fold.ascii(Cleaning.fold(a.title)).split(" ")
        val tb = Fold.ascii(Cleaning.fold(b.title)).split(" ")
        val (short, long) = if (ta.size <= tb.size) ta to tb else tb to ta
        return short.size >= 2 && long.size > short.size && long.take(short.size) == short &&
            long.drop(short.size).all { Cleaning.isVolumeToken(it) || it in VOLUME_WORDS }
    }

    private val VOLUME_WORDS = setOf("cilt", "c", "kitap", "bolum", "sayi", "cild", "vol", "volume", "part")

    /** The same pair in either order gives the same key, so a decision is asked once. */
    fun decisionKey(a: Book, b: Book): String = listOf(identity(a), identity(b)).sorted().joinToString("|")
}
