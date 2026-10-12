package com.hesaplarimdijital.kitapbulucu.xlsx

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds the smallest .xlsx that holds a given sheet, for tests that need odd cells quickly. */
object TestWorkbooks {
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun fixture(name: String): ByteArray =
        TestWorkbooks::class.java.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }

    fun workbook(sheetData: String, sharedStrings: List<String> = emptyList(), stylesXml: String? = null): ByteArray {
        val parts = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$REL/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$NS" xmlns:r="$REL"><sheets><sheet name="Deneme" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$REL/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="$NS"><sheetData>$sheetData</sheetData></worksheet>""",
        )
        if (sharedStrings.isNotEmpty()) {
            parts["xl/sharedStrings.xml"] = """<?xml version="1.0" encoding="UTF-8"?><sst xmlns="$NS">""" +
                sharedStrings.joinToString("") { "<si><t xml:space=\"preserve\">${escape(it)}</t></si>" } + "</sst>"
        }
        if (stylesXml != null) parts["xl/styles.xml"] = stylesXml
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, xml) in parts) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** A styles part whose cell style 1 shows [formatCode]; style 0 is plain. */
    fun styles(formatCode: String): String = """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="$NS">""" +
        """<numFmts count="1"><numFmt numFmtId="164" formatCode="${escape(formatCode)}"/></numFmts>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0"/></cellStyleXfs>""" +
        """<cellXfs count="2"><xf numFmtId="0"/><xf numFmtId="164" applyNumberFormat="1"/></cellXfs></styleSheet>"""

    fun inline(reference: String, text: String): String = """<c r="$reference" t="inlineStr"><is><t xml:space="preserve">${escape(text)}</t></is></c>"""
    fun number(reference: String, value: String, style: Int = 0): String = """<c r="$reference" s="$style"><v>$value</v></c>"""
    fun shared(reference: String, index: Int): String = """<c r="$reference" t="s"><v>$index</v></c>"""
    fun row(number: Int, cells: String): String = """<row r="$number">$cells</row>"""

    fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
