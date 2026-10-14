package com.hesaplarimdijital.kitapbulucu.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The expected values come from tools/temizleme/booklib.py, the rules of the first cleaning. */
class CleaningTest {
    private fun x(text: String) = Cleaning.extractSigned(text).let { it.text to it.note }

    @Test
    fun takesTheSignedMarkerOutLikeTheFirstCleaning() {
        assertEquals("HÂLÂ BEKLEYEN MEKTUP" to "İmzalı", x("HÂLÂ BEKLEYEN MEKTUP - İMZALI"))
        assertEquals("OYUN - ŞİİR" to "İmzalı", x("OYUN - İMZALI ŞİİR"))
        assertEquals("YAĞMURUN İSTANBUL'U" to "Cemal Bey'e imzalı", x("YAĞMURUN İSTANBUL'U - CEMAL BEY'E İMZALI"))
        assertEquals("KIRMIZI DEFTER" to "İmzalı", x("KIRMIZI DEFTER (İMZALI)"))
        assertEquals("MAVİ KİTAP" to "Bülent Ecevit'e imzalı", x("MAVİ KİTAP (BÜLENT ECEVİT'E İMZALI)"))
        assertEquals("SESSİZ HARİTA BASKI" to "İmzalı", x("SESSİZ HARİTA İMZALI BASKI"))
        assertEquals("İMZASIZ MEKTUPLAR" to null, x("İMZASIZ MEKTUPLAR"))
        assertEquals("ÇATIDAKİ RASATHANE" to null, x("  ÇATIDAKİ  RASATHANE "))
        assertEquals("" to "İmzalı", x("İMZALI"))
        assertEquals("Kar tanesi" to "İmzalı", x("Kar tanesi - imzalı"))
    }

    @Test
    fun tellsVolumesApart() {
        assertTrue(Cleaning.differsOnlyByNumber("SUÇ VE CEZA", "SUÇ VE CEZA 1"))
        assertTrue(Cleaning.differsOnlyByNumber("NTV ALMANAK 2006", "NTV ALMANAK 2007"))
        assertTrue(Cleaning.differsOnlyByNumber("TARİH II", "TARİH III"))
        assertFalse(Cleaning.differsOnlyByNumber("KAR", "KAR TANESİ"))
        assertFalse(Cleaning.differsOnlyByNumber("SUÇ VE CEZA", "SUÇ VE CEZA"))
    }

    @Test
    fun foldsAndTitleCasesLikeBooklib() {
        assertEquals("hala istanbul un kedileri", Cleaning.fold("Hâlâ İSTANBUL'un, KEDİLERİ!"))
        assertEquals("Bülent Ecevit'e", Cleaning.titleCase("BÜLENT ECEVİT'E"))
    }
}
