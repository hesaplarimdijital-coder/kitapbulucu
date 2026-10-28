package com.hesaplarimdijital.kitapbulucu.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private fun of(vararg values: String) = Suggestions.of(values.asSequence())

    private val publishers = of(
        "Türkiye İş Bankası Yayınları", "Türkiye İş Bankası Yayınları", "Türkiye İş Bankası Yayınları",
        "Can Yayınları", "Can Yayınları", "İletişim Yayınları", "Isparta Kitabevi", "Yapı Kredi Yayınları",
        "Kırmızı Kedi", "Işık Yayınları",
    )

    @Test
    fun wordStartFindsTheValue() {
        for (q in listOf("iş", "IŞ", "İş", "iş ban", "ban iş", "Türk iş")) {
            assertTrue("$q", "Türkiye İş Bankası Yayınları" in publishers.suggest(q))
        }
        // typed without Turkish letters
        assertTrue("Türkiye İş Bankası Yayınları" in publishers.suggest("is"))
        assertTrue("Türkiye İş Bankası Yayınları" in publishers.suggest("is ban"))
        assertEquals(listOf("Fyodor Dostoyevski"), of("Fyodor Dostoyevski", "Orhan Pamuk").suggest("dosto"))
    }

    @Test
    fun aQueryWithTurkishLettersIsStrict() {
        val found = publishers.suggest("iş")
        assertTrue("Isparta Kitabevi" !in found)
        assertTrue("Türkiye İş Bankası Yayınları" in found)
        // without Turkish letters the plain and the Turkish spelling both match
        val loose = publishers.suggest("is")
        assertTrue("Isparta Kitabevi" in loose)
        assertTrue("Türkiye İş Bankası Yayınları" in loose)
    }

    @Test
    fun dottedAndDotlessI() {
        val s = of("Işık Yayınları", "İletişim Yayınları", "Irmak Kitap", "İnkılap")
        // dotted and dotless capitals, typed in either case, find the same words
        for (q in listOf("ink", "INK", "İnk", "ınk", "Ink")) assertEquals(q, listOf("İnkılap"), s.suggest(q))
        for (q in listOf("ırmak", "IRMAK", "irmak", "İrmak")) assertEquals(q, listOf("Irmak Kitap"), s.suggest(q))
        assertEquals(listOf("Işık Yayınları"), s.suggest("ışık"))
        assertEquals(listOf("Işık Yayınları"), s.suggest("IŞIK"))
        assertEquals(listOf("Işık Yayınları"), s.suggest("İŞİK"))
        assertEquals(listOf("İletişim Yayınları"), s.suggest("ilet"))
    }

    @Test
    fun circumflexIsIgnored() {
        val s = of("Hâlâ Yayınları", "Rüzgârlı Kasaba")
        assertEquals(listOf("Hâlâ Yayınları"), s.suggest("hala"))
        assertEquals(listOf("Hâlâ Yayınları"), s.suggest("HÂLÂ"))
        assertEquals(listOf("Rüzgârlı Kasaba"), s.suggest("rüzgarlı"))
        assertEquals(listOf("Rüzgârlı Kasaba"), s.suggest("ruzgarli kas"))
    }

    @Test
    fun valueStartBeatsWordStartThenUsageThenAlphabet() {
        val s = of(
            "Yapı Kredi Yayınları", "Yapı Kredi Yayınları", "Yapı Kredi Yayınları", // used 3x, but "kredi" starts a later word
            "Kredi Yayınları", // begins with the query
            "Kırmızı Kedi", "Kırmızı Kedi", "Kır Kitap",
        )
        assertEquals(listOf("Kredi Yayınları", "Yapı Kredi Yayınları"), s.suggest("kredi"))
        // among values that begin with the query: more books first, then alphabetical
        assertEquals(listOf("Kırmızı Kedi", "Kır Kitap"), s.suggest("kır"))
        val ties = of("Çiçek", "Cevat", "Çam", "Cem")
        assertEquals(listOf("Cem", "Cevat", "Çam", "Çiçek"), ties.suggest("c"))
    }

    @Test
    fun turkishAlphabeticalOrder() {
        // ç after c, ı before i, ş after s
        val s = of("Zeki", "Zafer", "Zühtü", "Zıya", "Zeynep")
        assertEquals(listOf("Zafer", "Zeki", "Zeynep", "Zıya", "Zühtü"), s.suggest("z"))
    }

    @Test
    fun spellingVariantsAreOneValueShownInTheMostUsedSpelling() {
        val s = of("Iş Bankası", "İş Bankası", "İş Bankası", "iş bankası")
        assertEquals(listOf("İş Bankası"), s.suggest("iş b"))
        assertEquals(1, s.size)
        // all variants count as uses of the one value
        val ranked = of("Ada Kitap", "Ada Kitap", "Aba Kitap", "Aba Kitap", "ADA KİTAP").suggest("a")
        assertEquals(2, ranked.size)
        assertEquals("Ada Kitap", ranked[0]) // three uses against two
    }

    @Test
    fun atMostSixAndNothingForNoQuery() {
        val many = of(*Array(20) { "Yayın $it" })
        assertEquals(6, many.suggest("yay").size)
        assertEquals(3, many.suggest("yay", limit = 3).size)
        assertTrue(many.suggest("").isEmpty())
        assertTrue(many.suggest("   ").isEmpty())
        assertTrue(many.suggest("?!").isEmpty())
        assertTrue(Suggestions.EMPTY.suggest("a").isEmpty())
    }

    @Test
    fun nothingWhenOnlyTheTypedTextMatches() {
        val s = of("Can Yayınları", "Can Yayınları")
        assertTrue(s.suggest("Can Yayınları").isEmpty())
        assertTrue(s.suggest("  Can   Yayınları ").isEmpty())
        // another spelling of the same value is still offered: it corrects the case
        assertEquals(listOf("Can Yayınları"), s.suggest("can yayınları"))
        // a longer value that begins with the typed text is offered
        val longer = of("Can", "Can Yayınları")
        assertEquals(listOf("Can Yayınları"), longer.suggest("Can"))
    }

    @Test
    fun blankValuesAreSkipped() {
        val s = of("", "  ", "-", "Ada")
        assertEquals(1, s.size)
    }

    @Test
    fun collectsAuthorsAndPublishersOfBothLists() {
        val dad = BookTable(listOf(Book("A", "Fyodor Dostoyevski", publisher = "İletişim"), Book("B", "Fyodor Dostoyevski", publisher = "")))
        val me = BookTable(listOf(Book("C", "Orhan Pamuk", publisher = "İletişim Yayınları"), Book("D", "")))
        val authors = Suggestions.authors(listOf(dad, me))
        assertEquals(2, authors.size)
        assertEquals(listOf("Fyodor Dostoyevski"), authors.suggest("dosto"))
        assertEquals(listOf("İletişim", "İletişim Yayınları"), Suggestions.publishers(listOf(dad, me)).suggest("ileti"))
    }

    @Test
    fun highlightsMarkTheTypedBeginningsOfWords() {
        val s = Suggestions.EMPTY
        assertEquals(listOf(8..9), s.highlights("Türkiye İş Bankası Yayınları", "iş"))
        assertEquals(listOf(8..9, 11..13), s.highlights("Türkiye İş Bankası Yayınları", "is ban"))
        assertEquals(listOf(8..9, 11..13), s.highlights("Türkiye İş Bankası Yayınları", "ban iş"))
        assertEquals(listOf(0..4), s.highlights("Dostoyevski, Fyodor", "dosto"))
        assertTrue(s.highlights("Can", "").isEmpty())
        assertTrue(s.highlights("Can", "xyz").isEmpty())
    }

    @Test
    fun fastOnTheBigLibrary() {
        val books = (0 until 7000).map { Book("Kitap $it", "Yazar ${it % 2500} Soyad${it % 97}", publisher = "Yayınevi ${it % 400} Yayınları") }
        val table = BookTable(books)
        val started = System.nanoTime()
        val authors = Suggestions.authors(listOf(table))
        val builtMs = (System.nanoTime() - started) / 1_000_000
        val asked = System.nanoTime()
        repeat(20) { authors.suggest("yaz 12") }
        val askMs = (System.nanoTime() - asked) / 20_000_000
        println("suggestions: built in $builtMs ms, one key in $askMs ms")
        assertTrue("one key took $askMs ms", askMs < 50)
        assertTrue(authors.suggest("yazar 7").size <= Suggestions.MAX)
    }
}
