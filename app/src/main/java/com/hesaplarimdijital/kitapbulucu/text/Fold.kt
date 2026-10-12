package com.hesaplarimdijital.kitapbulucu.text

/**
 * The two folding levels the search works with (ARCHITECTURE 9).
 *
 * Level 1, [turkish]: Turkish lower case, `ı` -> `i`, circumflexes and foreign accents removed,
 * Turkish letters kept: "Hâlâ Islık" -> "hala islik". Queries typed on a Turkish keyboard
 * match at this level.
 *
 * Level 2, [ascii]: level 1 plus `ç ğ ö ş ü` -> `c g o s u`, for queries typed without
 * Turkish letters: "Kürk" -> "kurk". A level 2 match ranks below a level 1 match.
 */
object Fold {
    private val APOSTROPHES = setOf('\'', '’', '‘', 'ʼ', '`', '´')

    fun turkish(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in TurkishText.lower(text)) {
            sb.append(
                when (ch) {
                    'ı', 'î', 'ï', 'í', 'ì' -> 'i'
                    'â', 'ä', 'á', 'à', 'ã', 'å' -> 'a'
                    'û', 'ú', 'ù' -> 'u'
                    'ô', 'ó', 'ò', 'õ' -> 'o'
                    'ê', 'ë', 'é', 'è' -> 'e'
                    'ñ' -> 'n'
                    else -> ch
                },
            )
        }
        return sb.toString()
    }

    fun ascii(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in turkish(text)) {
            sb.append(
                when (ch) {
                    'ç' -> 'c'
                    'ğ' -> 'g'
                    'ö' -> 'o'
                    'ş' -> 's'
                    'ü' -> 'u'
                    else -> ch
                },
            )
        }
        return sb.toString()
    }

    fun hasTurkishLetters(text: String): Boolean = text.any { it in "çğöşüÇĞÖŞÜİı" }

    /**
     * Level 1 words of a text. Letters and digits make a word; everything else separates,
     * except an apostrophe inside a word: "İstanbul'un" is indexed as `istanbul`, `un` and
     * `istanbulun`, so it is found whether the searcher types the apostrophe or not, while a
     * query keeps only the joined form (`istanbulun`), which matches both spellings.
     */
    fun words(text: String, forQuery: Boolean = false): List<String> {
        val folded = turkish(text)
        val words = ArrayList<String>()
        val parts = ArrayList<String>()
        val current = StringBuilder()

        fun flushPart() {
            if (current.isNotEmpty()) {
                parts.add(current.toString())
                current.setLength(0)
            }
        }

        fun flushWord() {
            flushPart()
            if (parts.isEmpty()) return
            if (parts.size == 1) {
                words.add(parts[0])
            } else if (forQuery) {
                words.add(parts.joinToString(""))
            } else {
                words.addAll(parts)
                words.add(parts.joinToString(""))
            }
            parts.clear()
        }

        for (ch in folded) {
            when {
                ch.isLetterOrDigit() -> current.append(ch)
                ch in APOSTROPHES && current.isNotEmpty() -> flushPart()
                else -> flushWord()
            }
        }
        flushWord()
        return words
    }
}
