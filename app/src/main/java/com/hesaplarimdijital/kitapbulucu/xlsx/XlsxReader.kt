package com.hesaplarimdijital.kitapbulucu.xlsx

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.util.SortedMap
import java.util.TreeMap
import java.util.zip.ZipInputStream

/**
 * Reads an .xlsx workbook into text cells with nothing but java.util.zip and the platform's
 * XmlPullParser. Shared, inline, formula-result, boolean and number cells are handled; numbers
 * with a date format become Turkish date text ("15 MART 1998"); errors become empty cells.
 */
object XlsxReader {
    /** The largest workbook part the reader accepts: a 10,000-book list stays far below it. */
    const val MAX_PART_BYTES = 64 * 1024 * 1024

    private const val WORKBOOK = "xl/workbook.xml"
    private const val WORKBOOK_RELS = "xl/_rels/workbook.xml.rels"
    private const val SHARED_STRINGS = "xl/sharedStrings.xml"
    private const val STYLES = "xl/styles.xml"

    fun read(input: InputStream): Workbook {
        val parts = HashMap<String, ByteArray>()
        var sawExcelPart = false
        try {
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val name = entry.name.removePrefix("/")
                    if (name.startsWith("xl/")) sawExcelPart = true
                    if (name == WORKBOOK || name == WORKBOOK_RELS || name == SHARED_STRINGS || name == STYLES ||
                        (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                    ) {
                        parts[name] = readPart(zip)
                    }
                }
            }
        } catch (e: SpreadsheetException) {
            throw e
        } catch (e: IOException) {
            throw SpreadsheetException(SpreadsheetException.Reason.DAMAGED, "Workbook could not be unpacked", e)
        }
        val workbookXml = parts[WORKBOOK] ?: throw if (sawExcelPart) {
            SpreadsheetException(SpreadsheetException.Reason.DAMAGED, "Workbook ends before $WORKBOOK")
        } else {
            SpreadsheetException(SpreadsheetException.Reason.NOT_A_SPREADSHEET, "No $WORKBOOK in the file")
        }
        return try {
            val (sheetRefs, date1904) = parseWorkbook(workbookXml)
            val targets = parts[WORKBOOK_RELS]?.let(::parseRelationships) ?: emptyMap()
            val shared = parts[SHARED_STRINGS]?.let(::parseSharedStrings) ?: emptyList()
            val styles = parts[STYLES]?.let(::parseStyles) ?: Styles(emptyList())
            val sheets = sheetRefs.mapIndexed { index, ref ->
                val target = targets[ref.relationshipId]?.let(::normalizeTarget)
                    ?: "xl/worksheets/sheet${index + 1}.xml"
                val xml = parts[target] ?: parts.entries.firstOrNull { it.key.endsWith("/" + target.substringAfterLast('/')) }?.value
                Sheet(ref.name, if (xml == null) emptyList() else parseSheet(xml, shared, styles, date1904))
            }
            Workbook(sheets)
        } catch (e: XmlPullParserException) {
            throw SpreadsheetException(SpreadsheetException.Reason.DAMAGED, "Workbook XML is malformed", e)
        }
    }

    private fun readPart(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_PART_BYTES) throw SpreadsheetException(SpreadsheetException.Reason.TOO_LARGE, "Workbook part is too large")
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ workbook, rels, styles

    private class SheetRef(val name: String, val relationshipId: String?)
    /** For every cell style, the date or time format code it shows, or null when it is not a date. */
    private class Styles(val dateFormats: List<String?>) {
        fun dateFormat(styleIndex: Int): String? = dateFormats.getOrNull(styleIndex)
    }

    private fun parseWorkbook(bytes: ByteArray): Pair<List<SheetRef>, Boolean> {
        val parser = newParser(bytes)
        val sheets = ArrayList<SheetRef>()
        var date1904 = false
        forEachStartTag(parser) { name ->
            when (name) {
                "sheet" -> sheets.add(SheetRef(parser.attr("name") ?: "Sayfa${sheets.size + 1}", parser.attr("id")))
                "workbookPr" -> date1904 = parser.attr("date1904").let { it == "1" || it == "true" }
            }
        }
        return sheets to date1904
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val parser = newParser(bytes)
        val targets = HashMap<String, String>()
        forEachStartTag(parser) { name ->
            if (name == "Relationship") {
                val id = parser.attr("Id")
                val target = parser.attr("Target")
                if (id != null && target != null) targets[id] = target
            }
        }
        return targets
    }

    /** "worksheets/sheet1.xml" and "/xl/worksheets/sheet1.xml" both mean the same part. */
    private fun normalizeTarget(target: String): String =
        if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"

    private fun parseStyles(bytes: ByteArray): Styles {
        val parser = newParser(bytes)
        val customFormats = HashMap<Int, String>()
        val dateFormats = ArrayList<String?>()
        var inCellXfs = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "numFmt" -> {
                        val id = parser.attr("numFmtId")?.toIntOrNull()
                        val code = parser.attr("formatCode")
                        if (id != null && code != null) customFormats[id] = code
                    }
                    "cellXfs" -> inCellXfs = true
                    "xf" -> if (inCellXfs) {
                        val id = parser.attr("numFmtId")?.toIntOrNull() ?: 0
                        val code = customFormats[id] ?: DateFormats.builtInCode(id)
                        dateFormats.add(code?.takeIf(DateFormats::isDateFormat))
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "cellXfs") {
                inCellXfs = false
            }
            event = parser.next()
        }
        return Styles(dateFormats)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = newParser(bytes)
        val strings = ArrayList<String>()
        val text = StringBuilder()
        var inItem = false
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inItem = true; text.setLength(0) }
                    "t" -> inText = inItem
                    "rPh" -> skip(parser) // phonetic hints are not part of the value
                }
                XmlPullParser.TEXT -> if (inText) text.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "si" -> { strings.add(text.toString()); inItem = false }
                }
            }
            event = parser.next()
        }
        return strings
    }

    // ------------------------------------------------------------------ sheet

    private fun parseSheet(bytes: ByteArray, shared: List<String>, styles: Styles, date1904: Boolean): List<List<String>> {
        val parser = newParser(bytes)
        val grid: SortedMap<Int, SortedMap<Int, String>> = TreeMap()
        var rowNumber = 0
        var column = -1
        var type: String? = null
        var style = -1
        var inValue = false      // <v>
        var inInline = false     // <is>
        var inInlineText = false // <t> inside <is>
        var inFormula = false    // <f>, ignored
        val value = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        rowNumber = parser.attr("r")?.toIntOrNull() ?: (rowNumber + 1)
                        column = -1
                    }
                    "c" -> {
                        val reference = parser.attr("r")
                        column = if (reference != null) CellReferences.columnIndex(reference) else column + 1
                        type = parser.attr("t")
                        style = parser.attr("s")?.toIntOrNull() ?: -1
                        value.setLength(0)
                    }
                    "v" -> inValue = true
                    "is" -> inInline = true
                    "t" -> inInlineText = inInline
                    "f" -> inFormula = true
                    "rPh" -> skip(parser)
                }
                XmlPullParser.TEXT -> if ((inValue || inInlineText) && !inFormula) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "is" -> inInline = false
                    "t" -> inInlineText = false
                    "f" -> inFormula = false
                    "c" -> {
                        val text = cellText(type, value.toString(), shared, styles.dateFormat(style), date1904)
                        if (text.isNotEmpty() && column >= 0 && rowNumber > 0) {
                            grid.getOrPut(rowNumber) { TreeMap() }[column] = text
                        }
                    }
                }
            }
            event = parser.next()
        }
        if (grid.isEmpty()) return emptyList()
        return (1..grid.lastKey()).map { row ->
            val cells = grid[row] ?: return@map emptyList()
            (0..cells.lastKey()).map { cells[it].orEmpty() }
        }
    }

    private fun cellText(type: String?, raw: String, shared: List<String>, dateFormat: String?, date1904: Boolean): String =
        when (type) {
            "s" -> shared.getOrNull(raw.trim().toIntOrNull() ?: -1).orEmpty()
            "inlineStr", "str" -> raw
            "b" -> if (raw.trim() == "1") "DOĞRU" else "YANLIŞ"
            "e" -> ""
            "d" -> raw.trim() // ISO 8601 as stored; rare in practice
            else -> numberText(raw.trim(), dateFormat, date1904)
        }

    private fun numberText(raw: String, dateFormat: String?, date1904: Boolean): String {
        if (raw.isEmpty()) return ""
        val number = try {
            BigDecimal(raw)
        } catch (e: NumberFormatException) {
            return raw
        }
        if (dateFormat != null) {
            val serial = number.toDouble()
            if (serial >= 0) return DateFormats.serialToText(serial, date1904, dateFormat)
        }
        return number.stripTrailingZeros().toPlainString()
    }

    // ------------------------------------------------------------------ parser helpers

    private fun newParser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(bytes.inputStream(), "UTF-8")
    }

    /** Attribute by local name, whatever its namespace prefix (`r:id`, `x:name`). */
    private fun XmlPullParser.attr(name: String): String? {
        for (i in 0 until attributeCount) if (getAttributeName(i) == name) return getAttributeValue(i)
        return null
    }

    private inline fun forEachStartTag(parser: XmlPullParser, action: (String) -> Unit) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) action(parser.name)
            event = parser.next()
        }
    }

    /** Skips the element the parser is on, however deep it nests. */
    private fun skip(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }
}

object CellReferences {
    /** "C7" -> 2, "AA1" -> 26. Letters only are read; anything else ends the column part. */
    fun columnIndex(reference: String): Int {
        var index = 0
        for (ch in reference) {
            if (!ch.isLetter()) break
            index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }

    /** 0 -> "A", 25 -> "Z", 26 -> "AA". */
    fun columnName(index: Int): String {
        var n = index + 1
        val name = StringBuilder()
        while (n > 0) {
            val remainder = (n - 1) % 26
            name.insert(0, 'A' + remainder)
            n = (n - 1) / 26
        }
        return name.toString()
    }
}
