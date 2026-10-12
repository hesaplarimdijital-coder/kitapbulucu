package com.hesaplarimdijital.kitapbulucu.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.xlsx.Sheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Needs Robolectric for org.json. */
@RunWith(AndroidJUnit4::class)
class ColumnMappingTest {

    private val headerless = Sheet(
        "Sayfa1",
        listOf(
            listOf("ISLIKLI KUŞLARIN SOKAĞI", "NEVİN ÇAĞLAR"),
            listOf("SEFİL FENERCİLER", "HALİT ORUÇ"),
            listOf("ÇATIDAKİ RASATHANE", "AYŞE İNCE", "ödünç"),
        ),
    )

    private val oddHeaders = Sheet(
        "Liste",
        listOf(
            listOf("Eserin Tam Adı", "Kim yazdı", "Dolap"),
            listOf("Sefil Fenerciler", "Halit Oruç", "A1"),
            listOf("Kar Tanesi", "Orhan Pamir", "B4"),
        ),
    )

    @Test
    fun guessesTitleAndAuthorForAHeaderlessSheet() {
        assertEquals(-1, BookTable.detectHeaderRow(headerless))
        val guess = ColumnMapping.guess(headerless)
        assertEquals(-1, guess.headerRow)
        assertEquals(mapOf(0 to BookColumn.TITLE, 1 to BookColumn.AUTHOR), guess.columns)
        assertNull(BookTable.parse(headerless))
        val parsed = BookTable.parse(headerless, guess)!!
        assertEquals(-1, parsed.headerRow)
        assertEquals(listOf("ISLIKLI KUŞLARIN SOKAĞI", "SEFİL FENERCİLER", "ÇATIDAKİ RASATHANE"), parsed.table.books.map { it.title })
        assertEquals(listOf(""), parsed.table.extraHeaders) // the third column has no name but holds something
        assertEquals(listOf("ödünç"), parsed.table.books[2].extra)
    }

    @Test
    fun usesTheUsersChoiceForUnknownHeaders() {
        assertNull(BookTable.parse(oddHeaders))
        val guess = ColumnMapping.guess(oddHeaders) // "Dolap" is a known name for the shelf column
        assertEquals(0, guess.headerRow)
        assertEquals(mapOf(2 to BookColumn.SHELF), guess.columns)
        val mapping = ColumnMapping.of(oddHeaders, 0, mapOf(0 to BookColumn.TITLE, 1 to BookColumn.AUTHOR, 2 to BookColumn.SHELF))
        assertEquals(listOf("Eserin Tam Adı", "Kim yazdı", "Dolap"), mapping.headerTexts)
        val parsed = BookTable.parse(oddHeaders, mapping)!!
        assertEquals(listOf(Book("Sefil Fenerciler", "Halit Oruç", "A1"), Book("Kar Tanesi", "Orhan Pamir", "B4")), parsed.table.books)
        assertEquals(emptyList<String>(), parsed.table.extraHeaders)
    }

    @Test
    fun aMappingIsDroppedWhenTheSheetChangesShape() {
        val mapping = ColumnMapping.of(oddHeaders, 0, mapOf(0 to BookColumn.TITLE, 1 to BookColumn.AUTHOR))
        val renamed = Sheet("Liste", listOf(listOf("Ad", "Yazar", "Raf"), listOf("Sefil Fenerciler", "Halit Oruç", "A1")))
        assertFalse(mapping.fits(renamed, BookTable.detectHeaderRow(renamed)))
        // the app's own headers are recognised again, so the stale mapping is not needed
        assertEquals(Book("Sefil Fenerciler", "Halit Oruç", "A1"), BookTable.parse(renamed, mapping)!!.table.books.single())

        val headerlessMapping = ColumnMapping(-1, mapOf(0 to BookColumn.TITLE))
        assertTrue(headerlessMapping.fits(headerless, -1))
        val nowWithHeaders = Sheet("Sayfa1", listOf(BookTable.HEADERS, listOf("Sefil Fenerciler", "Halit Oruç")))
        assertFalse(headerlessMapping.fits(nowWithHeaders, BookTable.detectHeaderRow(nowWithHeaders)))
        assertEquals(1, BookTable.parse(nowWithHeaders, headerlessMapping)!!.table.books.size) // the header row is not read as a book
    }

    @Test
    fun aMappingWithoutTheTitleIsIgnored() {
        assertNull(BookTable.parse(headerless, ColumnMapping(-1, mapOf(1 to BookColumn.AUTHOR))))
    }

    @Test
    fun survivesStorage() {
        val mapping = ColumnMapping.of(oddHeaders, 0, mapOf(0 to BookColumn.TITLE, 2 to BookColumn.SHELF, 1 to BookColumn.AUTHOR))
        assertEquals(mapping, ColumnMapping.fromJson(mapping.toJson()))
        val headerlessMapping = ColumnMapping(-1, mapOf(0 to BookColumn.TITLE))
        assertEquals(headerlessMapping, ColumnMapping.fromJson(headerlessMapping.toJson()))
        assertNull(ColumnMapping.fromJson(null))
        assertNull(ColumnMapping.fromJson("bozuk"))
        assertEquals(ColumnMapping(-1, emptyMap()), ColumnMapping.fromJson("""{"headerRow":-1,"columns":{"0":"UNKNOWN"}}"""))
    }

    @Test
    fun booksAboveAHeaderSendTheSheetToMapping() {
        val sheet = Sheet(
            "Eski",
            listOf(
                listOf("A KİTABI", "A YAZARI"), listOf("B KİTABI", "B YAZARI"), listOf("C KİTABI", "C YAZARI"), listOf("D KİTABI", "D YAZARI"),
                listOf("KİTAP", "YAZAR"),
                listOf("E KİTABI", "E YAZARI"),
            ),
        )
        assertEquals(-1, BookTable.detectHeaderRow(sheet))
        val guess = ColumnMapping.guess(sheet)
        assertEquals(ColumnMapping(-1, mapOf(0 to BookColumn.TITLE, 1 to BookColumn.AUTHOR)), guess)
        val parsed = BookTable.parse(sheet, guess)!!
        assertEquals(listOf("A KİTABI", "B KİTABI", "C KİTABI", "D KİTABI", "E KİTABI"), parsed.table.books.map { it.title })
    }
}
