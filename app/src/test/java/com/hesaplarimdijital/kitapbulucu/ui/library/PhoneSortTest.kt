package com.hesaplarimdijital.kitapbulucu.ui.library

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PhoneSortTest {

    private fun rows(vararg books: Book) = books.mapIndexed { i, b -> LibraryRow(Owner.DAD, i, b) }

    private fun titles(list: List<LibraryRow>) = list.map { it.book.title }

    @Test
    fun titlesInTurkishOrder() {
        val list = rows(Book("Zaman"), Book("Çiçek"), Book("Cam"), Book("İnce"), Book("Işık"), Book("Ölü"), Book("Oda"), Book("şiir"), Book("Sabah"))
        assertEquals(listOf("Cam", "Çiçek", "Işık", "İnce", "Oda", "Ölü", "Sabah", "şiir", "Zaman"), titles(sortRows(list, PhoneSort.TITLE)))
    }

    @Test
    fun equalTitlesKeepTheFileOrder() {
        val list = rows(Book("Aynı", "B"), Book("Aynı", "A"))
        assertEquals(listOf(0, 1), sortRows(list, PhoneSort.TITLE).map { it.position })
    }

    @Test
    fun authorsWithBlanksLast() {
        val list = rows(Book("X", ""), Book("Y", "Ülkü Tamer"), Book("Z", "Ahmet Ümit"), Book("W", "Ahmet Ümit"))
        assertEquals(listOf("W", "Z", "Y", "X"), titles(sortRows(list, PhoneSort.AUTHOR)))
    }

    @Test
    fun shelvesNaturalOrderUnshelvedLast() {
        val list = rows(Book("a", shelf = "B10"), Book("b", shelf = ""), Book("c", shelf = "B2"), Book("d", shelf = "a1"), Book("e", shelf = "b2"))
        assertEquals(listOf("d", "c", "e", "a", "b"), titles(sortRows(list, PhoneSort.SHELF)))
    }

    @Test
    fun fileOrderIsTheListItself() {
        val list = rows(Book("B"), Book("A"))
        assertSame(list, sortRows(list, PhoneSort.FILE))
    }
}
