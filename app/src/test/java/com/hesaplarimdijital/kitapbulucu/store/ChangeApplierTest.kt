package com.hesaplarimdijital.kitapbulucu.store

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.ChangeApplier.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** The conflict rules of ARCHITECTURE 7, on a table as someone else may have left it. */
class ChangeApplierTest {

    private val sefil = Book("Sefil Fenerciler", "Halit Oruç", "A1", 2)
    private val rasathane = Book("Çatıdaki Rasathane", "Ayşe İnce", "C2", 1, note = "Kapak yırtık")
    private val signedRasathane = rasathane.copy(signed = true, copies = 1, shelf = "C3")
    private val table = BookTable(listOf(sefil, rasathane, signedRasathane), listOf("Dil"))

    private fun outcomes(vararg changes: Change) = ChangeApplier.apply(table, changes.toList()).notes.map { it.outcome }
    private fun books(vararg changes: Change) = ChangeApplier.apply(table, changes.toList()).table.books

    @Test
    fun addAppendsOrRaisesTheCount() {
        val fresh = Book("Kar Tanesi", "Orhan Pamir", "B4")
        assertEquals(listOf(Outcome.APPLIED), outcomes(Change.Add(Owner.DAD, fresh)))
        assertEquals(table.books + fresh, books(Change.Add(Owner.DAD, fresh)))

        val again = Book("SEFİL  FENERCİLER", "halit oruc", "Z9", 3) // the same book, typed differently
        assertEquals(listOf(Outcome.MERGED_INTO_EXISTING), outcomes(Change.Add(Owner.DAD, again)))
        assertEquals(sefil.copy(copies = 5), books(Change.Add(Owner.DAD, again))[0]) // shelf and the rest stay as the file has them

        val signedSefil = sefil.copy(signed = true, copies = 1)
        assertEquals(listOf(Outcome.APPLIED), outcomes(Change.Add(Owner.DAD, signedSefil))) // a signed copy is its own row
        assertEquals(4, books(Change.Add(Owner.DAD, signedSefil)).size)
    }

    @Test
    fun editFindsTheRowExactlyThenLoosely() {
        val moved = sefil.copy(shelf = "B2")
        assertEquals(listOf(Outcome.APPLIED), outcomes(Change.Edit(Owner.DAD, sefil, moved)))
        assertEquals(moved, books(Change.Edit(Owner.DAD, sefil, moved))[0])

        // someone changed the note in Excel meanwhile; the user only moved the shelf
        val stale = sefil.copy(note = "eski not")
        val edit = Change.Edit(Owner.DAD, stale, stale.copy(shelf = "B2"))
        assertEquals(listOf(Outcome.EDITED_LOOSE_MATCH), outcomes(edit))
        assertEquals(sefil.copy(shelf = "B2"), books(edit)[0]) // the file's note survives, the shelf changes

        // the signed and unsigned rows are different books to an edit too
        val signedEdit = Change.Edit(Owner.DAD, signedRasathane.copy(note = "x"), signedRasathane.copy(note = "x", shelf = "D1"))
        assertEquals("D1", books(signedEdit)[2].shelf)
        assertEquals("C2", books(signedEdit)[1].shelf)
    }

    @Test
    fun editOfAVanishedRowAddsItBack() {
        val gone = Book("Kar Tanesi", "Orhan Pamir", "B4")
        val edit = Change.Edit(Owner.DAD, gone, gone.copy(shelf = "B5"))
        assertEquals(listOf(Outcome.ADDED_INSTEAD_OF_EDIT), outcomes(edit))
        assertEquals(gone.copy(shelf = "B5"), books(edit).last())
    }

    @Test
    fun removeTakesTheRowOrReportsItGone() {
        assertEquals(listOf(Outcome.APPLIED), outcomes(Change.Remove(Owner.DAD, sefil)))
        assertEquals(listOf(rasathane, signedRasathane), books(Change.Remove(Owner.DAD, sefil)))

        val loose = Change.Remove(Owner.DAD, rasathane.copy(note = "başka not"))
        assertEquals(listOf(Outcome.APPLIED), outcomes(loose))
        assertEquals(listOf(sefil, signedRasathane), books(loose)) // the unsigned one goes, the signed one stays

        val gone = Change.Remove(Owner.DAD, Book("Kar Tanesi", "Orhan Pamir"))
        assertEquals(listOf(Outcome.ALREADY_GONE), outcomes(gone))
        assertEquals(table.books, books(gone))
    }

    @Test
    fun aMoveIsAnAddThenARemove() {
        val target = ChangeApplier.apply(BookTable(emptyList()), listOf(Change.Add(Owner.ME, sefil)))
        assertEquals(listOf(sefil), target.table.books)
        val source = ChangeApplier.apply(table, listOf(Change.Remove(Owner.DAD, sefil)))
        assertEquals(2, source.table.books.size)
    }

    @Test
    fun changesApplyInOrderAndKeepExtraHeaders() {
        val fresh = Book("Kar Tanesi", "Orhan Pamir", "B4")
        val result = ChangeApplier.apply(
            table,
            listOf(Change.Add(Owner.DAD, fresh), Change.Edit(Owner.DAD, fresh, fresh.copy(shelf = "B5")), Change.Remove(Owner.DAD, sefil)),
        )
        assertEquals(listOf(Outcome.APPLIED, Outcome.APPLIED, Outcome.APPLIED), result.notes.map { it.outcome })
        assertEquals(listOf(rasathane, signedRasathane, fresh.copy(shelf = "B5")), result.table.books)
        assertEquals(listOf("Dil"), result.table.extraHeaders)
        assertEquals(emptyList<ChangeApplier.Note>(), result.surprises)
    }

    @Test
    fun previewIsTheSameTableWithoutChanges() {
        assertEquals(table, ChangeApplier.preview(table, emptyList()))
        assertEquals(null, ChangeApplier.preview(null, listOf(Change.Add(Owner.DAD, sefil))))
        assertEquals(4, ChangeApplier.preview(table, listOf(Change.Add(Owner.DAD, Book("Kar", "Orhan Pamir"))))!!.books.size)
    }

    @Test
    fun identityIgnoresCaseLettersAndSpacing() {
        assertEquals(ChangeApplier.identity(Book("Çatıdaki Rasathane", "Ayşe İnce")), ChangeApplier.identity(Book("  CATIDAKI  RASATHANE ", "ayse ince")))
        assert(ChangeApplier.identity(rasathane) != ChangeApplier.identity(signedRasathane))
        assert(ChangeApplier.identity(Book("Kar", "Orhan Pamir")) != ChangeApplier.identity(Book("Kar", "Leyla Umut")))
    }
}
