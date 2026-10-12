package com.hesaplarimdijital.kitapbulucu.trial

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipInputStream

/** Needs Robolectric: the XML parser comes from the Android runtime. */
@RunWith(AndroidJUnit4::class)
class MiniXlsxTest {

    @Test
    fun writtenCellsComeBackUnchanged() {
        val rows = listOf(
            listOf("Yazan", "Samsung SM-X926B"),
            listOf("Saat", "12.10.2026 14:05:12"),
            listOf("Türkçe & özel", "<İğne> \"ışık\" 'şapka' hâlâ"),
            listOf(" boşluklu ", "sonda "),
        )
        val bytes = MiniXlsx.build(rows)
        val entries = ZipInputStream(bytes.inputStream()).use { zip -> generateSequence { zip.nextEntry }.map { it.name }.toList() }
        assertEquals(listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml"), entries)
        assertEquals(rows, MiniXlsx.readCells(bytes.inputStream()))
    }

    @Test
    fun readsWorkbooksWrittenByOtherPrograms() {
        val cells = javaClass.getResourceAsStream("/fixtures/ornek-kutuphane.xlsx")!!.use(MiniXlsx::readCells)
        assertEquals(listOf("Kitap Adı", "Yazar", "Raf", "Adet", "İmzalı", "Yayınevi", "Yıl", "ISBN", "Not"), cells[0])
        assertEquals("Islıklı Kuşların Sokağı", cells[1][0])
        assertEquals("1", cells[1][3])             // a number cell, as stored
        assertEquals("=Formül Sanılan Kitap", cells[10][0])
        assertEquals("Evet", cells[9][4])
    }

    @Test
    fun columnNamesRoundTrip() {
        for (index in listOf(0, 1, 25, 26, 27, 51, 52, 701, 702)) {
            assertEquals(index, MiniXlsx.columnIndex(MiniXlsx.columnName(index) + "7"))
        }
        assertEquals("A", MiniXlsx.columnName(0))
        assertEquals("Z", MiniXlsx.columnName(25))
        assertEquals("AA", MiniXlsx.columnName(26))
    }
}
