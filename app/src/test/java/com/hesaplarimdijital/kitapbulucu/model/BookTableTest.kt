package com.hesaplarimdijital.kitapbulucu.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.xlsx.Sheet
import com.hesaplarimdijital.kitapbulucu.xlsx.TestWorkbooks
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxReader
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Needs Robolectric for the workbooks it reads and writes. */
@RunWith(AndroidJUnit4::class)
class BookTableTest {

    @Test
    fun readsTheAppLayoutIntoBooks() {
        val sheet = XlsxReader.read(TestWorkbooks.fixture("ornek-kutuphane.xlsx").inputStream()).sheets.single()
        val parsed = BookTable.parse(sheet)!!
        assertEquals(0, parsed.headerRow)
        assertEquals(emptyList<BookTable.Problem>(), parsed.problems)
        assertEquals(emptyList<String>(), parsed.table.extraHeaders)
        val books = parsed.table.books
        assertEquals(12, books.size)
        assertEquals(Book("Islıklı Kuşların Sokağı", "Nevin Çağlar", "A1", 1, false, "Mavi Defter", "2011", "9786050000017", ""), books[0])
        assertEquals(Book("Sefil Fenerciler", "Halit Oruç", "A1", 2), books[1])
        assertEquals(Book("Hâlâ Bekleyen Mektup", "Şükrü Ilgaz", "A2", 1, true, "Kumsal", "1998", "", "Yazarın kendi el yazısıyla"), books[2])
        assertEquals(Book("Çatıdaki Rasathane", "Ayşe İnce", "C2", 3), books[5])
        assertEquals(Book("Öğle Uykusu Üzerine Denemeler", "Işıl Öztürk", "3-4"), books[7])
        assertEquals(Book("=Formül Sanılan Kitap", "Deniz Yalın"), books[9])
        assertEquals(Book("Kâtip Çelebi'nin Defteri", "", "E1", 1, false, "", "1950", "", "Yazarı bilinmiyor"), books[11])
    }

    @Test
    fun recognisesHeaderVariantsAndKeepsUnknownColumns() {
        val sheet = Sheet(
            "Liste",
            listOf(
                listOf("Babamın kitapları", "", "", ""),                                  // a title line above the header
                listOf(),
                listOf("KİTAP ADI", "yazar", "Raf No", "Sayı", "İmza", "Yayın Evi", "Basım Yılı", "ISBN", "Notlar", "Dil", "", "Durum"),
                listOf("Sefil Fenerciler", "Halit Oruç", "A1", "2", "", "", "", "", "", "Türkçe", "", "iyi"),
                listOf("Çatıdaki  Rasathane ", " Ayşe İnce", "C2", "", "evet", "Kumsal", "1998", "9786050000017", "Kapak yırtık", "", "", ""),
                listOf("", "", "", "", "", "", "", "", "", "", "", ""),
                listOf("Yalnız Not", "", "", "", "", "", "", "", "", "", "", "kötü"),
            ),
        )
        val parsed = BookTable.parse(sheet)!!
        assertEquals(2, parsed.headerRow)
        assertEquals(listOf("Dil", "Durum"), parsed.table.extraHeaders) // the blank column with nothing in it is dropped
        assertEquals(
            listOf(
                Book("Sefil Fenerciler", "Halit Oruç", "A1", 2, extra = listOf("Türkçe", "iyi")),
                Book("Çatıdaki Rasathane", "Ayşe İnce", "C2", 1, true, "Kumsal", "1998", "9786050000017", "Kapak yırtık", extra = listOf("", "")),
                Book("Yalnız Not", extra = listOf("", "kötü")),
            ),
            parsed.table.books,
        )
    }

    @Test
    fun blankHeaderColumnsWithContentAreKept() {
        val sheet = Sheet("Liste", listOf(listOf("Kitap Adı", "Yazar", ""), listOf("Sefil Fenerciler", "Halit Oruç", "ödünç")))
        val parsed = BookTable.parse(sheet)!!
        assertEquals(listOf(""), parsed.table.extraHeaders)
        assertEquals(listOf("ödünç"), parsed.table.books.single().extra)
    }

    @Test
    fun aSheetWithoutAHeaderIsNotATable() {
        assertNull(BookTable.parse(Sheet("Eski", listOf(listOf("SEFİL FENERCİLER", "HALİT ORUÇ"), listOf("ÇATIDAKİ RASATHANE", "AYŞE İNCE")))))
        assertNull(BookTable.parse(Sheet("Boş", emptyList())))
        assertNull(BookTable.parse(Sheet("Tek", listOf(listOf("Kitap Adı"), listOf("Sefil Fenerciler"))))) // one known column is not enough
    }

    @Test
    fun countsAndSignedMarksAreForgiving() {
        val sheet = Sheet(
            "Liste",
            listOf(
                listOf("Kitap Adı", "Yazar", "Adet", "İmzalı"),
                listOf("a", "x", "", ""),
                listOf("b", "x", " 3 ", "Evet"),
                listOf("c", "x", "0", "x"),
                listOf("d", "x", "iki", "var"),
                listOf("e", "x", "2", "hayır"),
                listOf("f", "x", "-1", "İMZALI"),
                listOf("g", "x", "1", "1"),
            ),
        )
        val parsed = BookTable.parse(sheet)!!
        assertEquals(listOf(1, 3, 1, 1, 2, 1, 1), parsed.table.books.map { it.copies })
        assertEquals(listOf(false, true, true, true, false, true, true), parsed.table.books.map { it.signed })
        assertEquals(
            listOf(BookTable.Problem(4, BookColumn.COPIES, "0"), BookTable.Problem(5, BookColumn.COPIES, "iki"), BookTable.Problem(7, BookColumn.COPIES, "-1")),
            parsed.problems,
        )
    }

    @Test
    fun matchesHeadersLoosely() {
        assertEquals(BookColumn.TITLE, BookColumn.match("  kitap   adı "))
        assertEquals(BookColumn.TITLE, BookColumn.match("KİTABIN ADI"))
        assertEquals(BookColumn.AUTHOR, BookColumn.match("Yazarı"))
        assertEquals(BookColumn.SHELF, BookColumn.match("RAF"))
        assertEquals(BookColumn.COPIES, BookColumn.match("Adet"))
        assertEquals(BookColumn.SIGNED, BookColumn.match("imzali"))
        assertEquals(BookColumn.PUBLISHER, BookColumn.match("Yayınevi"))
        assertEquals(BookColumn.YEAR, BookColumn.match("Yıl"))
        assertEquals(BookColumn.ISBN, BookColumn.match("Isbn"))
        assertEquals(BookColumn.NOTE, BookColumn.match("Not"))
        assertNull(BookColumn.match("Dil"))
        assertNull(BookColumn.match(""))
        assertNull(BookColumn.match("Kitap Adı ve Yazarı"))
    }

    @Test
    fun writesAndReadsBackTheSameBooks() {
        val table = BookTable(
            listOf(
                Book("Islıklı Kuşların Sokağı", "Nevin Çağlar", "A1", 1, false, "Mavi Defter", "2011", "9786050000017", "", listOf("Türkçe", "")),
                Book("=Formül Sanılan Kitap", "Deniz Yalın", "3-4", 2, true, "Kuyu & Kalem", "Nis.53", "007", "\"Tırnak\" <ve> & işaret", listOf("", "ödünç")),
                Book("Kâtip Çelebi'nin Defteri", "", "", 1, false, "", "", "", "Yazarı bilinmiyor", listOf("", "")),
            ),
            listOf("Dil", "Durum"),
        )
        val bytes = XlsxWriter.write(listOf(table.toSheetSpec("Babam")))
        val sheet = XlsxReader.read(bytes.inputStream()).sheets.single()
        assertEquals(BookTable.HEADERS + listOf("Dil", "Durum"), sheet.rows[0])
        assertEquals(listOf("=Formül Sanılan Kitap", "Deniz Yalın", "3-4", "2", "Evet", "Kuyu & Kalem", "Nis.53", "007", "\"Tırnak\" <ve> & işaret", "", "ödünç"), sheet.rows[2])
        val parsed = BookTable.parse(sheet)!!
        assertEquals(table.extraHeaders, parsed.table.extraHeaders)
        assertEquals(table.books, parsed.table.books)
        assertTrue(parsed.problems.isEmpty())
    }

    @Test
    fun theSharedFileHoldsBothLibraries() {
        val dad = BookTable(listOf(Book("Sefil Fenerciler", "Halit Oruç", "A1", 2)))
        val me = BookTable(listOf(Book("Çatıdaki Rasathane", "Ayşe İnce", "C2")))
        val bytes = XlsxWriter.write(listOf(dad.toSheetSpec(Owner.DAD.exportSheet), me.toSheetSpec(Owner.ME.exportSheet)))
        val workbook = XlsxReader.read(bytes.inputStream())
        assertEquals(listOf("MKE", "Barış"), workbook.sheets.map { it.name })
        assertEquals(dad.books, BookTable.parse(workbook.sheets[0])!!.table.books)
        assertEquals(me.books, BookTable.parse(workbook.sheets[1])!!.table.books)
    }

    @Test
    fun blankBooksAreBlankWhateverTheirCount() {
        assertTrue(Book("", copies = 3).isBlank)
        assertFalse(Book("", note = "x").isBlank)
        assertFalse(Book("", extra = listOf("", "y")).isBlank)
    }
}
