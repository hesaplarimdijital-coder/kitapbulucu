package com.hesaplarimdijital.kitapbulucu.xlsx

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Reads a text table the way Excel writes it in Turkey: `;` or `,` between cells, quotes
 * around cells that need them, UTF-8 (with or without a byte-order mark) or Windows-1254.
 */
object CsvReader {
    private val WINDOWS_1254: Charset = Charset.forName("windows-1254")

    fun read(bytes: ByteArray, name: String = "CSV"): Sheet {
        val text = decode(bytes)
        return Sheet(name, parse(text, sniffDelimiter(text)))
    }

    /** UTF-8 when the bytes are valid UTF-8 (a BOM is dropped), otherwise Windows-1254. */
    fun decode(bytes: ByteArray): String {
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString()
        } catch (e: CharacterCodingException) {
            String(body, WINDOWS_1254)
        }
    }

    /** The most frequent of `;`, `,` and tab outside quotes in the first lines; `;` when in doubt. */
    fun sniffDelimiter(text: String): Char {
        val counts = HashMap<Char, Int>()
        var inQuotes = false
        var lines = 0
        for (ch in text) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                inQuotes -> Unit
                ch == '\n' -> if (++lines >= 20) break
                ch == ';' || ch == ',' || ch == '\t' -> counts[ch] = (counts[ch] ?: 0) + 1
            }
        }
        val best = counts.entries.maxByOrNull { it.value } ?: return ';'
        val semicolons = counts[';'] ?: 0
        return if (semicolons >= best.value) ';' else best.key
    }

    fun parse(text: String, delimiter: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var pending = false // the current line holds a cell, even an empty one
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                when {
                    ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                    ch == '"' -> inQuotes = false
                    else -> cell.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> { inQuotes = true; pending = true }
                    delimiter -> { row.add(cell.toString()); cell.setLength(0); pending = true }
                    '\r' -> Unit
                    '\n' -> {
                        if (pending) row.add(cell.toString())
                        cell.setLength(0)
                        rows.add(row)
                        row = ArrayList()
                        pending = false
                    }
                    else -> { cell.append(ch); pending = true }
                }
            }
            i++
        }
        if (pending) row.add(cell.toString())
        if (row.isNotEmpty()) rows.add(row)
        while (rows.isNotEmpty() && rows.last().all { it.isEmpty() }) rows.removeAt(rows.size - 1)
        return rows
    }
}
