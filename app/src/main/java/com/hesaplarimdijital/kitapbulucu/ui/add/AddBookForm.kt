package com.hesaplarimdijital.kitapbulucu.ui.add

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.ChangeApplier
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore

/** What the add form holds, as typed. */
data class AddBookFields(
    val title: String = "",
    val author: String = "",
    val owner: Owner = Owner.DAD,
    val shelf: String = "",
    val copies: String = "1",
    val signed: Boolean = false,
    val publisher: String = "",
    val year: String = "",
    val isbn: String = "",
    val note: String = "",
) {
    /** After a save the form stays open for the next book: same library and shelf, the rest empty. */
    fun next(): AddBookFields = AddBookFields(owner = owner, shelf = shelf)
}

/** The rules of the add form, kept apart from the screen so they are tested on their own. */
object AddBookForm {
    sealed class Check {
        object NeedTitle : Check()
        object BadCopies : Check()
        /** The same title, author and signed mark is already in that library. */
        data class Exists(val book: Book, val existing: Book) : Check()
        /** Writing to the other person's list: ask first. */
        data class OtherList(val book: Book) : Check()
        data class Ready(val book: Book) : Check()
    }

    /**
     * [me] is whose device this is; [table] what the chosen library holds now (pending changes
     * included). [confirmedOther] and [confirmedDuplicate] are the user's answers so far.
     */
    fun check(fields: AddBookFields, me: Owner?, table: BookTable?, confirmedOther: Boolean = false, confirmedDuplicate: Boolean = false): Check {
        if (fields.title.isBlank()) return Check.NeedTitle
        val copies = fields.copies.trim().ifEmpty { "1" }.toIntOrNull()
        if (copies == null || copies < 1) return Check.BadCopies
        val book = LibraryStore.tidy(
            Book(
                title = fields.title, author = fields.author, shelf = fields.shelf, copies = copies, signed = fields.signed,
                publisher = fields.publisher, year = fields.year, isbn = fields.isbn, note = fields.note,
                extra = List(table?.extraHeaders?.size ?: 0) { "" },
            ),
        )
        if (!confirmedDuplicate) {
            val existing = table?.books?.firstOrNull { ChangeApplier.identity(it) == ChangeApplier.identity(book) }
            if (existing != null) return Check.Exists(book, existing)
        }
        if (!confirmedOther && me != null && fields.owner != me) return Check.OtherList(book)
        return Check.Ready(book)
    }
}
