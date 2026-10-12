package com.hesaplarimdijital.kitapbulucu.model

/** Whose library a book belongs to; the label is what the screens show. */
enum class Owner(val label: String, val fileName: String) {
    DAD("Babam", "Babam.xlsx"),
    ME("Benim", "Benim.xlsx"),
}

/**
 * One row of a library file. A row has no lasting identity in Excel: a book is recognised by
 * all of its fields together. Several copies of the same book are one row with [copies] > 1;
 * a signed copy is its own row.
 */
data class Book(
    val title: String,
    val author: String = "",
    val shelf: String = "",
    val copies: Int = 1,
    val signed: Boolean = false,
    val publisher: String = "",
    val year: String = "",
    val isbn: String = "",
    val note: String = "",
    /** Cells of the columns the app does not know, in the order of [BookTable.extraHeaders]. */
    val extra: List<String> = emptyList(),
) {
    val isBlank: Boolean
        get() = title.isBlank() && author.isBlank() && shelf.isBlank() && publisher.isBlank() && year.isBlank() &&
            isbn.isBlank() && note.isBlank() && extra.all { it.isBlank() }
}
