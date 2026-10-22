package com.hesaplarimdijital.kitapbulucu.search

import com.hesaplarimdijital.kitapbulucu.text.Fold
import com.hesaplarimdijital.kitapbulucu.text.TurkishText

/** One book as the search screen shows it: every row that holds it, in either library. */
class BookGroup(val title: String, val author: String, val holdings: List<SearchHit>) {
    val totalCopies: Int get() = holdings.sumOf { it.book.copies }
}

object BookGroups {
    /**
     * Hits of the same title and author (ignoring case, Turkish letters and spacing) become one
     * group, in the order of their best hit: "MKE · Raf C2" and "Barış · Raf 1B" on one card.
     * Within a group MKE's rows come first.
     */
    fun group(hits: List<SearchHit>): List<BookGroup> {
        val order = LinkedHashMap<String, MutableList<SearchHit>>()
        for (hit in hits) order.getOrPut(key(hit.book.title, hit.book.author)) { ArrayList() }.add(hit)
        return order.values.map { found ->
            val holdings = found.sortedBy { it.owner.ordinal } // MKE first, then Barış; hit order within each
            BookGroup(holdings.first().book.title, holdings.first().book.author, holdings)
        }
    }

    fun key(title: String, author: String): String =
        Fold.ascii(TurkishText.squeeze(title)) + "\u0000" + Fold.ascii(TurkishText.squeeze(author))
}
