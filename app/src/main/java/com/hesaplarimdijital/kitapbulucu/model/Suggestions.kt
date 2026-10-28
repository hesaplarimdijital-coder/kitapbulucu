package com.hesaplarimdijital.kitapbulucu.model

import com.hesaplarimdijital.kitapbulucu.text.Fold
import com.hesaplarimdijital.kitapbulucu.text.TurkishText
import java.text.Collator
import java.util.Locale

/**
 * Suggestions for the free-text fields of the add form (Yazar, Yayınevi): the distinct values both
 * libraries already hold, found again from a few typed letters.
 *
 * A value matches when every word of the query is the start of some word of the value ("iş" and
 * "is ban" both find "Türkiye İş Bankası Yayınları"). Matching is Turkish aware ([Fold]): case,
 * `I/ı/İ/i` and circumflexes do not matter. A query without Turkish letters also matches the
 * Turkish letters ("is" finds "İş"); a query that has some ("iş") matches strictly, so it does not
 * find "Isparta".
 *
 * Order: values that begin with the query first, then the other word-start matches; inside each
 * group the value used by most books first, then alphabetical (Turkish collation). Spellings that
 * fold to the same text ("Iş Bankası", "İş bankası") are one value, shown in the spelling most
 * books use. Build once per change of the lists (it is cheap, but not free: ~7,000 books), ask per key.
 */
class Suggestions private constructor(private val entries: List<Entry>) {

    private class Entry(
        /** The spelling shown. */
        val text: String,
        /** How many books use this value, in any of its spellings. */
        val uses: Int,
        /** The whole value folded at level 1 and level 2: for the "begins with" test. */
        val whole1: String,
        val whole2: String,
        /** The words of the value at level 1 and level 2. */
        val words1: List<String>,
        val words2: List<String>,
    )

    val size: Int get() = entries.size

    /** Up to [limit] values for the typed [query]; empty for an empty query or when nothing but the typed text itself matches. */
    fun suggest(query: String, limit: Int = MAX): List<String> {
        val typed = TurkishText.squeeze(query)
        val q = Query.of(typed) ?: return emptyList()
        val hits = ArrayList<Pair<Entry, Int>>()
        for (entry in entries) {
            if (entry.text == typed) continue
            val words = if (q.strict) entry.words1 else entry.words2
            if (!q.words.all { w -> words.any { it.startsWith(w) } }) continue
            val whole = if (q.strict) entry.whole1 else entry.whole2
            hits.add(entry to if (whole.startsWith(q.whole)) 0 else 1)
        }
        if (hits.isEmpty()) return emptyList()
        hits.sortWith(
            compareBy<Pair<Entry, Int>> { it.second }
                .thenByDescending { it.first.uses }
                .thenComparator { a, b -> COLLATOR.compare(a.first.text, b.first.text) }
                .thenComparator { a, b -> a.first.text.compareTo(b.first.text) },
        )
        return hits.take(limit).map { it.first.text }
    }

    /**
     * The parts of [text] that the [query] matched, as character ranges, for showing them in bold:
     * each query word marks the beginning of the first still unmarked word it starts.
     */
    fun highlights(text: String, query: String): List<IntRange> {
        val q = Query.of(TurkishText.squeeze(query)) ?: return emptyList()
        val tokens = ArrayList<IntRange>()
        var start = -1
        for (i in 0..text.length) {
            val inWord = i < text.length && text[i].isLetterOrDigit()
            if (inWord && start < 0) start = i
            if (!inWord && start >= 0) {
                tokens.add(start until i)
                start = -1
            }
        }
        val used = BooleanArray(tokens.size)
        val marks = ArrayList<IntRange>()
        for (word in q.words) {
            val at = tokens.indices.firstOrNull { index ->
                if (used[index]) return@firstOrNull false
                val token = tokens[index]
                val folded = fold(text.substring(token.first, token.last + 1), q.strict)
                folded.length >= word.length && folded.startsWith(word)
            } ?: continue
            used[at] = true
            marks.add(tokens[at].first until tokens[at].first + word.length)
        }
        return marks.sortedBy { it.first }
    }

    private class Query(val words: List<String>, val whole: String, val strict: Boolean) {
        companion object {
            fun of(typed: String): Query? {
                val strict = Fold.hasTurkishLetters(typed)
                val words = Fold.words(typed, forQuery = true).map { if (strict) it else Fold.ascii(it) }
                if (words.isEmpty()) return null
                return Query(words, words.joinToString(" "), strict)
            }
        }
    }

    companion object {
        const val MAX = 6

        val EMPTY = Suggestions(emptyList())

        private val COLLATOR: Collator = Collator.getInstance(Locale("tr", "TR"))

        private fun fold(text: String, strict: Boolean) = if (strict) Fold.turkish(text) else Fold.ascii(text)

        /** The distinct values of [values] (blank ones skipped), with how often each is used. */
        fun of(values: Sequence<String>): Suggestions {
            // folded whole value -> spelling -> number of books
            val groups = HashMap<String, HashMap<String, Int>>()
            for (raw in values) {
                val text = TurkishText.squeeze(raw)
                val key = Fold.words(text, forQuery = true).joinToString(" ")
                if (key.isEmpty()) continue
                val spellings = groups.getOrPut(key) { HashMap() }
                spellings[text] = (spellings[text] ?: 0) + 1
            }
            val entries = groups.map { (key, spellings) ->
                val best = spellings.entries.sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key },
                ).first().key
                val words1 = Fold.words(best)
                Entry(
                    text = best,
                    uses = spellings.values.sum(),
                    whole1 = key,
                    whole2 = Fold.ascii(key),
                    words1 = words1,
                    words2 = words1.map { Fold.ascii(it) },
                )
            }
            return Suggestions(entries)
        }

        fun authors(tables: Iterable<BookTable>): Suggestions = of(tables.asSequence().flatMap { it.books.asSequence() }.map { it.author })

        fun publishers(tables: Iterable<BookTable>): Suggestions = of(tables.asSequence().flatMap { it.books.asSequence() }.map { it.publisher })
    }
}
