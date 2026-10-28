package com.hesaplarimdijital.kitapbulucu.text

/**
 * Turkish title case for book titles and authors typed in capitals (the father's original list):
 * "FYODOR DOSTOYEVSKİ" -> "Fyodor Dostoyevski", "SUÇ VE CEZA" -> "Suç ve Ceza".
 *
 * - Every word starts with a capital and goes on in lower case, with Turkish letters
 *   (I -> ı, İ -> i) and circumflexes kept (HÂLÂ -> Hâlâ).
 * - A word starts after a space and after any other mark: "-", "/", "(", quotes, and the dot
 *   between initials ("A.HAMDİ" -> "A.Hamdi", "1.CİLT" -> "1.Cilt").
 * - The suffix after an apostrophe stays lower case ("İSTANBUL'UN" -> "İstanbul'un"); an
 *   apostrophe with no letter before it opens a quote, and the next letter is a capital.
 * - Roman numerals ("II", "XIX"), the short abbreviations in [ACRONYMS] and letter-digit
 *   tokens ("3B", "MP3") are kept as they are.
 * - As the Turkish spelling rules (TDK) write titles, the conjunctions and the question particle
 *   in [SMALL_WORDS] are lower case unless they open the cell ("Suç ve Ceza", "Leonardo da Vinci").
 *
 * Only a cell typed wholly in capitals is converted ([needsFix]); anything the user already
 * typed in mixed case is left alone.
 */
object TitleCase {

    /** Abbreviations that stay in capitals. Dotted ones (T.C., İ.Ü.) are single letters and stay anyway. */
    val ACRONYMS = setOf(
        "TBMM", "TDK", "MEB", "TRT", "ABD", "AB", "NATO", "UNESCO", "MKE", "TC", "ODTÜ", "İTÜ", "DTCF", "TTK", "PTT", "SSCB", "BM",
    )

    /** Written in lower case inside a title (TDK): conjunctions and the question particle. */
    val SMALL_WORDS = setOf("ve", "ile", "ya", "veya", "yahut", "ki", "da", "de", "mı", "mi", "mu", "mü")

    private val APOSTROPHES = setOf('\'', '’', 'ʼ', '`', '´')

    /** Only I, V, X, L and C: "MI" (the question particle) and "DİL" must not pass for numerals. */
    private val ROMAN = Regex("^(?=[IVXLC])(C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$")

    /** True when [text] has letters and all of them are capitals: the cells this fix is for. */
    fun needsFix(text: String): Boolean {
        var letters = false
        for (ch in text) {
            if (!ch.isLetter()) continue
            if (ch.isLowerCase()) return false
            letters = true
        }
        return letters
    }

    /** [text] in title case when it is written wholly in capitals, otherwise [text] itself. */
    fun fix(text: String): String = if (needsFix(text)) convert(text) else text

    /** Title case regardless of how [text] is written now; spacing and marks are kept as they are. */
    fun convert(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        var wordsBefore = 0
        while (i < text.length) {
            val ch = text[i]
            if (!ch.isLetterOrDigit()) {
                out.append(ch)
                i++
                continue
            }
            var end = i
            while (end < text.length && text[end].isLetterOrDigit()) end++
            val word = text.substring(i, end)
            // the suffix after an apostrophe (İSTANBUL'UN, ABD'NİN, 1950'LER), possibly more than one
            var suffixEnd = end
            while (suffixEnd + 1 < text.length && text[suffixEnd] in APOSTROPHES && text[suffixEnd + 1].isLetter()) {
                suffixEnd++
                while (suffixEnd < text.length && text[suffixEnd].isLetterOrDigit()) suffixEnd++
            }
            val small = wordsBefore > 0 && suffixEnd == end && standsAlone(text, i, end) && TurkishText.lower(word) in SMALL_WORDS
            out.append(if (small) TurkishText.lower(word) else capitalized(word))
            out.append(TurkishText.lower(text.substring(end, suffixEnd)))
            wordsBefore++
            i = suffixEnd
        }
        return out.toString()
    }

    private fun capitalized(word: String): String = when {
        word.any(Char::isDigit) -> word
        word in ACRONYMS -> word
        ROMAN.matches(word) -> word
        else -> TurkishText.upper(word.substring(0, 1)) + TurkishText.lower(word.substring(1))
    }

    /** A whole word between spaces (a "?", "," or "." may follow it): "MI?" is the particle, "DE-" is not. */
    private fun standsAlone(text: String, start: Int, end: Int): Boolean {
        val before = text[start - 1]
        if (!before.isWhitespace()) return false
        if (end == text.length) return true
        val after = text[end]
        return after.isWhitespace() || after in "?!,.;:"
    }
}
