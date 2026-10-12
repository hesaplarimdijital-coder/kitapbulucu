package com.hesaplarimdijital.kitapbulucu.xlsx

/** One sheet as text: `rows[i]` is sheet row i + 1; gaps are empty strings, empty rows are empty lists. */
class Sheet(val name: String, val rows: List<List<String>>) {
    val isEmpty: Boolean get() = rows.none { row -> row.any { it.isNotEmpty() } }
}

class Workbook(val sheets: List<Sheet>) {
    /** Sheets that hold at least one non-empty cell, in workbook order. */
    val filledSheets: List<Sheet> get() = sheets.filter { !it.isEmpty }
}

/** What a file turned out to be, judged by its first bytes rather than its name. */
enum class FileKind { XLSX, XLS, CSV, UNKNOWN }

/** Why a file could not be read; the UI turns [reason] into a sentence the user can act on. */
class SpreadsheetException(val reason: Reason, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Reason {
        /** The old binary Excel format: "save it as .xlsx and try again". */
        OLD_XLS,
        /** Neither a workbook nor a text table. */
        NOT_A_SPREADSHEET,
        /** A workbook that is cut off or malformed. */
        DAMAGED,
        /** Larger than the app is willing to read. */
        TOO_LARGE,
    }
}

object FileKinds {
    private val ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val OLE2 = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

    fun detect(head: ByteArray): FileKind = when {
        head.startsWith(ZIP) -> FileKind.XLSX
        head.startsWith(OLE2) -> FileKind.XLS
        looksLikeText(head) -> FileKind.CSV
        else -> FileKind.UNKNOWN
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    /** Text has no NUL bytes and few control characters in its first kilobyte. */
    private fun looksLikeText(head: ByteArray): Boolean {
        if (head.isEmpty()) return false
        var control = 0
        for (b in head.take(1024)) {
            val value = b.toInt() and 0xFF
            if (value == 0) return false
            if (value < 0x20 && value != 0x09 && value != 0x0A && value != 0x0D) control++
        }
        return control * 20 < head.size.coerceAtMost(1024)
    }
}
