package com.hesaplarimdijital.kitapbulucu.store

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The edits of the one-time title case fix, and how fast thousands of them are applied. */
class TitleCaseFixTest {

    private val upper = Book("ÇATIDAKİ RASATHANE", "AYŞE İNCE", "C2", 2, signed = true, "MAVİ YAYINLARI", "1998", "9750000000001", "SAYFA KENARI YIRTIK", listOf("DEDEMDEN"))

    @Test
    fun onlyTitleAndAuthorChange() {
        val fixed = TitleCaseFix.fixed(upper)
        assertEquals(upper.copy(title = "Çatıdaki Rasathane", author = "Ayşe İnce"), fixed)
        // publisher, note and unknown columns stay in capitals as they were
        assertEquals("MAVİ YAYINLARI", fixed.publisher)
        assertEquals("SAYFA KENARI YIRTIK", fixed.note)
        assertEquals(listOf("DEDEMDEN"), fixed.extra)
    }

    @Test
    fun booksThatStayTheSameGiveNoEdit() {
        val books = listOf(
            upper,
            Book("Sefil Fenerciler", "Halit Oruç"),          // typed by hand already
            Book("Kar Tanesi", "ORHAN PAMİR"),                // only the author
            Book("MKE", ""),                                  // an abbreviation stays as it is
            Book("1984", ""),                                 // no letters
        )
        val edits = TitleCaseFix.edits(Owner.DAD, books)
        assertEquals(2, edits.size)
        assertEquals(upper, edits[0].before)
        assertEquals(Book("Kar Tanesi", "Orhan Pamir"), edits[1].after)
        assertTrue(edits.all { it.owner == Owner.DAD })
    }

    @Test
    fun aPlanCountsPerListAndPicksExamples() {
        val plan = TitleCaseFix.plan(
            mapOf(
                Owner.DAD to listOf(upper, Book("SUÇ VE KUM", "LEYLA UMUT"), Book("Mavi Defter", "Nur Akın")),
                Owner.ME to listOf(Book("İSTANBUL'UN SAATLERİ", "Nur Akın")),
            ),
        )
        assertEquals(2, plan.count(Owner.DAD))
        assertEquals(1, plan.count(Owner.ME))
        assertEquals(3, plan.total)
        assertEquals(3, plan.changes.size)
        assertEquals(
            listOf("ÇATIDAKİ RASATHANE" to "Çatıdaki Rasathane", "AYŞE İNCE" to "Ayşe İnce", "SUÇ VE KUM" to "Suç ve Kum"),
            plan.examples,
        )
        assertEquals(0, TitleCaseFix.plan(mapOf(Owner.ME to listOf(Book("Mavi Defter", "Nur Akın")))).total)
        assertTrue(TitleCaseFix.plan(emptyMap()).examples.isEmpty())
    }

    /** About as many as the father's list: worked out and applied (twice, as the screens and the write do) well within a second or two. */
    @Test
    fun sevenThousandEditsApplyQuickly() {
        val books = List(7_000) { i -> Book("UYDURMA KİTAP NUMARA ${i / 2} ${if (i % 2 == 0) "BİR" else "İKİ"}", "YAZAR ${i % 300}", "R${i % 40}") }
        val table = BookTable(books, listOf("Bağışlayan"))
        val started = System.nanoTime()
        val edits = TitleCaseFix.edits(Owner.DAD, books)
        val applied = ChangeApplier.apply(table, edits)
        val again = ChangeApplier.apply(table, edits)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertEquals(7_000, edits.size)
        assertTrue(applied.notes.all { it.outcome == ChangeApplier.Outcome.APPLIED })
        assertEquals(books.map(TitleCaseFix::fixed), applied.table.books)
        assertEquals(applied.table.books, again.table.books)
        assertEquals(listOf("Bağışlayan"), applied.table.extraHeaders)
        assertTrue("took $millis ms", millis < 3_000)
    }

    /** The lookup by position must stay right after rows change identity and are appended. */
    @Test
    fun editsStillFindTheirRowsAfterOtherEditsAndAdds() {
        val a = Book("Ada", "Bir")
        val b = Book("Bora", "İki")
        val c = Book("Cem", "Üç")
        val table = BookTable(listOf(a, b, c, a.copy(shelf = "X")))
        val result = ChangeApplier.apply(
            table,
            listOf(
                Change.Edit(Owner.ME, b, b.copy(title = "Bora Yeni")),         // identity changes
                Change.Add(Owner.ME, Book("Defne", "Dört")),                   // appended
                Change.Edit(Owner.ME, b.copy(title = "Bora Yeni"), b.copy(title = "Bora Yeni", shelf = "Y")),
                Change.Edit(Owner.ME, a.copy(shelf = "X"), a.copy(shelf = "Z")), // the second row of the same identity
                Change.Edit(Owner.ME, Book("Defne", "Dört"), Book("Defne", "Dört", "K1")),
                Change.Remove(Owner.ME, c),                                    // from here on rows are looked for one by one
                Change.Edit(Owner.ME, Book("Defne", "Dört", "K1"), Book("Defne", "Dört", "K2")),
            ),
        )
        assertTrue(result.notes.all { it.outcome == ChangeApplier.Outcome.APPLIED })
        assertEquals(listOf(a, b.copy(title = "Bora Yeni", shelf = "Y"), a.copy(shelf = "Z"), Book("Defne", "Dört", "K2")), result.table.books)
    }
}
