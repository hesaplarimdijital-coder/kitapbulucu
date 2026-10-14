package com.hesaplarimdijital.kitapbulucu.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ShelvesTest {
    @Test
    fun groupsShelvesNaturallyWithUnshelvedLast() {
        val books = listOf(
            Book("a", shelf = "A10"), Book("b", shelf = "A2"), Book("c"), Book("d", shelf = "b3"), Book("e", shelf = "B3 "),
            Book("f", shelf = "Ç1"), Book("g", shelf = "C9"), Book("h", copies = 2),
        )
        val groups = Shelves.group(books)
        assertEquals(listOf("A2", "A10", "b3", "C9", "Ç1", ""), groups.map { it.shelf })
        assertEquals(listOf(3, 4), groups[2].books.map { it.index })
        assertEquals(3, groups.last().copies)
        assertEquals(2, Shelves.unshelvedCount(books))
    }

    @Test
    fun theSameShelfWrittenDifferentlyIsOne() {
        assertEquals(Shelves.key("b 3"), Shelves.key(" B3"))
        assertEquals("İ2", Shelves.key("i2"))
        assertEquals(1, Shelves.group(listOf(Book("a", shelf = "i2"), Book("b", shelf = "İ2"))).size)
    }
}
