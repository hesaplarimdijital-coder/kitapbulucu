package com.hesaplarimdijital.kitapbulucu.search

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.text.Fold
import com.hesaplarimdijital.kitapbulucu.text.TurkishText
import java.text.CollationKey
import java.text.Collator
import java.util.Locale

/** How a query word met a book word, best first. */
enum class MatchKind { EXACT, PREFIX, CONTAINS, NEAR }

/** One book found for a query, with what the ranking knew about it. */
class SearchHit(
    val owner: Owner,
    val book: Book,
    /** 1 whole title or author equals the query, 2 title starts with it, 3 every word exact or prefix, 4 some word inside a word, 5 near. */
    val layer: Int,
    /** 1 matched with Turkish letters, 2 only after flattening them to ASCII. */
    val level: Int,
    /** True when every query word was found in the title. */
    val inTitle: Boolean,
)

/**
 * Word index over both libraries (ARCHITECTURE 9). Every query word must match a word of the
 * title or the author, in any order; words match whole, as a prefix, or inside a longer word
 * (3+ letters). Results come in ranking layers, title matches first, Turkish-letter matches
 * before ASCII ones, short titles first, then Turkish alphabetical order.
 */
class SearchIndex private constructor(
    private val entries: Array<Entry>,
    /** Sorted vocabulary: level 1 words and, where different, their level 2 (ASCII) forms. */
    private val words: Array<String>,
    private val levels: ByteArray,
    /** Per word, `(entry id shl 1) or field`, field 0 = title, 1 = author. */
    private val postings: Array<IntArray>,
) {
    private class Entry(
        val owner: Owner,
        val book: Book,
        val title: String,
        val author: String,
        val asciiTitle: String,
        val asciiAuthor: String,
        val sortKey: CollationKey,
    )

    val size: Int get() = entries.size

    fun search(query: String, owner: Owner? = null, limit: Int = DEFAULT_LIMIT): List<SearchHit> {
        val folded = TurkishText.squeeze(Fold.turkish(query))
        val tokens = Fold.words(folded, forQuery = true)
        if (tokens.isEmpty()) return emptyList()
        val ascii = Fold.ascii(folded)
        val matches = tokens.map { candidates(it) }
        val hits = ArrayList<Hit>()
        for (id in entries.indices) {
            val entry = entries[id]
            if (owner != null && entry.owner != owner) continue
            var worstKind = 0
            var level = 1
            var inTitle = true
            var found = true
            for (match in matches) {
                val packed = match[id]
                if (packed < 0) {
                    found = false
                    break
                }
                worstKind = maxOf(worstKind, packed shr 2)
                level = maxOf(level, ((packed shr 1) and 1) + 1)
                if ((packed and 1) == 1) inTitle = false
            }
            if (!found) continue
            val layer = when {
                entry.title == folded || entry.author == folded -> 1
                entry.asciiTitle == ascii || entry.asciiAuthor == ascii -> { level = 2; 1 }
                entry.title.startsWith(folded) -> 2
                entry.asciiTitle.startsWith(ascii) -> { level = 2; 2 }
                worstKind <= MatchKind.PREFIX.ordinal -> 3
                worstKind == MatchKind.CONTAINS.ordinal -> 4
                else -> 5
            }
            hits.add(Hit(entry, layer, level, inTitle, worstKind))
        }
        hits.sortWith(HIT_ORDER)
        return hits.take(limit).map { SearchHit(it.entry.owner, it.entry.book, it.layer, it.level, it.inTitle) }
    }

    private class Hit(val entry: Entry, val layer: Int, val level: Int, val inTitle: Boolean, val worstKind: Int)

    /**
     * For one query word: per entry, the best match packed as `kind * 4 + (level - 1) * 2 + field`,
     * or -1 when the entry has no word matching it. Smaller is better.
     */
    private fun candidates(token: String): IntArray {
        val best = IntArray(entries.size) { -1 }
        fun offer(wordIndex: Int, kind: MatchKind) {
            val levelBits = (levels[wordIndex] - 1) * 2
            for (posting in postings[wordIndex]) {
                val id = posting shr 1
                val packed = kind.ordinal * 4 + levelBits + (posting and 1)
                val old = best[id]
                if (old < 0 || packed < old) best[id] = packed
            }
        }
        var i = lowerBound(token)
        while (i < words.size && words[i].startsWith(token)) {
            offer(i, if (words[i].length == token.length) MatchKind.EXACT else MatchKind.PREFIX)
            i++
        }
        if (token.length >= MIN_CONTAINS_LENGTH) {
            for (j in words.indices) {
                val word = words[j]
                if (word.length > token.length && !word.startsWith(token) && word.contains(token)) offer(j, MatchKind.CONTAINS)
            }
        }
        return best
    }

    /** First vocabulary index whose word is not smaller than [token]. */
    private fun lowerBound(token: String): Int {
        var low = 0
        var high = words.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (words[mid] < token) low = mid + 1 else high = mid
        }
        return low
    }

    companion object {
        const val DEFAULT_LIMIT = 200
        const val MIN_CONTAINS_LENGTH = 3

        private val HIT_ORDER: Comparator<Hit> = Comparator { a, b ->
            when {
                a.layer != b.layer -> a.layer - b.layer
                a.inTitle != b.inTitle -> if (a.inTitle) -1 else 1
                a.level != b.level -> a.level - b.level
                a.worstKind != b.worstKind -> a.worstKind - b.worstKind // whole words before prefixes before insides
                a.entry.title.length != b.entry.title.length -> a.entry.title.length - b.entry.title.length
                else -> a.entry.sortKey.compareTo(b.entry.sortKey)
            }
        }

        fun build(libraries: Map<Owner, List<Book>>): SearchIndex {
            val collator = Collator.getInstance(Locale("tr", "TR"))
            val entries = ArrayList<Entry>()
            val level1 = HashMap<String, ArrayList<Int>>()
            for (owner in Owner.entries) {
                for (book in libraries[owner].orEmpty()) {
                    val id = entries.size
                    val title = TurkishText.squeeze(Fold.turkish(book.title))
                    val author = TurkishText.squeeze(Fold.turkish(book.author))
                    entries.add(Entry(owner, book, title, author, Fold.ascii(title), Fold.ascii(author), collator.getCollationKey(book.title)))
                    for (word in Fold.words(title)) level1.getOrPut(word) { ArrayList() }.add(id shl 1)
                    for (word in Fold.words(author)) level1.getOrPut(word) { ArrayList() }.add((id shl 1) or 1)
                }
            }
            val level2 = HashMap<String, ArrayList<Int>>()
            for ((word, posts) in level1) {
                val ascii = Fold.ascii(word)
                if (ascii != word) level2.getOrPut(ascii) { ArrayList() }.addAll(posts)
            }
            val vocabulary = ArrayList<Triple<String, Byte, IntArray>>(level1.size + level2.size)
            for ((word, posts) in level1) vocabulary.add(Triple(word, 1.toByte(), posts.distinct().sorted().toIntArray()))
            for ((word, posts) in level2) vocabulary.add(Triple(word, 2.toByte(), posts.distinct().sorted().toIntArray()))
            vocabulary.sortWith(compareBy({ it.first }, { it.second }))
            return SearchIndex(
                entries.toTypedArray(),
                Array(vocabulary.size) { vocabulary[it].first },
                ByteArray(vocabulary.size) { vocabulary[it].second },
                Array(vocabulary.size) { vocabulary[it].third },
            )
        }
    }
}
