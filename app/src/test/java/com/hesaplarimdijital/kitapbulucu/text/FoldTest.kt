package com.hesaplarimdijital.kitapbulucu.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldTest {

    @Test
    fun levelOneKeepsTurkishLettersButNotDotsAndHats() {
        assertEquals("hala islik iğde çağrı", Fold.turkish("Hâlâ Islık İğde Çağrı"))
        assertEquals("istanbul'un kedileri", Fold.turkish("İSTANBUL'UN KEDİLERİ"))
        assertEquals("cafe noel", Fold.turkish("Café Noël"))
        assertEquals("kürk mantolu", Fold.turkish("KÜRK MANTOLU"))
    }

    @Test
    fun levelTwoFlattensEverything() {
        assertEquals("hala islik igde cagri", Fold.ascii("Hâlâ Islık İğde Çağrı"))
        assertEquals("kurk mantolu kedi", Fold.ascii("Kürk Mantolu Kedi"))
        assertEquals("sukru ilgaz", Fold.ascii("Şükrü ILGAZ"))
    }

    @Test
    fun splitsWordsAndKeepsApostropheFormsForTheIndex() {
        assertEquals(listOf("yağmurun", "istanbul", "u", "istanbulu"), Fold.words("Yağmurun İstanbul'u"))
        assertEquals(listOf("istanbul", "un", "istanbulun", "kedileri"), Fold.words("İstanbul’un Kedileri"))
        assertEquals(listOf("kürk", "mantolu", "kedi", "2006"), Fold.words("Kürk  Mantolu - Kedi (2006)!"))
        assertEquals(listOf("tirnak", "başta"), Fold.words("’tırnak’ başta'"))
        assertEquals(listOf("a", "b"), Fold.words("a''b"))
        assertEquals(emptyList<String>(), Fold.words(""))
        assertEquals(emptyList<String>(), Fold.words(" - ... "))
    }

    @Test
    fun aQueryKeepsOnlyTheJoinedApostropheForm() {
        assertEquals(listOf("yağmurun", "istanbulu"), Fold.words("Yağmurun İstanbul'u", forQuery = true))
        assertEquals(listOf("istanbulun"), Fold.words("istanbulun", forQuery = true))
        assertEquals(listOf("kar", "tanesi"), Fold.words("kar tanesi", forQuery = true))
    }

    @Test
    fun knowsWhenTurkishLettersWereTyped() {
        assertTrue(Fold.hasTurkishLetters("kürk"))
        assertTrue(Fold.hasTurkishLetters("Islık"))
        assertFalse(Fold.hasTurkishLetters("kurk mantolu"))
    }
}
