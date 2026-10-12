package com.hesaplarimdijital.kitapbulucu.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TurkishTextTest {

    @Test
    fun lowerAndUpperKnowTheTwoIs() {
        assertEquals("ısparta ve iğdır", TurkishText.lower("ISPARTA VE İĞDIR"))
        assertEquals("ISPARTA VE İĞDIR", TurkishText.upper("ısparta ve iğdır"))
        assertEquals("hâlâ şükrü çağlar", TurkishText.lower("HÂLÂ ŞÜKRÜ ÇAĞLAR"))
        assertEquals("", TurkishText.lower(""))
    }

    @Test
    fun asciiFoldFlattensTurkishLetters() {
        assertEquals("hala igde", TurkishText.asciiFold("Hâlâ İğde"))
        assertEquals("islikli kuslarin sokagi", TurkishText.asciiFold("ISLIKLI KUŞLARIN SOKAĞI"))
        assertEquals("sukru ilgaz, omer isikci, cagri", TurkishText.asciiFold("Şükrü Ilgaz, Ömer Işıkçı, Çağrı"))
        assertEquals("kurk mantolu madonna", TurkishText.asciiFold("Kürk Mantolu Madonna"))
        assertEquals("3-4 / isbn 978", TurkishText.asciiFold("3-4 / ISBN 978"))
    }

    @Test
    fun squeezeTrimsAndCollapsesWhitespace() {
        assertEquals("Çatıdaki Rasathane", TurkishText.squeeze("  Çatıdaki \t\n Rasathane  "))
        assertEquals("a b", TurkishText.squeeze("a  b"))
        assertEquals("", TurkishText.squeeze("   "))
        assertEquals("tek", TurkishText.squeeze("tek"))
    }
}
