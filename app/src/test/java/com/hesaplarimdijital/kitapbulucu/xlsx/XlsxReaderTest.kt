package com.hesaplarimdijital.kitapbulucu.xlsx

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Needs Robolectric: the XML parser comes from the Android runtime. */
@RunWith(AndroidJUnit4::class)
class XlsxReaderTest {

    /** What the hand-written "Kitaplar" sheet (ornek-uc-durumlar / ornek-onekli) must read as. */
    private val handMadeRows = listOf(
        listOf("Kitap Adı", "Yazar", "Raf", "Adet", "Tarih"),
        listOf("Kürk Mantolu Madonna", "Sabahattin Ali", "B3", "1", "NİSAN 1953"), // rich shared string, mmm/yy
        listOf(" boşluklu ", "", "Tokyo", "2", "15 MART 1998"),                   // gap, phonetic hint skipped, 2.0, dd/mmm/yy
        emptyList<String>(),                                                               // row 4 missing
        listOf("Formül sonucu", "DOĞRU", "", "9789750718533", "1000"),            // formula result, boolean, error, long and 1E3
        listOf("A6 adressiz", "0.5"),                                              // row and cells without addresses
        listOf("1 ARALIK 2016", "12:00", "", "1.5", "1998"),                      // built-in 14, h:mm, empty v, 1.50, "Yıl: "0
        listOf("  iki boşlukla başlar", "satır içi zengin", "YANLIŞ"),            // leading spaces, rich inline, false
    )

    private fun read(name: String): Workbook = XlsxReader.read(TestWorkbooks.fixture(name).inputStream())

    @Test
    fun readsEveryCellKindInTheHandMadeWorkbook() {
        val workbook = read("ornek-uc-durumlar.xlsx")
        assertEquals(listOf("Kitaplar", "Boş", "Diğer"), workbook.sheets.map { it.name })
        assertEquals(handMadeRows, workbook.sheets[0].rows)
        assertTrue(workbook.sheets[1].isEmpty)
        assertEquals(emptyList<List<String>>(), workbook.sheets[1].rows)
        assertEquals(listOf(emptyList<String>(), emptyList<String>(), listOf("", "Üçüncü sayfa")), workbook.sheets[2].rows)
        assertEquals(listOf("Kitaplar", "Diğer"), workbook.filledSheets.map { it.name })
    }

    @Test
    fun readsPrefixedTagsAndAbsoluteSheetTargets() {
        val workbook = read("ornek-onekli.xlsx")
        assertEquals(listOf("Kitaplar", "Diğer"), workbook.sheets.map { it.name })
        assertEquals(handMadeRows, workbook.sheets[0].rows)
        assertEquals(listOf(emptyList<String>(), emptyList<String>(), listOf("", "Üçüncü sayfa")), workbook.sheets[1].rows)
    }

    @Test
    fun honoursThe1904DateSystem() {
        val workbook = read("ornek-1904.xlsx")
        assertEquals(listOf(listOf("1 OCAK 1904", "NİSAN 1953")), workbook.sheets.single().rows)
    }

    @Test
    fun readsTheAppLayoutWrittenByOpenpyxl() {
        val sheet = read("ornek-kutuphane.xlsx").sheets.single()
        assertEquals("Babam", sheet.name)
        assertEquals(13, sheet.rows.size)
        assertEquals(listOf("Kitap Adı", "Yazar", "Raf", "Adet", "İmzalı", "Yayınevi", "Yıl", "ISBN", "Not"), sheet.rows[0])
        assertEquals(listOf("Islıklı Kuşların Sokağı", "Nevin Çağlar", "A1", "1", "", "Mavi Defter", "2011", "9786050000017"), sheet.rows[1])
        assertEquals(listOf("Sefil Fenerciler", "Halit Oruç", "A1", "2"), sheet.rows[2])
        assertEquals("Evet", sheet.rows[3][4])
        assertEquals("Yazarın kendi el yazısıyla", sheet.rows[3][8])
        assertEquals("Dağ Başındaki <Sessizlik>", sheet.rows[7][0])
        assertEquals("3-4", sheet.rows[8][2])
        assertEquals("=Formül Sanılan Kitap", sheet.rows[10][0])
        assertEquals("Uzun Bir Adı Olan ve Satır Sonuna Kadar Giden, Bitmek Bilmeyen Kitabın Adı", sheet.rows[11][0])
        assertEquals(listOf("Kâtip Çelebi'nin Defteri", "", "E1", "1", "", "", "1950", "", "Yazarı bilinmiyor"), sheet.rows[12])
    }

    @Test
    fun readsTheOldHandKeptLayout() {
        val workbook = read("ornek-eski-bicim.xlsx")
        assertEquals(listOf("Sayfa1", "Sayfa2"), workbook.sheets.map { it.name })
        assertEquals(1, workbook.filledSheets.size)
        val rows = workbook.sheets[0].rows
        assertEquals(9, rows.size)
        assertEquals(listOf("ISLIKLI KUŞLARIN SOKAĞI", "NEVİN ÇAĞLAR"), rows[0])
        assertEquals("SEFİL FENERCİLER ", rows[1][0]) // stray spaces survive reading; cleaning is a later step
        assertEquals(listOf("TARLA DERGİSİ SAYI 1", "NİSAN 1953"), rows[4]) // the cell Excel turned into a date
        assertEquals(listOf("KİTAP", "YAZAR"), rows[5])
        assertEquals(listOf("YAZARSIZ BİR ALMANAK"), rows[8])
    }

    @Test
    fun readsAWorkbookSavedByLibreOffice() {
        val workbook = read("ornek-libreoffice.xlsx")
        assertEquals(listOf("Kitaplar", "Notlar"), workbook.sheets.map { it.name })
        assertEquals(
            listOf(
                listOf("Kitap Adı", "Yazar", "Raf", "Adet", "Yıl", "Tarih", "Not"),
                listOf("Islıklı Kuşların Sokağı", "Nevin Çağlar", "A1", "1", "2011", "15 MART 1998", "Çatı katı; ikinci baskı"),
                listOf("Dağ \"Sessizlik\"", "Ferit Ağaoğlu", "B2", "2", "1953", "NİSAN 1953"),
                listOf("Hâlâ Bekleyen Mektup", "Şükrü Ilgaz", "C3", "1", "", "", "İmzalı"),
                listOf("Toplam", "", "", "4"), // the formula's cached result
            ),
            workbook.sheets[0].rows,
        )
        assertEquals(listOf(emptyList<String>(), listOf("", "İkinci sayfa")), workbook.sheets[1].rows)
    }

    @Test
    fun numbersKeepTheirDigitsAndLoseExcelNoise() {
        val sheet = TestWorkbooks.workbook(
            TestWorkbooks.row(
                1,
                TestWorkbooks.number("A1", "1") + TestWorkbooks.number("B1", "2.0") + TestWorkbooks.number("C1", "1.50") +
                    TestWorkbooks.number("D1", "9789750718533") + TestWorkbooks.number("E1", "1E3") + TestWorkbooks.number("F1", "-0.25") +
                    TestWorkbooks.number("G1", "123456789012345678") + TestWorkbooks.number("H1", "0") + TestWorkbooks.number("I1", "abc"),
            ),
        )
        assertEquals(
            listOf(listOf("1", "2", "1.5", "9789750718533", "1000", "-0.25", "123456789012345678", "0", "abc")),
            XlsxReader.read(sheet.inputStream()).sheets.single().rows,
        )
    }

    @Test
    fun dateStyledNumbersBecomeTurkishText() {
        fun cell(value: String, format: String): String {
            val sheet = TestWorkbooks.workbook(TestWorkbooks.row(1, TestWorkbooks.number("A1", value, style = 1)), stylesXml = TestWorkbooks.styles(format))
            return XlsxReader.read(sheet.inputStream()).sheets.single().rows[0][0]
        }
        assertEquals("15 MART 1998", cell("35869", "dd/mm/yyyy;@"))
        assertEquals("15 MART 1998", cell("35869", "[\$-41F]d mmmm yyyy"))
        assertEquals("NİSAN 1953", cell("19450", "mmm/yy"))
        assertEquals("1998", cell("35869", "yyyy"))
        assertEquals("14:05", cell("0.5868", "h:mm"))
        assertEquals("35869", cell("35869", "General"))
        assertEquals("35869", cell("35869", "0"))
        assertEquals("1998", cell("1998", "\"Yıl: \"0"))
    }

    @Test
    fun sharedStringsAndGapsLineUp() {
        val sheet = TestWorkbooks.workbook(
            TestWorkbooks.row(2, TestWorkbooks.shared("B2", 1) + TestWorkbooks.shared("D2", 0)) +
                TestWorkbooks.row(4, TestWorkbooks.inline("A4", "<İğne> & \"ışık\"")),
            sharedStrings = listOf("ilk", "ikinci"),
        )
        assertEquals(
            listOf(emptyList<String>(), listOf("", "ikinci", "", "ilk"), emptyList<String>(), listOf("<İğne> & \"ışık\"")),
            XlsxReader.read(sheet.inputStream()).sheets.single().rows,
        )
    }

    @Test
    fun aCutOffWorkbookIsReportedAsDamaged() {
        assertReason(SpreadsheetException.Reason.DAMAGED) { read("bozuk.xlsx") }
        val whole = TestWorkbooks.fixture("ornek-libreoffice.xlsx")
        assertReason(SpreadsheetException.Reason.DAMAGED) { XlsxReader.read(whole.copyOf(whole.size / 3).inputStream()) }
    }

    @Test
    fun aZipWithoutAWorkbookIsNotASpreadsheet() {
        assertReason(SpreadsheetException.Reason.NOT_A_SPREADSHEET) { read("belge.zip") }
    }

    @Test
    fun malformedXmlIsReportedAsDamaged() {
        val sheet = TestWorkbooks.workbook("<row r=\"1\"><c r=\"A1\"><v>1</v></row>")
        assertReason(SpreadsheetException.Reason.DAMAGED) { XlsxReader.read(sheet.inputStream()) }
    }

    @Test
    fun tenThousandRowsReadInUnderASecond() {
        val sheetData = StringBuilder()
        for (r in 1..10_000) {
            sheetData.append(
                TestWorkbooks.row(
                    r,
                    TestWorkbooks.inline("A$r", "Kitap adı $r: Çatıdaki Rasathane") + TestWorkbooks.inline("B$r", "Yazar Şükrü $r") +
                        TestWorkbooks.inline("C$r", "R${r % 40}") + TestWorkbooks.number("D$r", "${1 + r % 3}") +
                        TestWorkbooks.inline("E$r", if (r % 7 == 0) "Evet" else "") + TestWorkbooks.inline("F$r", "Yayınevi $r") +
                        TestWorkbooks.inline("G$r", "${1950 + r % 70}") + TestWorkbooks.number("H$r", "${9789750000000L + r}") +
                        TestWorkbooks.inline("I$r", "Not $r"),
                ),
            )
        }
        val bytes = TestWorkbooks.workbook(sheetData.toString())
        XlsxReader.read(bytes.inputStream()) // warm up the parser classes once
        val start = System.nanoTime()
        val workbook = XlsxReader.read(bytes.inputStream())
        val millis = (System.nanoTime() - start) / 1_000_000
        assertEquals(10_000, workbook.sheets.single().rows.size)
        assertEquals(listOf("Kitap adı 10000: Çatıdaki Rasathane", "Yazar Şükrü 10000", "R0", "2", "", "Yayınevi 10000", "2000", "9789750010000", "Not 10000"), workbook.sheets.single().rows[9_999])
        assertTrue("10,000 rows took $millis ms", millis < 1_000)
    }

    @Test
    fun cellReferencesRoundTrip() {
        assertEquals(0, CellReferences.columnIndex("A1"))
        assertEquals(2, CellReferences.columnIndex("C7"))
        assertEquals(26, CellReferences.columnIndex("AA1"))
        assertEquals(701, CellReferences.columnIndex("ZZ3"))
        for (index in listOf(0, 1, 25, 26, 27, 51, 52, 701, 702, 16383)) {
            assertEquals(index, CellReferences.columnIndex(CellReferences.columnName(index) + "9"))
        }
        assertEquals("A", CellReferences.columnName(0))
        assertEquals("Z", CellReferences.columnName(25))
        assertEquals("AA", CellReferences.columnName(26))
        assertEquals("XFD", CellReferences.columnName(16383))
    }

    private fun assertReason(reason: SpreadsheetException.Reason, block: () -> Unit) {
        try {
            block()
            fail("expected $reason")
        } catch (e: SpreadsheetException) {
            assertEquals(reason, e.reason)
        }
    }
}
