package com.hesaplarimdijital.kitapbulucu.store

import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.xlsx.Sheet

enum class LoadStatus {
    /** No file chosen yet for this owner. */
    NOT_LINKED,
    /** A read is under way; [LibraryState.table] may still hold the cached copy. */
    LOADING,
    /** The table is what the file holds. */
    READY,
    /** The last read failed; [LibraryState.table] is the last good copy, if any. */
    FAILED,
}

/** Why the last read of a library failed. */
enum class LoadFailure {
    NOT_FOUND, NO_PERMISSION, IO, TOO_LARGE, OLD_XLS, NOT_A_SPREADSHEET, DAMAGED,
    /** The sheet has no row with the known column headers. */
    NO_HEADER,
}

/** Everything a screen needs to know about one library. */
data class LibraryState(
    val owner: Owner,
    val status: LoadStatus,
    /** The books, from the file or from the cached copy; null until anything was read. */
    val table: BookTable? = null,
    /** True while [table] comes from the cached copy rather than from the file itself. */
    val fromCache: Boolean = false,
    val failure: LoadFailure? = null,
    val displayName: String? = null,
    /** When the file was last read successfully. */
    val readAt: Long? = null,
    /** When the list last changed, as far as the app knows (the provider's time or when a change was seen). */
    val updatedAt: Long? = null,
    /** Cells the table could not take as written (a count that is not a number). */
    val problems: List<BookTable.Problem> = emptyList(),
    /** Names of the filled sheets, when the workbook has more than one. */
    val sheetNames: List<String> = emptyList(),
) {
    val isLinked: Boolean get() = status != LoadStatus.NOT_LINKED
    val bookCount: Int get() = table?.books?.size ?: 0

    companion object {
        fun notLinked(owner: Owner) = LibraryState(owner, LoadStatus.NOT_LINKED)
    }
}

/** The first rows of each filled sheet of a file and the sheet being read. */
class WorkbookPreview(val sheets: List<Sheet>, val selected: String) {
    /** Sheet names to choose from; empty when there is only one. */
    val filledSheetNames: List<String> get() = if (sheets.size > 1) sheets.map { it.name } else emptyList()
    val selectedSheet: Sheet get() = sheets.firstOrNull { it.name == selected } ?: sheets.first()

    companion object {
        const val ROWS = 30
    }
}

/** A stray file next to the libraries: Excel's lock file or Drive's conflicted copy. */
data class StrayFileWarning(val names: List<String>)
