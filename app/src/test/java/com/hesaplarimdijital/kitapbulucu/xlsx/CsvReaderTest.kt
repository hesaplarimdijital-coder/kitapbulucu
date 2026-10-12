package com.hesaplarimdijital.kitapbulucu.xlsx

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvReaderTest {

    /** The same three books, written four ways by make_edge_fixtures.py. */
    private val table = listOf(
        listOf("Kitap Adı", "Yazar", "Raf"),
        listOf("Islıklı Kuşların Sokağı", "Nevin Çağlar", "A1"),
        listOf("Çatıdaki Rasathane; ikinci baskı", "Ayşe İnce", ""),
        listOf("Dağ \"Sessizlik\"", "Ferit Ağaoğlu", "B2"),
    )

    private fun read(name: String): Sheet = CsvReader.read(TestWorkbooks.fixture(name), name)

    @Test
    fun readsWindows1254() {
        assertEquals(table, read("ornek-1254.csv").rows)
    }

    @Test
    fun readsUtf8WithByteOrderMark() {
        assertEquals(table, read("ornek-bom.csv").rows)
    }

    @Test
    fun readsCommaSeparatedUtf8() {
        assertEquals(table, read("ornek-virgul.csv").rows)
    }

    @Test
    fun readsQuotedNewlinesAndEmptyLines() {
        assertEquals(
            listOf(listOf("Kitap Adı", "Not"), listOf("Satır\niçinde yeni satır", "tırnak \" içinde"), emptyList<String>(), listOf("son", "")),
            read("ornek-tirnak.csv").rows,
        )
    }

    @Test
    fun readsLibreOfficeExport() {
        assertEquals(
            listOf(
                listOf("Kitap Adı", "Yazar", "Raf", "Adet", "Yıl", "Tarih", "Not"),
                listOf("Islıklı Kuşların Sokağı", "Nevin Çağlar", "A1", "1", "2011", "15.03.1998", "Çatı katı; ikinci baskı"),
                listOf("Dağ \"Sessizlik\"", "Ferit Ağaoğlu", "B2", "2", "1953", "Apr/53", ""),
                listOf("Hâlâ Bekleyen Mektup", "Şükrü Ilgaz", "C3", "1", "", "", "İmzalı"),
                listOf("Toplam", "", "", "4", "", "", ""),
            ),
            read("ornek-libreoffice.csv").rows,
        )
    }

    @Test
    fun decodesUtf8FirstAndFallsBackToWindows1254() {
        assertEquals("Şükrü Ilgaz", CsvReader.decode("Şükrü Ilgaz".toByteArray()))
        assertEquals("Şükrü Ilgaz", CsvReader.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Şükrü Ilgaz".toByteArray()))
        assertEquals("Şükrü Ilgaz", CsvReader.decode("Şükrü Ilgaz".toByteArray(charset("windows-1254"))))
        assertEquals("", CsvReader.decode(ByteArray(0)))
    }

    @Test
    fun sniffsTheDelimiter() {
        assertEquals(';', CsvReader.sniffDelimiter("a;b;c\n1;2;3\n"))
        assertEquals(',', CsvReader.sniffDelimiter("a,b,c\n1,2,3\n"))
        assertEquals('\t', CsvReader.sniffDelimiter("a\tb\tc\n1\t2\t3\n"))
        assertEquals(';', CsvReader.sniffDelimiter("a;b\n\"1,5\";\"2,5\"\n"))   // commas inside quotes do not count
        assertEquals(';', CsvReader.sniffDelimiter("a;b;c\n1;2;3,4\n"))          // a decimal comma is not the delimiter
        assertEquals(';', CsvReader.sniffDelimiter("tek sütun\nbaşka\n"))
        assertEquals(';', CsvReader.sniffDelimiter(""))
    }

    @Test
    fun parsesQuotesLineEndingsAndTrailingRows() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), CsvReader.parse("a;b\r\n1;2\r\n", ';'))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), CsvReader.parse("a;b\n1;2", ';'))
        assertEquals(listOf(listOf("a;b", "c\"d", "")), CsvReader.parse("\"a;b\";\"c\"\"d\";\n", ';'))
        assertEquals(listOf(listOf("", ""), listOf("a")), CsvReader.parse(";\na\n", ';'))
        assertEquals(emptyList<List<String>>(), CsvReader.parse(";\n", ';')) // a row of blanks at the end is noise
        assertEquals(listOf(listOf("a"), emptyList<String>(), listOf("b")), CsvReader.parse("a\n\nb\n\n\n", ';'))
        assertEquals(listOf(listOf("x", " boşluklu ", "\"")), CsvReader.parse("x; boşluklu ;\"\"\"\"", ';'))
        assertEquals(emptyList<List<String>>(), CsvReader.parse("", ';'))
        assertEquals(emptyList<List<String>>(), CsvReader.parse("\n\n", ';'))
    }
}
