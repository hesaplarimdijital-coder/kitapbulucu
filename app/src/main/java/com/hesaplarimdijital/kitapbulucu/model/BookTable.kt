package com.hesaplarimdijital.kitapbulucu.model

import com.hesaplarimdijital.kitapbulucu.text.TurkishText
import com.hesaplarimdijital.kitapbulucu.xlsx.Sheet
import com.hesaplarimdijital.kitapbulucu.xlsx.SheetSpec

/** Header text reduced to what matters: "  Kitap  ADI " -> "kitap adi". A top-level function because enum entries are built before their companion. */
private fun foldHeader(header: String): String = TurkishText.asciiFold(TurkishText.squeeze(header))

/** The columns the app knows, with the header it writes and the headers it accepts. */
enum class BookColumn(val header: String, val width: Double, vararg aliases: String) {
    TITLE("Kitap Adı", 44.0, "kitap", "ad", "adi", "baslik", "eser", "kitabin adi", "title", "book"),
    AUTHOR("Yazar", 26.0, "yazari", "yazarlar", "yazan", "author"),
    SHELF("Raf", 10.0, "raf no", "yer", "konum", "dolap", "shelf"),
    COPIES("Adet", 7.0, "sayi", "miktar", "kopya", "copies", "count"),
    SIGNED("İmzalı", 8.0, "imza", "signed"),
    PUBLISHER("Yayınevi", 20.0, "yayin evi", "yayinci", "publisher"),
    YEAR("Yıl", 8.0, "basim yili", "yayin yili", "tarih", "year"),
    ISBN("ISBN", 16.0, "isbn no", "isbn-13", "isbn13"),
    NOTE("Not", 36.0, "notlar", "aciklama", "note", "notes"),
    ;

    /** Folded forms that mean this column. */
    val keys: Set<String> = (listOf(header) + aliases).map(::foldHeader).toSet()

    companion object {
        fun fold(header: String): String = foldHeader(header)

        fun match(header: String): BookColumn? {
            val key = fold(header)
            if (key.isEmpty()) return null
            return entries.firstOrNull { key in it.keys }
        }
    }
}

/** One library as rows of books plus the headers of the columns the app carries along unchanged. */
class BookTable(val books: List<Book>, val extraHeaders: List<String> = emptyList()) {

    /** The sheet to write: standard columns first, unknown columns after them with their own headers. */
    fun toSheetSpec(name: String): SheetSpec = SheetSpec(
        name = name,
        header = HEADERS + extraHeaders,
        rows = books.map { book ->
            listOf(
                book.title, book.author, book.shelf, book.copies.toString(), if (book.signed) SIGNED_TEXT else "",
                book.publisher, book.year, book.isbn, book.note,
            ) + List(extraHeaders.size) { book.extra.getOrNull(it).orEmpty() }
        },
        numericColumns = setOf(BookColumn.COPIES.ordinal),
        columnWidths = BookColumn.entries.map { it.width } + List(extraHeaders.size) { EXTRA_WIDTH },
    )

    /** A sheet read back into books. [headerRow] is the 0-based row that held the headers. */
    class Parsed(val table: BookTable, val headerRow: Int, val problems: List<Problem>)

    /** A cell the app could not take as written; the row is 1-based as the user sees it in Excel. */
    data class Problem(val row: Int, val column: BookColumn, val value: String)

    companion object {
        val HEADERS: List<String> = BookColumn.entries.map { it.header }
        const val SIGNED_TEXT = "Evet"
        private const val EXTRA_WIDTH = 18.0
        const val HEADER_SEARCH_ROWS = 20
        private const val PREVIEW_SCAN_ROWS = 200
        private const val MAX_TITLE_LINES = 3
        private val SIGNED_WORDS = setOf("evet", "e", "x", "var", "imzali", "imza", "1", "dogru", "true", "✓", "✔")

        /**
         * Books of [sheet]. The header row is found by its column names; when none is found and the
         * user made a [mapping] for this sheet, the mapping says which column holds what. Null when
         * neither works: the sheet needs a mapping.
         */
        fun parse(sheet: Sheet, mapping: ColumnMapping? = null): Parsed? {
            val detected = detectHeaderRow(sheet)
            val useMapping = mapping != null && mapping.columns.containsValue(BookColumn.TITLE) && mapping.fits(sheet, detected)
            val headerRow: Int
            val standard = HashMap<BookColumn, Int>()
            val extraColumns = ArrayList<Int>()
            val header: List<String>
            if (useMapping) {
                headerRow = mapping!!.headerRow
                header = if (headerRow >= 0) sheet.rows[headerRow] else emptyList()
                for ((index, column) in mapping.columns) if (column !in standard) standard[column] = index
                for (index in 0 until columnCount(sheet)) if (index !in standard.values) extraColumns.add(index)
            } else {
                if (detected < 0) return null
                headerRow = detected
                header = sheet.rows[headerRow]
                header.forEachIndexed { index, text ->
                    val column = BookColumn.match(text)
                    if (column != null && column !in standard) {
                        standard[column] = index
                    } else {
                        extraColumns.add(index)
                    }
                }
            }
            val body = sheet.rows.drop(headerRow + 1)
            // unknown columns are kept when they have a header or hold anything at all
            val keptExtras = extraColumns.filter { index -> header.getOrNull(index)?.isNotBlank() == true || body.any { it.getOrNull(index)?.isNotBlank() == true } }
            val extraHeaders = keptExtras.map { TurkishText.squeeze(header.getOrNull(it).orEmpty()) }
            val problems = ArrayList<Problem>()
            val books = ArrayList<Book>()
            body.forEachIndexed { offset, row ->
                if (looksLikeHeader(row)) return@forEachIndexed // a header repeated inside the list is not a book
                fun cell(column: BookColumn): String = standard[column]?.let { row.getOrNull(it) }.orEmpty()
                val excelRow = headerRow + offset + 2
                val copiesText = cell(BookColumn.COPIES).trim()
                val copies = copiesText.toIntOrNull()
                if (copiesText.isNotEmpty() && (copies == null || copies < 1)) problems.add(Problem(excelRow, BookColumn.COPIES, copiesText))
                val book = Book(
                    title = TurkishText.squeeze(cell(BookColumn.TITLE)),
                    author = TurkishText.squeeze(cell(BookColumn.AUTHOR)),
                    shelf = TurkishText.squeeze(cell(BookColumn.SHELF)),
                    copies = copies?.takeIf { it >= 1 } ?: 1,
                    signed = isSigned(cell(BookColumn.SIGNED)),
                    publisher = TurkishText.squeeze(cell(BookColumn.PUBLISHER)),
                    year = TurkishText.squeeze(cell(BookColumn.YEAR)),
                    isbn = TurkishText.squeeze(cell(BookColumn.ISBN)),
                    note = TurkishText.squeeze(cell(BookColumn.NOTE)),
                    extra = keptExtras.map { row.getOrNull(it).orEmpty() },
                )
                if (!book.isBlank) books.add(book)
            }
            return Parsed(BookTable(books, extraHeaders), headerRow, problems)
        }

        /**
         * The 0-based row that names the columns, or -1. A header with more than a few filled rows
         * above it is not taken: those rows would be books lost above the header (the old hand-kept
         * list repeats "KİTAP | YAZAR" in the middle), so such a sheet goes to the mapping screen.
         */
        fun detectHeaderRow(sheet: Sheet): Int {
            val row = sheet.rows.take(HEADER_SEARCH_ROWS).indexOfFirst { looksLikeHeader(it) }
            if (row < 0) return -1
            val filledAbove = sheet.rows.take(row).count { cells -> cells.any { it.isNotBlank() } }
            return if (filledAbove <= MAX_TITLE_LINES) row else -1
        }

        /** The widest of the first rows: how many columns a mapping screen shows. */
        fun columnCount(sheet: Sheet): Int = sheet.rows.take(PREVIEW_SCAN_ROWS).maxOfOrNull { it.size } ?: 0

        fun isSigned(text: String): Boolean = BookColumn.fold(text) in SIGNED_WORDS

        /** A header row names the title column and at least one other known column. */
        fun looksLikeHeader(row: List<String>): Boolean {
            val matched = row.mapNotNull { BookColumn.match(it) }.toSet()
            return BookColumn.TITLE in matched && matched.size >= 2
        }
    }
}
