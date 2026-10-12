package com.hesaplarimdijital.kitapbulucu.model

import com.hesaplarimdijital.kitapbulucu.text.TurkishText
import com.hesaplarimdijital.kitapbulucu.xlsx.Sheet
import org.json.JSONArray
import org.json.JSONObject

/**
 * Which column of a sheet holds which field, for a file whose headers the app does not
 * recognise or that has no header row at all. Made once by the user on the mapping screen.
 * [headerRow] is the 0-based row with the column names, or -1 when the data starts in the
 * first row; [headerTexts] is that row as it was, so the mapping is not applied to a sheet
 * that changed shape.
 */
data class ColumnMapping(
    val headerRow: Int,
    val columns: Map<Int, BookColumn>,
    val headerTexts: List<String> = emptyList(),
) {
    /**
     * Whether this mapping still describes [sheet]. A header-less mapping applies only while the
     * sheet has no recognisable header ([detectedHeaderRow] < 0): once the app has written the
     * file with its own headers, those win.
     */
    fun fits(sheet: Sheet, detectedHeaderRow: Int): Boolean =
        if (headerRow < 0) {
            detectedHeaderRow < 0
        } else {
            sheet.rows.getOrNull(headerRow)?.let { row -> normalise(row) == headerTexts } == true
        }

    fun toJson(): String = JSONObject()
        .put("headerRow", headerRow)
        .put("columns", JSONObject().apply { columns.forEach { (index, column) -> put(index.toString(), column.name) } })
        .put("headerTexts", JSONArray(headerTexts))
        .toString()

    companion object {
        fun fromJson(json: String?): ColumnMapping? {
            if (json.isNullOrBlank()) return null
            return try {
                val root = JSONObject(json)
                val columnsJson = root.getJSONObject("columns")
                val columns = HashMap<Int, BookColumn>()
                for (key in columnsJson.keys()) {
                    val column = BookColumn.entries.firstOrNull { it.name == columnsJson.getString(key) } ?: continue
                    columns[key.toInt()] = column
                }
                val textsJson = root.optJSONArray("headerTexts") ?: JSONArray()
                ColumnMapping(root.getInt("headerRow"), columns, List(textsJson.length()) { textsJson.getString(it) })
            } catch (e: Exception) {
                null
            }
        }

        /** A header row as the mapping remembers it: trimmed cells, trailing blanks dropped. */
        fun normalise(row: List<String>): List<String> = row.map { TurkishText.squeeze(it) }.dropLastWhile { it.isEmpty() }

        /** A mapping for [headerRow] with the columns named in it. */
        fun of(sheet: Sheet, headerRow: Int, columns: Map<Int, BookColumn>): ColumnMapping =
            ColumnMapping(headerRow, columns, if (headerRow >= 0) normalise(sheet.rows.getOrNull(headerRow).orEmpty()) else emptyList())

        /**
         * The app's best guess to start the mapping screen with: a row near the top that names at
         * least one known column, else no header with the title in the first column and the author in the
         * second (the old hand-kept layout).
         */
        fun guess(sheet: Sheet): ColumnMapping {
            val rows = sheet.rows.take(BookTable.HEADER_SEARCH_ROWS)
            val headerRow = rows.indexOfFirst { row -> row.any { BookColumn.match(it) != null } }
            if (headerRow >= 0 && BookTable.fewRowsAbove(sheet, headerRow)) {
                val columns = HashMap<Int, BookColumn>()
                rows[headerRow].forEachIndexed { index, text ->
                    val column = BookColumn.match(text)
                    if (column != null && column !in columns.values) columns[index] = column
                }
                return of(sheet, headerRow, columns)
            }
            val count = BookTable.columnCount(sheet)
            val columns = buildMap {
                if (count >= 1) put(0, BookColumn.TITLE)
                if (count >= 2) put(1, BookColumn.AUTHOR)
            }
            return ColumnMapping(-1, columns)
        }
    }
}
