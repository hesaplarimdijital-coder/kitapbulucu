package com.hesaplarimdijital.kitapbulucu.text

/** Turkish-aware case folding: `I` is the capital of `ı`, `İ` the capital of `i`. */
object TurkishText {
    fun lower(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when (ch) {
                    'I' -> 'ı'
                    'İ' -> 'i'
                    else -> ch.lowercaseChar()
                },
            )
        }
        return sb.toString()
    }

    fun upper(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when (ch) {
                    'ı' -> 'I'
                    'i' -> 'İ'
                    else -> ch.uppercaseChar()
                },
            )
        }
        return sb.toString()
    }

    /** Lower-cased with Turkish letters and circumflexes flattened to ASCII: "Hâlâ İğde" -> "hala igde". */
    fun asciiFold(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in lower(text)) {
            sb.append(
                when (ch) {
                    'ı', 'î', 'ï', 'í', 'ì' -> 'i'
                    'ş' -> 's'
                    'ğ' -> 'g'
                    'ü', 'û', 'ú', 'ù' -> 'u'
                    'ö', 'ô', 'ó', 'ò' -> 'o'
                    'ç' -> 'c'
                    'â', 'ä', 'á', 'à' -> 'a'
                    'ê', 'ë', 'é', 'è' -> 'e'
                    else -> ch
                },
            )
        }
        return sb.toString()
    }

    /** Trimmed, with runs of whitespace collapsed to one space. */
    fun squeeze(text: String): String {
        val sb = StringBuilder(text.length)
        var pendingSpace = false
        for (ch in text) {
            if (ch.isWhitespace() || ch == ' ') {
                pendingSpace = sb.isNotEmpty()
            } else {
                if (pendingSpace) sb.append(' ')
                pendingSpace = false
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
