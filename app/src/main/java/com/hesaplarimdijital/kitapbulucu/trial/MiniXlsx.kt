package com.hesaplarimdijital.kitapbulucu.trial

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.SortedMap
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The smallest workbook Excel, Google Sheets and LibreOffice all open: one sheet, every cell a
 * text cell written inline. Just enough for the Drive trial; the real reader and writer
 * (S-07, S-08) replace it.
 */
object MiniXlsx {
    private const val SHEET_PATH = "xl/worksheets/sheet1.xml"
    private const val SHARED_STRINGS_PATH = "xl/sharedStrings.xml"

    /** A workbook whose first sheet holds [rows]; every value is text. */
    fun build(rows: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun part(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            part("[Content_Types].xml", CONTENT_TYPES)
            part("_rels/.rels", ROOT_RELS)
            part("xl/workbook.xml", WORKBOOK)
            part("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            part("xl/styles.xml", STYLES)
            part(SHEET_PATH, sheet(rows))
        }
        return out.toByteArray()
    }

    /**
     * Cells of the first sheet as text, row by row (empty strings for gaps). Handles shared,
     * inline and plain values; numbers come back as the text stored in the file.
     */
    fun readCells(input: InputStream): List<List<String>> {
        var sheet: ByteArray? = null
        var shared: List<String> = emptyList()
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                when (entry.name) {
                    SHEET_PATH -> sheet = zip.readBytes()
                    SHARED_STRINGS_PATH -> shared = parseSharedStrings(zip.readBytes())
                }
            }
        }
        val bytes = sheet ?: throw IllegalArgumentException("No $SHEET_PATH in the workbook")
        return parseSheet(bytes, shared)
    }

    // ------------------------------------------------------------------ writing

    private fun sheet(rows: List<List<String>>): String {
        val xml = StringBuilder()
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        rows.forEachIndexed { rowIndex, cells ->
            xml.append("<row r=\"").append(rowIndex + 1).append("\">")
            cells.forEachIndexed { columnIndex, value ->
                xml.append("<c r=\"").append(columnName(columnIndex)).append(rowIndex + 1).append("\" t=\"inlineStr\"><is><t")
                if (value != value.trim()) xml.append(" xml:space=\"preserve\"")
                xml.append(">").append(escape(value)).append("</t></is></c>")
            }
            xml.append("</row>")
        }
        xml.append("</sheetData></worksheet>")
        return xml.toString()
    }

    /** 0 -> A, 25 -> Z, 26 -> AA. */
    fun columnName(index: Int): String {
        var n = index + 1
        val name = StringBuilder()
        while (n > 0) {
            val remainder = (n - 1) % 26
            name.insert(0, ('A' + remainder))
            n = (n - 1) / 26
        }
        return name.toString()
    }

    /** "C7" -> 2; "AA1" -> 26. */
    fun columnIndex(reference: String): Int {
        var index = 0
        for (ch in reference) {
            if (!ch.isLetter()) break
            index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }

    private fun escape(text: String): String {
        val safe = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch == '&' -> safe.append("&amp;")
                ch == '<' -> safe.append("&lt;")
                ch == '>' -> safe.append("&gt;")
                ch == '"' -> safe.append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> safe.append(ch)
                ch < ' ' -> Unit // control characters are not allowed in XML
                else -> safe.append(ch)
            }
        }
        return safe.toString()
    }

    // ------------------------------------------------------------------ reading

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

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val parser = newParser(bytes)
        val grid: SortedMap<Int, SortedMap<Int, String>> = TreeMap()
        var rowNumber = 0
        var cellColumn = -1
        var cellType: String? = null
        var capture = false
        val text = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        rowNumber = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowNumber + 1)
                        cellColumn = -1
                    }
                    "c" -> {
                        val reference = parser.getAttributeValue(null, "r")
                        cellColumn = if (reference != null) columnIndex(reference) else cellColumn + 1
                        cellType = parser.getAttributeValue(null, "t")
                        text.setLength(0)
                    }
                    "v", "t" -> capture = true
                    "rPh" -> skip(parser)
                }
                XmlPullParser.TEXT -> if (capture) text.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> capture = false
                    "c" -> {
                        val raw = text.toString()
                        val value = if (cellType == "s") shared.getOrNull(raw.trim().toIntOrNull() ?: -1).orEmpty() else raw
                        if (value.isNotEmpty() && cellColumn >= 0) {
                            grid.getOrPut(rowNumber) { TreeMap() }[cellColumn] = value
                        }
                    }
                }
            }
            event = parser.next()
        }
        if (grid.isEmpty()) return emptyList()
        return (1..grid.lastKey()).map { row ->
            val cells = grid[row] ?: return@map emptyList()
            (0..cells.lastKey()).map { column -> cells[column].orEmpty() }
        }
    }

    private fun newParser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply { setInput(bytes.inputStream(), "UTF-8") }

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

    // ------------------------------------------------------------------ fixed parts

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Deneme" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
