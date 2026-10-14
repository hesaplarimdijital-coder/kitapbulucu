package com.hesaplarimdijital.kitapbulucu.text

/**
 * The rules of the first cleaning of the list (tools/temizleme/booklib.py), ported so that every
 * imported file is cleaned the same way (ARCHITECTURE 10): the signed marker leaves the title or
 * author, a dedication goes to the note, volume numbers tell books apart.
 */
object Cleaning {
    const val SIGNED_WORD = "İMZALI"
    const val SIGNED_NOTE = "İmzalı"

    /** A genre tag that shares the last title segment with the signed marker stays in the title. */
    private val GENRE_TAGS = setOf("ŞİİR", "ŞİİRLER")
    private val SIGNED = Regex("(?<![\\p{L}\\p{N}_])$SIGNED_WORD(?![\\p{L}\\p{N}_])")
    private val ROMAN = Regex("^(?=[ivxlc])(c{0,3})(xc|xl|l?x{0,3})(ix|iv|v?i{0,3})$")
    private val TRAILING_GROUP = Regex("^(.*?)\\s*\\(([^()]*)\\)$")
    private val DASH = Regex("\\s+[-–]\\s*|\\s*[-–]\\s+")

    /** A cell without the signed marker, and the note it leaves: null when there was none. */
    data class Extracted(val text: String, val note: String?)

    /** "BÜLENT ECEVİT'E" -> "Bülent Ecevit'e" (the suffix after an apostrophe stays lower case). */
    fun titleCase(text: String): String = TurkishText.squeeze(text).split(" ").joinToString(" ") { word ->
        val apostrophe = word.indexOfFirst { it == '\'' || it == '’' }
        val head = if (apostrophe < 0) word else word.substring(0, apostrophe)
        val tail = if (apostrophe < 0) "" else word.substring(apostrophe)
        TurkishText.upper(head.take(1)) + TurkishText.lower(head.drop(1)) + TurkishText.lower(tail)
    }

    /** Moves a "signed copy" marker out of a title or author cell; "İMZASIZ" is left alone. */
    fun extractSigned(raw: String): Extracted {
        val text = TurkishText.squeeze(raw)
        val upper = TurkishText.upper(text)
        if (upper.length != text.length || !SIGNED.containsMatchIn(upper)) return Extracted(text, null)

        // 1) a trailing group in brackets: "TITLE (İMZALI)", "TITLE (X'E İMZALI)"
        TRAILING_GROUP.matchEntire(upper)?.let { match ->
            val group = match.groupValues[2]
            if (SIGNED.containsMatchIn(group)) {
                val (tail, note) = splitSegment(group)
                val head = text.substring(0, match.groups[1]!!.range.last + 1)
                return Extracted(if (tail.isNotEmpty()) "$head ($tail)" else head, note)
            }
        }
        // 2) the last dash-separated segment: "TITLE - İMZALI", "TITLE - ŞİİR İMZALI"
        val separators = DASH.findAll(upper).toList()
        if (separators.isNotEmpty()) {
            val last = separators.last()
            val segment = upper.substring(last.range.last + 1)
            if (SIGNED.containsMatchIn(segment)) {
                val (tail, note) = splitSegment(segment)
                val head = text.substring(0, last.range.first)
                return Extracted(if (tail.isNotEmpty()) head + text.substring(last.range.first, last.range.last + 1) + tail else head, note)
            }
        }
        // 3) the word inside the text without a segment of its own
        val match = SIGNED.find(upper)!!
        var cleaned = TurkishText.squeeze(text.substring(0, match.range.first) + " " + text.substring(match.range.last + 1))
        cleaned = cleaned.replace(Regex("(\\s[-–])+(?=\\s[-–]\\s)"), "")
        cleaned = cleaned.replace(Regex("^\\s*[-–]\\s+|\\s+[-–]\\s*$|\\(\\s*\\)"), "")
        return Extracted(TurkishText.squeeze(cleaned), SIGNED_NOTE)
    }

    /** (kept tail, note) for an upper-cased segment holding the signed word. */
    private fun splitSegment(upperSegment: String): Pair<String, String> {
        val rest = TurkishText.squeeze(SIGNED.replace(upperSegment, " "))
        return when {
            rest.isEmpty() -> "" to SIGNED_NOTE
            rest in GENRE_TAGS -> rest to SIGNED_NOTE
            // a dedication such as "BÜLENT ECEVİT'E İMZALI": kept whole and readable
            else -> "" to "${titleCase(rest)} imzalı"
        }
    }

    /** booklib.fold: case, dotted and dotless i, circumflex and punctuation ignored. */
    fun fold(text: String): String {
        val lowered = TurkishText.lower(TurkishText.squeeze(text))
            .replace('ı', 'i').replace('â', 'a').replace('î', 'i').replace('û', 'u').replace('ê', 'e').replace('ô', 'o')
        return TurkishText.squeeze(lowered.replace(Regex("[^\\p{L}\\p{N}_\\s]"), " "))
    }

    /** Numbers and Roman numerals: the tokens that tell volumes and issues apart. */
    fun isVolumeToken(token: String): Boolean = token.all(Char::isDigit) && token.isNotEmpty() || ROMAN.matches(token)

    /** True when two titles are the same words with different numbers (vol. 2 / vol. 3). */
    fun differsOnlyByNumber(a: String, b: String): Boolean {
        val x = fold(a).split(" ").filter { it.isNotEmpty() }
        val y = fold(b).split(" ").filter { it.isNotEmpty() }
        return x.filterNot(::isVolumeToken) == y.filterNot(::isVolumeToken) && x.filter(::isVolumeToken) != y.filter(::isVolumeToken)
    }
}
