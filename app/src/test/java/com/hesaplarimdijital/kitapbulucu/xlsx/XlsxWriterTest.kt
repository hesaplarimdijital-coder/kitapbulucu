package com.hesaplarimdijital.kitapbulucu.xlsx

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipInputStream

/** Needs Robolectric: reading back goes through the Android XML parser. */
@RunWith(AndroidJUnit4::class)
class XlsxWriterTest {

    private val header = listOf("Kitap Adı", "Yazar", "Raf", "Adet", "Not")

    private fun parts(bytes: ByteArray): Map<String, String> =
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.associate { it.name to zip.readBytes().toString(Charsets.UTF_8) }
        }

    @Test
    fun everyKindOfTextSurvivesTheRoundTrip() {
        val rows = listOf(
            listOf("Islıklı Kuşların Sokağı", "Nevin Çağlar", "A1", "1", "Şükrü & Gülşen <İğde> \"ışık\" 'şapka' hâlâ"),
            listOf("=Formül Sanılan Kitap", "Deniz Yalın", "3-4", "2", "Nis.53"),              // Excel would mangle these if they were not text
            listOf(" baştaki ve sondaki boşluk ", "  iki  boşluk", "", "", "satır\niçinde\tsekme"),
            listOf("9786050000017", "0012", "007", "3", "1E3"),                               // digits stay digits
            listOf("Emoji 📚 ve 𝔘nicode", "Ağaoğlu", "B2", "abc", "yüz\u0007bir"),              // a surrogate pair stays, a bell is dropped
        )
        val expected = rows.map { row -> row.map { it.replace("\u0007", "") } }
        val bytes = XlsxWriter.write(listOf(SheetSpec("Babam", header, rows, numericColumns = setOf(3))))
        val sheet = XlsxReader.read(bytes.inputStream()).sheets.single()
        assertEquals("Babam", sheet.name)
        assertEquals(header, sheet.rows[0])
        assertEquals(expected, sheet.rows.drop(1))
    }

    @Test
    fun writesTwoSheetsForTheSharedFile() {
        val bytes = XlsxWriter.write(
            listOf(
                SheetSpec("Babam", header, listOf(listOf("Sefil Fenerciler", "Halit Oruç", "A1", "2", ""))),
                SheetSpec("Benim", header, listOf(listOf("Çatıdaki Rasathane", "Ayşe İnce", "C2", "1", "Kapak yırtık"))),
            ),
        )
        val workbook = XlsxReader.read(bytes.inputStream())
        assertEquals(listOf("Babam", "Benim"), workbook.sheets.map { it.name })
        assertEquals("Sefil Fenerciler", workbook.sheets[0].rows[1][0])
        assertEquals("Kapak yırtık", workbook.sheets[1].rows[1][4])
        val parts = parts(bytes)
        assertTrue(parts.getValue("[Content_Types].xml").contains("/xl/worksheets/sheet2.xml"))
        assertTrue(parts.getValue("xl/_rels/workbook.xml.rels").contains("worksheets/sheet2.xml"))
    }

    @Test
    fun headerIsBoldAndFrozenAndCellsAreTextFormatted() {
        val bytes = XlsxWriter.write(listOf(SheetSpec("Babam", header, listOf(listOf("Sefil Fenerciler", "", "A1", "2")), numericColumns = setOf(3), columnWidths = listOf(44.0, 26.0))))
        val parts = parts(bytes)
        val styles = parts.getValue("xl/styles.xml")
        val cellStyles = styles.substringAfter("<cellXfs").substringBefore("</cellXfs>")
        val xfs = Regex("<xf [^>]*/>").findAll(cellStyles).map { it.value }.toList()
        assertEquals(3, xfs.size)
        assertTrue(xfs[0].contains("numFmtId=\"49\""))                        // text
        assertTrue(xfs[1].contains("numFmtId=\"49\"") && xfs[1].contains("fontId=\"1\"")) // bold text
        assertTrue(xfs[2].contains("numFmtId=\"1\""))                         // whole number
        assertTrue(styles.substringAfter("<fonts").contains("<font><b/>"))

        val sheet = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>"""))
        assertTrue(sheet.contains("""<col min="1" max="1" width="44" customWidth="1" style="0"/>"""))
        assertTrue(sheet.contains("""<col min="3" max="3" width="14" customWidth="1" style="0"/>"""))
        assertTrue(sheet.contains("""<col min="4" max="4" width="14" customWidth="1" style="2"/>"""))
        assertTrue(sheet.contains("""<c r="A1" s="1" t="inlineStr"><is><t xml:space="preserve">Kitap Adı</t></is></c>"""))
        assertTrue(sheet.contains("""<c r="A2" s="0" t="inlineStr"><is><t xml:space="preserve">Sefil Fenerciler</t></is></c>"""))
        assertTrue(sheet.contains("""<c r="B2" s="0"/>"""))                     // an empty cell keeps the text format
        assertTrue(sheet.contains("""<c r="D2" s="2"><v>2</v></c>"""))
        assertTrue(sheet.contains("""<c r="E2" s="0"/>"""))                     // a missing trailing cell is written too
        assertTrue(sheet.contains("""<dimension ref="A1:E2"/>"""))
        assertFalse(sheet.contains("t=\"s\""))                                  // no shared strings anywhere
    }

    @Test
    fun numericColumnFallsBackToTextForOddValues() {
        val bytes = XlsxWriter.write(listOf(SheetSpec("Babam", header, listOf(listOf("a", "b", "c", " 7 ", ""), listOf("a", "b", "c", "yedi", ""), listOf("a", "b", "c", "", "")), numericColumns = setOf(3))))
        val sheet = parts(bytes).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("""<c r="D2" s="2"><v>7</v></c>"""))
        assertTrue(sheet.contains("""<c r="D3" s="0" t="inlineStr"><is><t xml:space="preserve">yedi</t></is></c>"""))
        assertTrue(sheet.contains("""<c r="D4" s="2"/>"""))
        assertEquals(listOf("a", "b", "c", "7"), XlsxReader.read(bytes.inputStream()).sheets.single().rows[1])
    }

    @Test
    fun sheetNamesAreMadeLegal() {
        assertEquals("Babam", XlsxWriter.sheetName("Babam", 0))
        assertEquals("Babam Benim", XlsxWriter.sheetName("Babam / Benim", 0))
        assertEquals("Sayfa2", XlsxWriter.sheetName("  ", 1))
        assertEquals(31, XlsxWriter.sheetName("Çok uzun bir sayfa adı ki Excel otuz bir karakterde keser", 0).length)
        val read = XlsxReader.read(XlsxWriter.write(listOf(SheetSpec("Liste [2026]: *yeni*", header, emptyList()))).inputStream())
        assertEquals("Liste 2026 yeni", read.sheets.single().name)
    }

    @Test
    fun escapesWhatXmlCannotCarry() {
        assertEquals("Kuyu &amp; Kalem &lt;b&gt; &quot;a&quot;", XmlText.escape("Kuyu & Kalem <b> \"a\""))
        assertEquals("tab\tnew\nline\r", XmlText.escape("tab\tnew\nline\r"))
        assertEquals("ab", XmlText.escape("a\u0000\u0001\u001Fb￾￿"))
        assertEquals("📚", XmlText.escape("📚"))
        assertEquals("ab", XmlText.escape("a\uD83Db"))     // a lone high surrogate is dropped
        assertEquals("ab", XmlText.escape("a\uDC00b"))     // a lone low surrogate is dropped
        assertEquals("", XmlText.escape(""))
    }

    @Test
    fun aThousandRowsWriteAndReadBackIntact() {
        val rows = (1..1000).map { listOf("Kitap $it", "Yazar $it", "R${it % 30}", "${it % 4}", if (it % 2 == 0) "çift" else "tek") }
        val bytes = XlsxWriter.write(listOf(SheetSpec("Babam", header, rows, numericColumns = setOf(3))))
        val sheet = XlsxReader.read(bytes.inputStream()).sheets.single()
        assertEquals(1001, sheet.rows.size)
        assertEquals(rows, sheet.rows.drop(1))
    }
}
