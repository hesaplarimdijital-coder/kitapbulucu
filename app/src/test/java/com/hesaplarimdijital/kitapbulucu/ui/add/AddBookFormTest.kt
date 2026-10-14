package com.hesaplarimdijital.kitapbulucu.ui.add

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddBookFormTest {
    private val dad = BookTable(listOf(Book("Hâlâ Bekleyen Mektup", "Şükrü Ilgaz", "A2"), Book("Kar", "Orhan Pamir", "", signed = true)), listOf("Dil"))

    @Test
    fun needsATitleAndASensibleCount() {
        assertEquals(AddBookForm.Check.NeedTitle, AddBookForm.check(AddBookFields(title = "  "), Owner.DAD, dad))
        assertEquals(AddBookForm.Check.BadCopies, AddBookForm.check(AddBookFields(title = "Kar Tanesi", copies = "0"), Owner.DAD, dad))
        assertEquals(AddBookForm.Check.BadCopies, AddBookForm.check(AddBookFields(title = "Kar Tanesi", copies = "iki"), Owner.DAD, dad))
        val empty = AddBookForm.check(AddBookFields(title = "Kar Tanesi", copies = ""), Owner.DAD, dad)
        assertEquals(1, (empty as AddBookForm.Check.Ready).book.copies)
    }

    @Test
    fun readyBooksAreTidyAndCarryTheExtraColumns() {
        val ready = AddBookForm.check(
            AddBookFields(title = "  Kar   Tanesi ", author = "Orhan  Pamir", owner = Owner.DAD, shelf = " B4 ", copies = "2", isbn = " 978 ", note = "x"),
            Owner.DAD, dad,
        ) as AddBookForm.Check.Ready
        assertEquals(Book("Kar Tanesi", "Orhan Pamir", "B4", 2, isbn = "978", note = "x", extra = listOf("")), ready.book)
    }

    @Test
    fun warnsAboutABookAlreadyThere() {
        val exists = AddBookForm.check(AddBookFields(title = "HALA BEKLEYEN MEKTUP", author = "şükrü ılgaz"), Owner.DAD, dad)
        assertTrue(exists is AddBookForm.Check.Exists)
        assertEquals("A2", (exists as AddBookForm.Check.Exists).existing.shelf)
        assertTrue(AddBookForm.check(AddBookFields(title = "Hâlâ Bekleyen Mektup", author = "Şükrü Ilgaz"), Owner.DAD, dad, confirmedDuplicate = true) is AddBookForm.Check.Ready)
        // a signed copy is a different book from an unsigned one
        assertTrue(AddBookForm.check(AddBookFields(title = "Kar", author = "Orhan Pamir"), Owner.DAD, dad) is AddBookForm.Check.Ready)
        assertTrue(AddBookForm.check(AddBookFields(title = "Kar", author = "Orhan Pamir", signed = true), Owner.DAD, dad) is AddBookForm.Check.Exists)
    }

    @Test
    fun asksBeforeAddingToTheOtherList() {
        val fields = AddBookFields(title = "Kar Tanesi", owner = Owner.ME)
        assertTrue(AddBookForm.check(fields, Owner.DAD, null) is AddBookForm.Check.OtherList)
        assertTrue(AddBookForm.check(fields, Owner.DAD, null, confirmedOther = true) is AddBookForm.Check.Ready)
        assertTrue(AddBookForm.check(fields, Owner.ME, null) is AddBookForm.Check.Ready)
        assertTrue(AddBookForm.check(fields, null, null) is AddBookForm.Check.Ready) // nobody chosen yet: no question
    }

    @Test
    fun theNextBookKeepsListAndShelf() {
        val next = AddBookFields(title = "Kar", author = "Orhan Pamir", owner = Owner.ME, shelf = "B4", copies = "3", signed = true, note = "x").next()
        assertEquals(AddBookFields(owner = Owner.ME, shelf = "B4"), next)
    }
}
