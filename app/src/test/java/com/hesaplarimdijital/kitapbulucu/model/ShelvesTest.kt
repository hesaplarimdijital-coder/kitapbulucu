package com.hesaplarimdijital.kitapbulucu.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun overviewJoinsCreatedAndUsedShelvesInNaturalOrder() {
        val books = listOf(Book("a", shelf = "A10"), Book("b", shelf = "b3"), Book("c"), Book("d", shelf = "A2"))
        val catalog = listOf(ShelfEntry("C1", "Salon"), ShelfEntry("B3", "Koridor"), ShelfEntry("A9"))
        val groups = Shelves.overview(books, catalog)
        assertEquals(listOf("A2", "A9", "A10", "B3", "C1", ""), groups.map { it.shelf })
        assertEquals(listOf(1, 0, 1, 1, 0, 1), groups.map { it.books.size })
        // a created shelf keeps its spelling and description, the books stay with it
        val b3 = groups[3]
        assertEquals("Koridor", b3.note)
        assertTrue(b3.saved)
        assertEquals(listOf(1), b3.books.map { it.index })
        assertFalse(b3.canDelete)
        assertTrue(groups[1].canDelete) // A9: created and empty
        assertFalse(groups[0].saved) // A2: only the books name it
        assertFalse(groups[0].canDelete)
        assertTrue(groups.last().isUnshelved)
        assertFalse(groups.last().canDelete)
    }

    @Test
    fun overviewWithoutCreatedShelvesIsTheBooksShelves() {
        val books = listOf(Book("a", shelf = "B1"), Book("b"))
        assertEquals(Shelves.group(books).map { it.shelf }, Shelves.overview(books, emptyList()).map { it.shelf })
        assertEquals(listOf("D1"), Shelves.overview(emptyList(), listOf(ShelfEntry("D1"))).map { it.shelf })
        assertEquals(emptyList<String>(), Shelves.overview(emptyList(), emptyList()).map { it.shelf })
    }

    @Test
    fun aNewShelfCodeMustBeWrittenAndNew() {
        val existing = listOf("A1", "b3")
        assertEquals(Shelves.NameProblem.EMPTY, Shelves.checkName("  ", existing))
        assertEquals(Shelves.NameProblem.TAKEN, Shelves.checkName("B 3", existing))
        assertEquals(Shelves.NameProblem.TAKEN, Shelves.checkName("a1", existing))
        assertNull(Shelves.checkName("B4", existing))
        // renaming may change only the spelling, but not take another shelf's code
        assertNull(Shelves.checkName("B3", existing, renaming = "b3"))
        assertEquals(Shelves.NameProblem.TAKEN, Shelves.checkName("A1", existing, renaming = "b3"))
        assertNull(Shelves.checkName("C7", existing, renaming = "b3"))
        assertEquals("B 3", Shelves.clean("  B   3 "))
    }
}
