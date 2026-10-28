package com.hesaplarimdijital.kitapbulucu.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Turkish title case for titles and authors typed wholly in capitals; made-up titles besides a few well-known names. */
class TitleCaseTest {
    private fun fix(text: String) = TitleCase.fix(text)

    @Test
    fun capitalisesEachWordTheTurkishWay() {
        assertEquals("Fyodor Dostoyevski", fix("FYODOR DOSTOYEVSKİ"))
        assertEquals("Oğuz Atay", fix("OĞUZ ATAY"))
        assertEquals("Çalıkuşu", fix("ÇALIKUŞU"))
        assertEquals("Iğdır Yolları", fix("IĞDIR YOLLARI"))         // I is the capital of ı
        assertEquals("İnce Memed", fix("İNCE MEMED"))               // İ is the capital of i
        assertEquals("Şişli Gümüşsuyu Öyküleri", fix("ŞİŞLİ GÜMÜŞSUYU ÖYKÜLERİ"))
        assertEquals("Kırık Işıklı Sokak", fix("KIRIK IŞIKLI SOKAK"))
    }

    @Test
    fun keepsCircumflexes() {
        assertEquals("Hâlâ Işık", fix("HÂLÂ IŞIK"))
        assertEquals("Kâğıt Gemiler", fix("KÂĞIT GEMİLER"))
        assertEquals("Rüzgârlı Kasaba", fix("RÜZGÂRLI KASABA"))
        assertEquals("Hâkim Bey", fix("HÂKİM BEY"))
    }

    @Test
    fun theSuffixAfterAnApostropheStaysLowerCase() {
        assertEquals("İstanbul'un Saatleri", fix("İSTANBUL'UN SAATLERİ"))
        assertEquals("Bülent Ecevit'e", fix("BÜLENT ECEVİT'E"))
        assertEquals("Iğdır'dan Öte", fix("IĞDIR'DAN ÖTE"))
        assertEquals("1950'lerde İstanbul", fix("1950'LERDE İSTANBUL"))
        assertEquals("Ankara’nın Kalesi", fix("ANKARA’NIN KALESİ"))  // typographic apostrophe
        assertEquals("ABD'nin Tarihi", fix("ABD'NİN TARİHİ"))
        assertEquals("TBMM'de Bir Gün", fix("TBMM'DE BİR GÜN"))
    }

    @Test
    fun anOpeningQuoteStartsAWord() {
        assertEquals("'Kayıp' Zaman", fix("'KAYIP' ZAMAN"))
        assertEquals("\"Yaşamak\" Üzerine", fix("\"YAŞAMAK\" ÜZERİNE"))
        assertEquals("“Gece” Mektupları", fix("“GECE” MEKTUPLARI"))
    }

    @Test
    fun initialsAndDotsStartWords() {
        assertEquals("A.Hamdi Tanpınar", fix("A.HAMDİ TANPINAR"))
        assertEquals("Y. Kemal", fix("Y. KEMAL"))
        assertEquals("A. K. Doğan", fix("A. K. DOĞAN"))
        assertEquals("T.C. Anayasası", fix("T.C. ANAYASASI"))
        assertEquals("İ.Ü. Edebiyat Fakültesi", fix("İ.Ü. EDEBİYAT FAKÜLTESİ"))
    }

    @Test
    fun hyphensSlashesAndBracketsStartWords() {
        assertEquals("Çocuk-Adam", fix("ÇOCUK-ADAM"))
        assertEquals("Savaş/Barış", fix("SAVAŞ/BARIŞ"))
        assertEquals("Günlükler (Seçmeler)", fix("GÜNLÜKLER (SEÇMELER)"))
        assertEquals("Deniz Feneri - Öyküler", fix("DENİZ FENERİ - ÖYKÜLER"))
        assertEquals("Mektuplar [Seçme]", fix("MEKTUPLAR [SEÇME]"))
    }

    @Test
    fun romanNumeralsStayInCapitals() {
        assertEquals("I. Dünya Savaşı", fix("I. DÜNYA SAVAŞI"))
        assertEquals("II. Abdülhamit", fix("II. ABDÜLHAMİT"))
        assertEquals("XIX. Yüzyıl Romanı", fix("XIX. YÜZYIL ROMANI"))
        assertEquals("Tarih Dersleri IV", fix("TARİH DERSLERİ IV"))
        assertEquals("Anılar III - VI", fix("ANILAR III - VI"))
        assertEquals("XX. Yüzyıl, IX. Bölüm", fix("XX. YÜZYIL, IX. BÖLÜM"))
        assertEquals("Şiirler I", fix("ŞİİRLER I"))
        // words of the same letters that are no numerals
        assertEquals("Dil", fix("DİL"))
        assertEquals("Mil", fix("MİL"))
        assertEquals("Cıvıl Cıvıl", fix("CIVIL CIVIL"))   // Turkish, not a numeral
    }

    @Test
    fun abbreviationsFromTheListStayInCapitals() {
        assertEquals("MKE", fix("MKE"))
        assertEquals("TBMM Tutanakları", fix("TBMM TUTANAKLARI"))
        assertEquals("NATO ve UNESCO Raporu", fix("NATO VE UNESCO RAPORU"))
        assertEquals("ODTÜ Yayınları", fix("ODTÜ YAYINLARI"))
        assertEquals("İTÜ Vakfı", fix("İTÜ VAKFI"))
        assertEquals("TRT, TDK, MEB", fix("TRT, TDK, MEB"))
        assertEquals("SSCB ve ABD", fix("SSCB VE ABD"))
        assertEquals("DTCF, TTK, PTT, BM, AB, TC", fix("DTCF, TTK, PTT, BM, AB, TC"))
    }

    @Test
    fun tokensWithDigitsKeepTheirLetters() {
        assertEquals("1.Cilt", fix("1.CİLT"))           // the dot starts a word
        assertEquals("21.Yüzyıl", fix("21.YÜZYIL"))
        assertEquals("3B Yazıcı", fix("3B YAZICI"))
        assertEquals("MP3 Çağı", fix("MP3 ÇAĞI"))
        assertEquals("2. Baskı", fix("2. BASKI"))
    }

    @Test
    fun conjunctionsAndTheQuestionParticleAreLowerCaseInside() {
        assertEquals("Suç ve Ceza", fix("SUÇ VE CEZA"))
        assertEquals("Kürk Mantolu Madonna mı?", fix("KÜRK MANTOLU MADONNA MI?"))
        assertEquals("Leonardo da Vinci", fix("LEONARDO DA VİNCİ"))
        assertEquals("Simone de Beauvoir", fix("SİMONE DE BEAUVOİR"))
        assertEquals("Ya da Hiç", fix("YA DA HİÇ"))                    // the first word is a capital
        assertEquals("İle Başlayan", fix("İLE BAŞLAYAN"))
        assertEquals("De Gaulle", fix("DE GAULLE"))
        assertEquals("Şehir ve İnsan, ya da Öteki", fix("ŞEHİR VE İNSAN, YA DA ÖTEKİ"))
        assertEquals("Deniz ile Kum", fix("DENİZ İLE KUM"))
        assertEquals("Sen de mi?", fix("SEN DE Mİ?"))
        assertEquals("Ben ki Yazdım", fix("BEN Kİ YAZDIM"))
        assertEquals("Ve", fix("VE"))
        // attached to another mark it is not the conjunction
        assertEquals("Ev-De", fix("EV-DE"))
    }

    @Test
    fun onlyCellsWrittenWhollyInCapitalsChange() {
        assertTrue(TitleCase.needsFix("FYODOR DOSTOYEVSKİ"))
        assertTrue(TitleCase.needsFix("HÂLÂ 1984"))
        assertFalse(TitleCase.needsFix("Fyodor Dostoyevski"))
        assertFalse(TitleCase.needsFix("Kürk mantolu MADONNA"))
        assertFalse(TitleCase.needsFix("ırmak"))
        assertFalse(TitleCase.needsFix("1984"))
        assertFalse(TitleCase.needsFix(""))
        assertFalse(TitleCase.needsFix("   "))
        assertEquals("Fyodor Dostoyevski", fix("Fyodor Dostoyevski"))
        assertEquals("Kürk mantolu MADONNA", fix("Kürk mantolu MADONNA"))
        assertEquals("iPhone ile ODTÜ", fix("iPhone ile ODTÜ"))
        assertEquals("1984", fix("1984"))
        assertEquals("", fix(""))
        assertEquals("--", fix("--"))
    }

    @Test
    fun singleLettersStayCapitals() {
        assertEquals("İ", fix("İ"))
        assertEquals("I", fix("I"))
        assertEquals("O Bir Melek", fix("O BİR MELEK"))
        assertEquals("Mı", fix("MI"))   // alone, it opens the cell
    }

    @Test
    fun spacingAndMarksAreKept() {
        assertEquals("Uzun  Yol ", fix("UZUN  YOL "))
        assertEquals("Ne Var?!", fix("NE VAR?!"))
        assertEquals("Ah, Yaşam: Bir Roman", fix("AH, YAŞAM: BİR ROMAN"))
    }

    @Test
    fun fixingTwiceChangesNothingMore() {
        for (text in listOf("İSTANBUL'UN SAATLERİ", "SUÇ VE CEZA", "A.HAMDİ TANPINAR", "XIX. YÜZYIL ROMANI", "MKE")) {
            val once = fix(text)
            assertEquals(once, fix(once))
        }
    }
}
