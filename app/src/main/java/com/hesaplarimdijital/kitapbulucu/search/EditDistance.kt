package com.hesaplarimdijital.kitapbulucu.search

/**
 * Edit distance for near matches: an inserted, dropped or wrong letter costs one, and so does
 * a swap of two neighbouring letters ("kürk" / "krük"). Only distances up to a small bound
 * matter, so the table is abandoned as soon as a row exceeds the bound. One instance keeps
 * its rows and is reused across a whole vocabulary scan; it is not thread-safe.
 */
class EditDistance(initialCapacity: Int = 64) {
    private var rowBefore = IntArray(initialCapacity + 1)
    private var rowAbove = IntArray(initialCapacity + 1)
    private var row = IntArray(initialCapacity + 1)

    /**
     * Distance between [token] and [word], or `bound + 1` when it is larger than [bound].
     * With [prefix], the token is compared against the beginnings of the word instead, so a
     * half-typed word with a typo ("mantl" against "mantolu") still comes close.
     */
    fun bounded(token: String, word: String, bound: Int, prefix: Boolean = false): Int {
        val tooFar = bound + 1
        val m = token.length
        val n = if (prefix) minOf(word.length, m + bound) else word.length
        if (n - m > bound || m - n > bound) return tooFar
        if (m == 0) return n
        if (n == 0) return m
        ensureCapacity(n)
        for (j in 0..n) rowAbove[j] = j
        for (i in 1..m) {
            row[0] = i
            var rowMin = i
            val tokenChar = token[i - 1]
            for (j in 1..n) {
                val wordChar = word[j - 1]
                var value = minOf(rowAbove[j - 1] + (if (tokenChar == wordChar) 0 else 1), rowAbove[j] + 1, row[j - 1] + 1)
                if (i > 1 && j > 1 && tokenChar == word[j - 2] && token[i - 2] == wordChar) {
                    value = minOf(value, rowBefore[j - 2] + 1)
                }
                row[j] = value
                if (value < rowMin) rowMin = value
            }
            if (rowMin > bound) return tooFar
            val recycled = rowBefore
            rowBefore = rowAbove
            rowAbove = row
            row = recycled
        }
        val last = rowAbove
        var result = last[n]
        if (prefix) {
            for (j in maxOf(0, m - bound) until n) if (last[j] < result) result = last[j]
        }
        return minOf(result, tooFar)
    }

    private fun ensureCapacity(n: Int) {
        if (rowAbove.size <= n) {
            rowBefore = IntArray(n + 1)
            rowAbove = IntArray(n + 1)
            row = IntArray(n + 1)
        }
    }
}
