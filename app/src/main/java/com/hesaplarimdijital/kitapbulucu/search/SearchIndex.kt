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
    /** For suggestions: how many of the query words this book matched. */
    val matchedWords: Int,
)

/**
 * Word index over both libraries (ARCHITECTURE 9). Every query word must match a word of the
 * title or the author, in any order; words match whole, as a prefix, or inside a longer word
 * (3+ letters). When nothing matches that way, words of four letters or more may also match
 * with a typo (one for 4–7 letters, two for longer ones). Results come in ranking layers,
 * title matches first, Turkish-letter matches before ASCII ones, short titles first, then
 * Turkish alphabetical order.
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

    private class Query(val folded: String, val ascii: String, val tokens: List<String>)

    private class Hit(val entry: Entry, val layer: Int, val level: Int, val inTitle: Boolean, val worstKind: Int, val matched: Int)

    val size: Int get() = entries.size

    /** Books matching every word of [query]; typos are tolerated only when nothing matches without them. */
    fun search(query: String, owner: Owner? = null, limit: Int = DEFAULT_LIMIT): List<SearchHit> {
        val parsed = parse(query) ?: return emptyList()
        var matches = parsed.tokens.map { candidates(it, near = false, prefixNear = false) }
        var hits = collect(parsed, matches, owner, requireAll = true)
        if (hits.isEmpty() && parsed.tokens.any { it.length >= MIN_NEAR_LENGTH }) {
            val last = parsed.tokens.lastIndex
            matches = parsed.tokens.mapIndexed { i, token ->
                if (token.length >= MIN_NEAR_LENGTH) candidates(token, near = true, prefixNear = i == last) else matches[i]
            }
            hits = collect(parsed, matches, owner, requireAll = true)
        }
        hits.sortWith(HIT_ORDER)
        return hits.take(limit).map { it.toSearchHit() }
    }

    /** The closest books when [search] finds nothing: those matching the most query words, typos allowed. */
    fun suggest(query: String, owner: Owner? = null, count: Int = SUGGESTION_COUNT): List<SearchHit> {
        val parsed = parse(query) ?: return emptyList()
        val last = parsed.tokens.lastIndex
        val matches = parsed.tokens.mapIndexed { i, token -> candidates(token, near = token.length >= MIN_NEAR_LENGTH, prefixNear = i == last) }
        val hits = collect(parsed, matches, owner, requireAll = false)
        hits.sortWith(compareByDescending<Hit> { it.matched }.then(HIT_ORDER))
        return hits.take(count).map { it.toSearchHit() }
    }

    private fun Hit.toSearchHit() = SearchHit(entry.owner, entry.book, layer, level, inTitle, matched)

    private fun parse(query: String): Query? {
        val folded = TurkishText.squeeze(Fold.turkish(query))
        val tokens = Fold.words(folded, forQuery = true).take(MAX_QUERY_WORDS)
        if (tokens.isEmpty()) return null
        return Query(folded, Fold.ascii(folded), tokens)
    }

    private fun collect(query: Query, matches: List<IntArray>, owner: Owner?, requireAll: Boolean): ArrayList<Hit> {
        val hits = ArrayList<Hit>()
        for (id in entries.indices) {
            val entry = entries[id]
            if (owner != null && entry.owner != owner) continue
            var worstKind = 0
            var level = 1
            var inTitle = true
            var matched = 0
            for (match in matches) {
                val packed = match[id]
                if (packed < 0) {
                    if (requireAll) break
                    continue
                }
                matched++
                worstKind = maxOf(worstKind, packed shr 2)
                level = maxOf(level, ((packed shr 1) and 1) + 1)
                if ((packed and 1) == 1) inTitle = false
            }
            if (matched == 0 || (requireAll && matched < matches.size)) continue
            val layer = when {
                worstKind >= KIND_NEAR -> 5
                entry.title == query.folded || entry.author == query.folded -> 1
                entry.asciiTitle == query.ascii || entry.asciiAuthor == query.ascii -> { level = 2; 1 }
                entry.title.startsWith(query.folded) -> 2
                entry.asciiTitle.startsWith(query.ascii) -> { level = 2; 2 }
                worstKind <= KIND_PREFIX -> 3
                else -> 4
            }
            hits.add(Hit(entry, layer, level, inTitle, worstKind, matched))
        }
        return hits
    }

    /**
     * For one query word: per entry, the best match packed as `kind * 4 + (level - 1) * 2 + field`,
     * or -1 when the entry has no word matching it. Smaller is better. Kinds: 0 exact, 1 prefix,
     * 2 inside, 3 near by one letter, 4 near by two.
     */
    private fun candidates(token: String, near: Boolean, prefixNear: Boolean): IntArray {
        val best = IntArray(entries.size) { -1 }
        fun offer(wordIndex: Int, kind: Int) {
            val levelBits = (levels[wordIndex] - 1) * 2
            for (posting in postings[wordIndex]) {
                val id = posting shr 1
                val packed = kind * 4 + levelBits + (posting and 1)
                val old = best[id]
                if (old < 0 || packed < old) best[id] = packed
            }
        }
        var i = lowerBound(token)
        while (i < words.size && words[i].startsWith(token)) {
            offer(i, if (words[i].length == token.length) KIND_EXACT else KIND_PREFIX)
            i++
        }
        if (token.length >= MIN_CONTAINS_LENGTH) {
            for (j in words.indices) {
                val word = words[j]
                if (word.length > token.length && !word.startsWith(token) && word.contains(token)) offer(j, KIND_CONTAINS)
            }
        }
        if (near && token.length >= MIN_NEAR_LENGTH) {
            val bound = if (token.length >= TWO_TYPO_LENGTH) 2 else 1
            val distance = EditDistance()
            for (j in words.indices) {
                val word = words[j]
                val lengthGap = word.length - token.length
                if (lengthGap < -bound || (!prefixNear && lengthGap > bound)) continue
                val d = distance.bounded(token, word, bound, prefixNear)
                if (d in 1..bound) offer(j, KIND_NEAR + d - 1)
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
        const val SUGGESTION_COUNT = 3
        const val MIN_CONTAINS_LENGTH = 3
        const val MIN_NEAR_LENGTH = 4
        const val TWO_TYPO_LENGTH = 8
        const val MAX_QUERY_WORDS = 12

        private const val KIND_EXACT = 0
        private const val KIND_PREFIX = 1
        private const val KIND_CONTAINS = 2
        private const val KIND_NEAR = 3

        private val HIT_ORDER: Comparator<Hit> = Comparator { a, b ->
            when {
                a.layer != b.layer -> a.layer - b.layer
                a.inTitle != b.inTitle -> if (a.inTitle) -1 else 1
                a.level != b.level -> a.level - b.level
                a.worstKind != b.worstKind -> a.worstKind - b.worstKind // whole words before prefixes before insides before typos
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
