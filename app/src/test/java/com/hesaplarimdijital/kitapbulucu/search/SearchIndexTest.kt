package com.hesaplarimdijital.kitapbulucu.search

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {

    /** Made-up books; a few share words on purpose. */
    private val index = SearchIndex.build(
        mapOf(
            Owner.DAD to listOf(
                Book("Islıklı Kuşların Sokağı", "Nevin Çağlar", "A1"),
                Book("Sefil Fenerciler", "Halit Oruç", "A1"),
                Book("Hâlâ Bekleyen Mektup", "Şükrü Ilgaz", "A2"),
                Book("Kürk Mantolu Kedi", "Sabiha Alın", "B3"),
                Book("Kar Tanesi", "Orhan Pamir", "B3"),
                Book("Kar", "Orhan Pamir", "B4"),
                Book("Karanlık Oda", "Leyla Umut", "C1"),
                Book("İstanbul'un Kedileri", "Ayşe İnce", "C2"),
                Book("Kurk Dergisi", "", "C2"),
                Book("Çatıdaki Rasathane", "Ayşe İnce", "C2"),
                Book("Almanak 2006", "", "D1"),
            ),
            Owner.ME to listOf(
                Book("Sessiz Harita", "Leyla Umut", "1"),
                Book("Kar Tanesi", "Orhan Pamir", "2"),
                Book("Dağ Başındaki Sessizlik", "Ferit Ağaoğlu", "2"),
            ),
        ),
    )

    private fun titles(query: String, owner: Owner? = null): List<String> = index.search(query, owner).map { it.book.title }

    @Test
    fun findsTurkishSpellingsFromPlainTyping() {
        assertEquals("Islıklı Kuşların Sokağı", titles("islik").first())
        assertEquals("Sefil Fenerciler", titles("sefil").first())
        assertEquals("Hâlâ Bekleyen Mektup", titles("hala").first())
        assertEquals(1, index.search("hala").single().level) // not an ASCII fallback: the hat is not a Turkish letter
        assertEquals(listOf("Islıklı Kuşların Sokağı"), titles("ISLIKLI"))
    }

    @Test
    fun wordOrderDoesNotMatter() {
        assertEquals(listOf("Kürk Mantolu Kedi"), titles("kedi mantolu kürk"))
        assertEquals(listOf("Kürk Mantolu Kedi"), titles("mantolu"))
    }

    @Test
    fun searchesTitleAndAuthorTogether() {
        assertEquals(listOf("Kar", "Kar Tanesi", "Kar Tanesi"), titles("orhan pamir kar"))
        assertEquals(listOf("Karanlık Oda", "Sessiz Harita"), titles("leyla"))
        assertEquals(listOf("Çatıdaki Rasathane", "İstanbul'un Kedileri"), titles("ayşe ince"))
        val mixed = index.search("orhan pamir kar").first()
        assertEquals(3, mixed.layer)
        assertTrue(!mixed.inTitle)
    }

    @Test
    fun exactBeatsStartBeatsInside() {
        val hits = index.search("kar")
        assertEquals(listOf("Kar", "Kar Tanesi", "Kar Tanesi", "Karanlık Oda"), hits.map { it.book.title })
        assertEquals(listOf(1, 2, 2, 2), hits.map { it.layer })
        assertEquals(listOf(Owner.DAD, Owner.DAD, Owner.ME, Owner.DAD), hits.map { it.owner })

        assertEquals(listOf(1, 1), index.search("kar tanesi").map { it.layer })
        assertEquals(listOf(3, 3), index.search("tane").map { it.layer })   // a word prefix, not the title start
        assertEquals(listOf(4, 4), index.search("anesi").map { it.layer })  // inside a word
        assertEquals(listOf("Kar Tanesi", "Kar Tanesi"), titles("nesi"))
    }

    @Test
    fun apostrophesAreOptional() {
        assertEquals(listOf("İstanbul'un Kedileri"), titles("istanbulun"))
        assertEquals(listOf("İstanbul'un Kedileri"), titles("istanbul'un"))
        assertEquals(listOf("İstanbul'un Kedileri"), titles("İstanbul’un kedileri"))
        assertEquals(listOf("İstanbul'un Kedileri"), titles("istanbul"))
        assertEquals(listOf("Kürk Mantolu Kedi", "İstanbul'un Kedileri"), titles("kedi")) // the whole word before the prefix
    }

    @Test
    fun filtersByOwner() {
        assertEquals(listOf(Owner.DAD, Owner.ME), index.search("kar tanesi").map { it.owner })
        assertEquals(listOf(Owner.ME), index.search("kar tanesi", Owner.ME).map { it.owner })
        assertEquals(listOf(Owner.DAD), index.search("kar tanesi", Owner.DAD).map { it.owner })
        assertEquals(emptyList<SearchHit>(), index.search("islik", Owner.ME))
    }

    @Test
    fun asciiTypingFindsTurkishWordsButRanksThemLower() {
        val hits = index.search("kurk")
        assertEquals(listOf("Kurk Dergisi", "Kürk Mantolu Kedi"), hits.map { it.book.title })
        assertEquals(listOf(1, 2), hits.map { it.level })
        assertEquals(listOf("Kürk Mantolu Kedi"), titles("kürk"))
        assertEquals(listOf("Çatıdaki Rasathane"), titles("catidaki"))
        assertEquals(listOf("Dağ Başındaki Sessizlik"), titles("dag basindaki"))
        assertEquals(2, index.search("sukru").single().level)
    }

    @Test
    fun digitsAreWords() {
        assertEquals(listOf("Almanak 2006"), titles("2006"))
        assertEquals(listOf("Almanak 2006"), titles("almanak 200"))
    }

    @Test
    fun shortWordsDoNotMatchInsideOtherWords() {
        assertEquals(emptyList<String>(), titles("ri")) // "ri" sits inside harita, kedileri, fenerciler; too short to count
        assertEquals(emptyList<String>(), titles("yok böyle kitap"))
        assertEquals(emptyList<String>(), titles(""))
        assertEquals(emptyList<String>(), titles("   "))
        assertEquals(emptyList<String>(), titles("- ..."))
    }

    @Test
    fun limitsAndCountsHits() {
        assertEquals(14, index.size)
        assertEquals(2, index.search("a", limit = 2).size)
        assertTrue(index.search("a").size > 2)
    }

    @Test
    fun sortsShortTitlesAndTurkishAlphabetFirstWithinALayer() {
        val local = SearchIndex.build(
            mapOf(Owner.DAD to listOf(Book("Şeker Portakalı Hikâyesi"), Book("Çay Saati"), Book("Zeytin"), Book("Üzüm Bağı"), Book("Incir"), Book("İnci"))),
        )
        assertEquals(listOf("İnci", "Incir"), local.search("i").map { it.book.title }) // both start with the query; the shorter title first

        val sameLayer = SearchIndex.build(mapOf(Owner.DAD to listOf(Book("Zar Oyunu"), Book("Çar Oyunu"), Book("Üst Oyunu"), Book("Var Oyunu"), Book("Car Oyunu"))))
        assertEquals(listOf("Car Oyunu", "Çar Oyunu", "Üst Oyunu", "Var Oyunu", "Zar Oyunu"), sameLayer.search("oyunu").map { it.book.title })
    }

    @Test
    fun toleratesTyposOnlyWhenNothingElseMatches() {
        val hits = index.search("kurk mantlu")
        assertEquals(listOf("Kürk Mantolu Kedi"), hits.map { it.book.title })
        assertEquals(5, hits.single().layer)
        assertEquals(listOf("Kürk Mantolu Kedi"), titles("krük"))          // two letters swapped count as one typo
        assertEquals(listOf("Kürk Mantolu Kedi", "İstanbul'un Kedileri"), titles("kedy")) // four letters allow one typo; the last word may also be a prefix
        assertEquals(listOf("Sefil Fenerciler"), titles("fenarcilar"))     // eight or more allow two
        assertEquals(emptyList<String>(), titles("fenarcilarr"))           // three is too many
        assertEquals(emptyList<String>(), titles("kat"))                   // three letters allow none
        assertEquals(listOf("Kürk Mantolu Kedi"), titles("kürk mantl"))    // the word being typed may have a typo in its prefix
        assertEquals(2, index.search("kürk mant").single().layer)         // but a clean prefix is a plain match (the title starts with it), not a typo
    }

    @Test
    fun nearMatchesStayBelowPlainOnes() {
        val local = SearchIndex.build(mapOf(Owner.DAD to listOf(Book("Kat Tanesi"), Book("Kar Tanesi"), Book("Kar Tanesi Üstüne"))))
        assertEquals(listOf("Kar Tanesi", "Kar Tanesi Üstüne"), local.search("kar tanesi").map { it.book.title })
        val suggested = local.suggest("kar tanesi")
        assertEquals(listOf("Kar Tanesi", "Kar Tanesi Üstüne", "Kat Tanesi"), suggested.map { it.book.title })
        assertEquals(listOf(2, 2, 1), suggested.map { it.matchedWords })
        assertEquals(listOf(1, 2, 3), suggested.map { it.layer })
    }

    @Test
    fun suggestsTheClosestBooksWhenNothingMatches() {
        assertEquals(emptyList<String>(), titles("kürk mantolu kaplan"))
        val suggested = index.suggest("kürk mantolu kaplan")
        assertEquals(listOf("Kürk Mantolu Kedi", "Kurk Dergisi"), suggested.map { it.book.title }) // two words, then one word with a typo
        assertEquals(listOf(2, 1), suggested.map { it.matchedWords })
        assertEquals(listOf("Kar", "Kar Tanesi", "Kar Tanesi"), index.suggest("kar").map { it.book.title })
        assertEquals(listOf("Kar Tanesi"), index.suggest("kar tanesi", Owner.ME).map { it.book.title })
        assertEquals(emptyList<SearchHit>(), index.suggest(""))
        assertEquals(emptyList<SearchHit>(), index.suggest("zxqwv"))
    }

    @Test
    fun oddQueriesAreHarmless() {
        assertTrue(index.search("k").isNotEmpty())
        assertEquals(emptyList<String>(), titles("x"))
        assertEquals(listOf("Almanak 2006"), titles("2"))
        val longQuery = (1..80).joinToString(" ") { "sözcük$it" }
        assertEquals(emptyList<String>(), titles(longQuery))
        assertEquals(emptyList<String>(), titles("a".repeat(500)))
        assertEquals(listOf("Kürk Mantolu Kedi"), titles("KÜRK, MANTOLU; KEDİ!!!"))
    }
}
