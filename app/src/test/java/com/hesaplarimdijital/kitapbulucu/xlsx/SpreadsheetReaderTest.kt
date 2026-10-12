package com.hesaplarimdijital.kitapbulucu.xlsx

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Needs Robolectric: workbooks go through the Android XML parser. */
@RunWith(AndroidJUnit4::class)
class SpreadsheetReaderTest {

    @Test
    fun judgesFilesByTheirFirstBytes() {
        assertEquals(FileKind.XLSX, FileKinds.detect(TestWorkbooks.fixture("ornek-kutuphane.xlsx")))
        assertEquals(FileKind.XLSX, FileKinds.detect(TestWorkbooks.fixture("belge.zip")))
        assertEquals(FileKind.XLS, FileKinds.detect(TestWorkbooks.fixture("ornek-libreoffice.xls")))
        assertEquals(FileKind.CSV, FileKinds.detect(TestWorkbooks.fixture("ornek-1254.csv")))
        assertEquals(FileKind.CSV, FileKinds.detect(TestWorkbooks.fixture("ornek-bom.csv")))
        assertEquals(FileKind.CSV, FileKinds.detect("Kitap Adı;Yazar\r\nSefil Fenerciler;Halit Oruç\r\n".toByteArray()))
        assertEquals(FileKind.UNKNOWN, FileKinds.detect(ByteArray(0)))
        assertEquals(FileKind.UNKNOWN, FileKinds.detect(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)))
        assertEquals(FileKind.UNKNOWN, FileKinds.detect(ByteArray(100) { (it % 7).toByte() }))
    }

    @Test
    fun opensWorkbooksAndTextTablesAlike() {
        val workbook = SpreadsheetReader.read(TestWorkbooks.fixture("ornek-kutuphane.xlsx"), "Babam.xlsx")
        assertEquals("Babam", workbook.sheets.single().name)
        assertEquals("Kitap Adı", workbook.sheets.single().rows[0][0])

        val csv = SpreadsheetReader.read(TestWorkbooks.fixture("ornek-virgul.csv"), "liste.csv")
        assertEquals("liste", csv.sheets.single().name)
        assertEquals(4, csv.sheets.single().rows.size)

        val unnamed = SpreadsheetReader.read(TestWorkbooks.fixture("ornek-bom.csv"))
        assertEquals("CSV", unnamed.sheets.single().name)
        assertEquals("Kitap Adı", unnamed.sheets.single().rows[0][0])
    }

    @Test
    fun refusesOldExcelWithItsOwnReason() {
        assertReason(SpreadsheetException.Reason.OLD_XLS) { SpreadsheetReader.read(TestWorkbooks.fixture("ornek-libreoffice.xls"), "Kütüphane.xls") }
        assertReason(SpreadsheetException.Reason.OLD_XLS) { SpreadsheetReader.read(byteArrayOf(0, 1, 2, 3), "Kütüphane.xls") } // only the name is left to go by
    }

    @Test
    fun reportsDamagedAndForeignFiles() {
        assertReason(SpreadsheetException.Reason.DAMAGED) { SpreadsheetReader.read(TestWorkbooks.fixture("bozuk.xlsx"), "bozuk.xlsx") }
        assertReason(SpreadsheetException.Reason.DAMAGED) { SpreadsheetReader.read(byteArrayOf(0, 1, 2, 3), "liste.xlsx") }
        assertReason(SpreadsheetException.Reason.NOT_A_SPREADSHEET) { SpreadsheetReader.read(TestWorkbooks.fixture("belge.zip"), "belge.docx") }
        assertReason(SpreadsheetException.Reason.NOT_A_SPREADSHEET) { SpreadsheetReader.read(byteArrayOf(0, 1, 2, 3), "resim.png") }
    }

    @Test
    fun anEmptyFileIsAnEmptySheet() {
        val workbook = SpreadsheetReader.read(ByteArray(0), "bos.csv")
        assertTrue(workbook.sheets.single().isEmpty)
        assertEquals(emptyList<List<String>>(), workbook.sheets.single().rows)
        assertEquals(emptyList<Sheet>(), workbook.filledSheets)
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
