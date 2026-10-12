package com.hesaplarimdijital.kitapbulucu.search

import org.junit.Assert.assertEquals
import org.junit.Test

class EditDistanceTest {
    private val distance = EditDistance()

    @Test
    fun countsInsertsDeletesChangesAndSwaps() {
        assertEquals(0, distance.bounded("mantolu", "mantolu", 2))
        assertEquals(1, distance.bounded("mantlu", "mantolu", 2))   // a letter missing
        assertEquals(1, distance.bounded("mantoolu", "mantolu", 2)) // a letter too many
        assertEquals(1, distance.bounded("mantalu", "mantolu", 2))  // a wrong letter
        assertEquals(1, distance.bounded("krük", "kürk", 2))        // neighbours swapped
        assertEquals(1, distance.bounded("abcd", "acbd", 2))
        assertEquals(2, distance.bounded("fenarcilar", "fenerciler", 2))
        assertEquals(2, distance.bounded("kedi", "kadı", 2))
    }

    @Test
    fun stopsAtTheBound() {
        assertEquals(2, distance.bounded("fenarcilarr", "fenerciler", 1)) // bound + 1 means "further than that"
        assertEquals(2, distance.bounded("kurk", "kedi", 1))
        assertEquals(3, distance.bounded("kurk", "kedi", 2))
        assertEquals(3, distance.bounded("abc", "xyz", 2))
        assertEquals(2, distance.bounded("abcdefgh", "abcdefghij", 1))  // too different in length to bother
        assertEquals(2, distance.bounded("abcdefgh", "abcdef", 1))
    }

    @Test
    fun handlesEmptyStrings() {
        assertEquals(0, distance.bounded("", "", 1))
        assertEquals(1, distance.bounded("", "a", 1))
        assertEquals(2, distance.bounded("", "ab", 1))
        assertEquals(1, distance.bounded("a", "", 1))
    }

    @Test
    fun comparesAgainstPrefixesWhenAsked() {
        assertEquals(1, distance.bounded("mantl", "mantolu", 1, prefix = true))
        assertEquals(0, distance.bounded("mant", "mantolu", 1, prefix = true))
        assertEquals(1, distance.bounded("kurk", "kurtulus", 1, prefix = true))
        assertEquals(2, distance.bounded("kurk", "kedileri", 1, prefix = true))
        assertEquals(2, distance.bounded("mantolu", "mant", 1, prefix = true)) // a word shorter than the token is not a prefix match
        assertEquals(1, distance.bounded("mantol", "mantolu", 1, prefix = false))
    }

    @Test
    fun reusesItsRowsSafelyAcrossCalls() {
        for (i in 0 until 50) {
            assertEquals(1, distance.bounded("mantlu", "mantolu", 1))
            assertEquals(0, distance.bounded("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz", "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz", 2))
            assertEquals(1, distance.bounded("ab", "ba", 1))
        }
    }
}
