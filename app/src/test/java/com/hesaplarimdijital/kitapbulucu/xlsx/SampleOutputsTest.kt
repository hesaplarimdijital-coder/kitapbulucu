package com.hesaplarimdijital.kitapbulucu.xlsx

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Writes the sample workbooks CI keeps (and checks with openpyxl and LibreOffice), so that a real
 * file from the app's writer can be opened on the tablet in Excel, Office and Google Sheets.
 * Books are the made-up ones from the fixtures.
 */
@RunWith(AndroidJUnit4::class)
class SampleOutputsTest {

    @Test
    fun writesTheSampleWorkbooks() {
        val dir = System.getProperty("kitapbulucu.sampleDir")?.let(::File) ?: return
        dir.mkdirs()
        val fixture = XlsxReader.read(TestWorkbooks.fixture("ornek-kutuphane.xlsx").inputStream()).sheets.single()
        val dad = BookTable.parse(fixture)!!.table
        val me = BookTable(
            listOf(
                Book("Sessiz Harita", "Leyla Umut", "1", 1, false, "Pusula", "2016", "9789750000032", "", listOf("Türkçe")),
                Book("Dağ Başındaki <Sessizlik>", "Ferit Ağaoğlu", "2", 2, true, "Kuyu & Kalem", "Nis.53", "", "Kapak yırtık", listOf("")),
            ),
            listOf("Dil"),
        )
        File(dir, Owner.DAD.fileName).writeBytes(XlsxWriter.write(listOf(dad.toSheetSpec(Owner.DAD.label))))
        File(dir, Owner.ME.fileName).writeBytes(XlsxWriter.write(listOf(me.toSheetSpec(Owner.ME.label))))
        File(dir, "Ortak.xlsx").writeBytes(XlsxWriter.write(listOf(dad.toSheetSpec(Owner.DAD.exportSheet), me.toSheetSpec(Owner.ME.exportSheet))))

        val readBack = XlsxReader.read(File(dir, "Ortak.xlsx").inputStream())
        assertEquals(listOf("MKE", "Barış"), readBack.sheets.map { it.name })
        assertEquals(dad.books, BookTable.parse(readBack.sheets[0])!!.table.books)
        assertEquals(me.books, BookTable.parse(readBack.sheets[1])!!.table.books)
    }
}
