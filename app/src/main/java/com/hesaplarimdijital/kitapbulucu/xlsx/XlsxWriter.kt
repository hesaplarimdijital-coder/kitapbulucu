package com.hesaplarimdijital.kitapbulucu.xlsx

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * One sheet to write. Every value is text; a column listed in [numericColumns] is written as a
 * number when its text is a whole number (the book count), as text otherwise.
 * [columnWidths] are in characters, one per header column; missing entries get a default.
 */
class SheetSpec(
    val name: String,
    val header: List<String>,
    val rows: List<List<String>>,
    val numericColumns: Set<Int> = emptySet(),
    val columnWidths: List<Double> = emptyList(),
)

/**
 * Writes an .xlsx workbook with nothing but java.util.zip: inline strings, a bold frozen header
 * row, every text cell (and every empty cell of a text column) in the text format `@`, so Excel
 * never turns "3-4" into a date or an ISBN into scientific notation when the file is edited later.
 */
object XlsxWriter {
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val DEFAULT_WIDTH = 14.0

    /** Cell style indexes in styles.xml. */
    private const val STYLE_TEXT = 0
    private const val STYLE_HEADER = 1
    private const val STYLE_NUMBER = 2

    fun write(sheets: List<SheetSpec>): ByteArray {
        val out = ByteArrayOutputStream()
        write(sheets, out)
        return out.toByteArray()
    }

    fun write(sheets: List<SheetSpec>, output: OutputStream) {
        require(sheets.isNotEmpty()) { "A workbook needs at least one sheet" }
        ZipOutputStream(output).use { zip ->
            fun part(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            part("[Content_Types].xml", contentTypes(sheets.size))
            part("_rels/.rels", rootRelationships())
            part("xl/workbook.xml", workbook(sheets))
            part("xl/_rels/workbook.xml.rels", workbookRelationships(sheets.size))
            part("xl/styles.xml", styles())
            sheets.forEachIndexed { index, sheet -> part("xl/worksheets/sheet${index + 1}.xml", worksheet(sheet)) }
        }
    }

    // ------------------------------------------------------------------ package parts

    private fun contentTypes(sheetCount: Int): String = buildString {
        append(XML_HEADER)
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun rootRelationships(): String = XML_HEADER +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="$REL/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbook(sheets: List<SheetSpec>): String = buildString {
        append(XML_HEADER)
        append("""<workbook xmlns="$NS" xmlns:r="$REL"><sheets>""")
        sheets.forEachIndexed { index, sheet ->
            append("""<sheet name="${XmlText.escape(sheetName(sheet.name, index))}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRelationships(sheetCount: Int): String = buildString {
        append(XML_HEADER)
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) append("""<Relationship Id="rId$i" Type="$REL/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${sheetCount + 1}" Type="$REL/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    /** Style 0: text. Style 1: bold text (header). Style 2: whole number. */
    private fun styles(): String = XML_HEADER +
        """<styleSheet xmlns="$NS">""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/><family val="2"/></font><font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font></fonts>""" +
        """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="3">""" +
        """<xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="49" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>""" +
        """<xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """</cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        """</styleSheet>"""

    // ------------------------------------------------------------------ worksheet

    private fun worksheet(sheet: SheetSpec): String {
        val columnCount = sheet.header.size
        require(columnCount > 0) { "Sheet ${sheet.name} has no columns" }
        val lastRow = sheet.rows.size + 1
        val sb = StringBuilder(64 + sheet.rows.size * columnCount * 48)
        sb.append(XML_HEADER)
        sb.append("""<worksheet xmlns="$NS" xmlns:r="$REL">""")
        sb.append("""<dimension ref="A1:${CellReferences.columnName(columnCount - 1)}$lastRow"/>""")
        // the header row stays visible while scrolling
        sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/><selection pane="bottomLeft" activeCell="A2" sqref="A2"/></sheetView></sheetViews>""")
        sb.append("""<sheetFormatPr defaultRowHeight="15"/>""")
        sb.append("<cols>")
        for (column in 0 until columnCount) {
            val width = sheet.columnWidths.getOrNull(column) ?: DEFAULT_WIDTH
            val style = if (column in sheet.numericColumns) STYLE_NUMBER else STYLE_TEXT
            // the column style makes cells typed in later text cells too
            sb.append("""<col min="${column + 1}" max="${column + 1}" width="${formatWidth(width)}" customWidth="1" style="$style"/>""")
        }
        sb.append("</cols>")
        sb.append("<sheetData>")
        sb.append("""<row r="1">""")
        sheet.header.forEachIndexed { column, text -> appendTextCell(sb, 1, column, text, STYLE_HEADER) }
        sb.append("</row>")
        sheet.rows.forEachIndexed { index, row ->
            val rowNumber = index + 2
            sb.append("""<row r="$rowNumber">""")
            for (column in 0 until columnCount) {
                val text = row.getOrNull(column).orEmpty()
                if (column in sheet.numericColumns) appendNumberCell(sb, rowNumber, column, text) else appendTextCell(sb, rowNumber, column, text, STYLE_TEXT)
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        sb.append("""<pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun appendTextCell(sb: StringBuilder, row: Int, column: Int, text: String, style: Int) {
        val reference = CellReferences.columnName(column) + row
        if (text.isEmpty()) {
            sb.append("""<c r="$reference" s="$style"/>""") // keeps the text format on the empty cell
        } else {
            sb.append("""<c r="$reference" s="$style" t="inlineStr"><is><t xml:space="preserve">""")
            sb.append(XmlText.escape(text))
            sb.append("</t></is></c>")
        }
    }

    private fun appendNumberCell(sb: StringBuilder, row: Int, column: Int, text: String) {
        val trimmed = text.trim()
        val number = trimmed.toLongOrNull()
        when {
            trimmed.isEmpty() -> sb.append("""<c r="${CellReferences.columnName(column)}$row" s="$STYLE_NUMBER"/>""")
            number != null -> sb.append("""<c r="${CellReferences.columnName(column)}$row" s="$STYLE_NUMBER"><v>$number</v></c>""")
            else -> appendTextCell(sb, row, column, text, STYLE_TEXT)
        }
    }

    /** Excel allows 31 characters and none of  : \ / ? * [ ]  in a sheet name. */
    fun sheetName(name: String, index: Int): String {
        val cleaned = name.map { if (it in ":\\/?*[]" || it.isISOControl()) ' ' else it }.joinToString("")
            .replace(DOUBLE_SPACES, " ").trim().take(31).trim()
        return cleaned.ifEmpty { "Sayfa${index + 1}" }
    }

    private val DOUBLE_SPACES = Regex(" {2,}")

    private fun formatWidth(width: Double): String = if (width == Math.floor(width)) width.toInt().toString() else width.toString()

    private const val XML_HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
}

object XmlText {
    /** Escapes `& < > "` and drops the control characters XML 1.0 cannot carry (tab, LF and CR stay). */
    fun escape(text: String): String {
        val sb = StringBuilder(text.length + 8)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(ch)
                ch < ' ' || ch == '￾' || ch == '￿' -> Unit
                Character.isHighSurrogate(ch) -> {
                    if (i + 1 < text.length && Character.isLowSurrogate(text[i + 1])) {
                        sb.append(ch).append(text[i + 1])
                        i++
                    }
                }
                Character.isLowSurrogate(ch) -> Unit // a lone half of a pair
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }
}
