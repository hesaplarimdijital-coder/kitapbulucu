package com.hesaplarimdijital.kitapbulucu.search

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import org.junit.Assert.assertEquals
import org.junit.Test

class BookGroupsTest {

    @Test
    fun theSameBookInBothLibrariesIsOneGroup() {
        val index = SearchIndex.build(
            mapOf(
                Owner.DAD to listOf(Book("Çatıdaki Rasathane", "Ayşe İnce", "C2", 3), Book("Çatıdaki Rasathane", "Ayşe İnce", "C3", signed = true)),
                Owner.ME to listOf(Book("ÇATIDAKİ  RASATHANE", "AYŞE İNCE", "1B"), Book("Çatıdaki Kuşların Son Şarkısı", "Ferit Ağaoğlu", "3C")),
            ),
        )
        val groups = BookGroups.group(index.search("çatıdaki"))
        assertEquals(listOf("Çatıdaki Rasathane", "Çatıdaki Kuşların Son Şarkısı"), groups.map { it.title })
        assertEquals(listOf(Owner.DAD, Owner.DAD, Owner.ME), groups[0].holdings.map { it.owner })
        assertEquals(listOf("C2", "C3", "1B"), groups[0].holdings.map { it.book.shelf })
        assertEquals(5, groups[0].totalCopies)
        assertEquals(1, groups[1].holdings.size)
    }

    @Test
    fun differentAuthorsStayApart() {
        val index = SearchIndex.build(mapOf(Owner.DAD to listOf(Book("Kar", "Orhan Pamir"), Book("Kar", "Leyla Umut"))))
        assertEquals(2, BookGroups.group(index.search("kar")).size)
        assertEquals(emptyList<BookGroup>(), BookGroups.group(emptyList()))
    }
}
