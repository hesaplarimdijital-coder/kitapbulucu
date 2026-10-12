package com.hesaplarimdijital.kitapbulucu.xlsx

/** Opens whatever the user picked: a workbook, a text table, or something to refuse politely. */
object SpreadsheetReader {
    /** Files bigger than this are refused before anything is parsed. */
    const val MAX_FILE_BYTES = 128 * 1024 * 1024

    fun read(bytes: ByteArray, fileName: String? = null): Workbook {
        if (bytes.size > MAX_FILE_BYTES) throw SpreadsheetException(SpreadsheetException.Reason.TOO_LARGE, "File is ${bytes.size} bytes")
        val lowerName = fileName?.lowercase().orEmpty()
        val sheetName = fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "CSV"
        return when (FileKinds.detect(bytes)) {
            FileKind.XLSX -> XlsxReader.read(bytes.inputStream())
            FileKind.XLS -> throw SpreadsheetException(SpreadsheetException.Reason.OLD_XLS, "Old binary Excel format")
            FileKind.CSV -> Workbook(listOf(CsvReader.read(bytes, sheetName)))
            FileKind.UNKNOWN -> when {
                lowerName.endsWith(".xls") -> throw SpreadsheetException(SpreadsheetException.Reason.OLD_XLS, "Old binary Excel format")
                lowerName.endsWith(".xlsx") || lowerName.endsWith(".xlsm") -> throw SpreadsheetException(SpreadsheetException.Reason.DAMAGED, "Not a valid workbook")
                bytes.isEmpty() -> Workbook(listOf(Sheet(sheetName, emptyList())))
                else -> throw SpreadsheetException(SpreadsheetException.Reason.NOT_A_SPREADSHEET, "Unknown file content")
            }
        }
    }
}
