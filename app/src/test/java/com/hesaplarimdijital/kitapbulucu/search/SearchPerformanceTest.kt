package com.hesaplarimdijital.kitapbulucu.search

import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Builds 10,000 made-up books and types a few queries letter by letter. Every keystroke must
 * answer in under 50 ms (ARCHITECTURE 9); the times are printed so a slow-down is visible in
 * the CI log before it crosses the line.
 */
class SearchPerformanceTest {

    @Test
    fun tenThousandBooksAnswerEveryKeystrokeFast() {
        val random = Random(20261012)
        val pool = wordPool(random, 12_000)
        fun word() = pool[(random.nextDouble().let { it * it } * pool.size).toInt()] // skewed: common words repeat as in real lists
        fun title() = (1..1 + random.nextInt(4)).joinToString(" ") { word().replaceFirstChar { it.titlecase() } }
        fun author() = word().replaceFirstChar { it.titlecase() } + " " + word().replaceFirstChar { it.titlecase() }
        val dad = List(7_000) { Book(title(), author(), "R${random.nextInt(60)}") }
        val me = List(3_000) { Book(title(), author(), "${random.nextInt(20)}") }

        val buildStart = System.nanoTime()
        val index = SearchIndex.build(mapOf(Owner.DAD to dad, Owner.ME to me))
        val buildMillis = (System.nanoTime() - buildStart) / 1_000_000
        println("index: ${index.size} books built in $buildMillis ms")

        val typed = ArrayList<String>()
        for (phrase in listOf("kar tanesi", "kurk mantlu", "şükrü ilgaz", "zyxwq vbnmq", pool[3].take(6))) {
            for (length in 1..phrase.length) typed.add(phrase.substring(0, length))
        }
        repeat(2) { for (query in typed) index.search(query) } // warm up the JIT like a few seconds of real typing would

        var slowest = 0L
        var slowestQuery = ""
        val report = StringBuilder()
        for (query in typed) {
            val start = System.nanoTime()
            val hits = index.search(query)
            val millis = (System.nanoTime() - start) / 1_000_000
            report.append("  '").append(query).append("' -> ").append(hits.size).append(" hits in ").append(millis).append(" ms\n")
            if (millis > slowest) {
                slowest = millis
                slowestQuery = query
            }
        }
        val suggestStart = System.nanoTime()
        val suggestions = index.suggest("zyxwq vbnmq kartanesi")
        val suggestMillis = (System.nanoTime() - suggestStart) / 1_000_000
        report.append("  suggest -> ").append(suggestions.size).append(" in ").append(suggestMillis).append(" ms\n")
        println(report)
        println("slowest keystroke: '$slowestQuery' in $slowest ms")
        assertTrue("'$slowestQuery' took $slowest ms, limit ${LIMIT_MILLIS} ms", slowest < LIMIT_MILLIS)
        assertTrue("suggest took $suggestMillis ms", suggestMillis < LIMIT_MILLIS)
        assertTrue("index build took $buildMillis ms", buildMillis < 3_000)
    }

    /** Random Turkish-looking words of two to four syllables, with the real letters in play. */
    private fun wordPool(random: Random, size: Int): List<String> {
        val consonants = "bcçdfgğhjklmnprsştvyz"
        val vowels = "aeıioöuü"
        val words = LinkedHashSet<String>()
        while (words.size < size) {
            val syllables = 2 + random.nextInt(3)
            val sb = StringBuilder()
            repeat(syllables) {
                sb.append(consonants[random.nextInt(consonants.length)]).append(vowels[random.nextInt(vowels.length)])
                if (random.nextInt(3) == 0) sb.append(consonants[random.nextInt(consonants.length)])
            }
            words.add(sb.toString())
        }
        return words.toList()
    }

    private companion object {
        const val LIMIT_MILLIS = 50
    }
}
