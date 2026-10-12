package com.hesaplarimdijital.kitapbulucu.xlsx

import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Excel keeps dates as day counts and shows them through a number format. A date-formatted
 * number becomes the text its owner meant: "15 MART 1998" when the format shows a day,
 * "NİSAN 1953" when it shows only month and year, "1998" for a year alone, "14:05" for a time.
 */
object DateFormats {
    val MONTHS = listOf("OCAK", "ŞUBAT", "MART", "NİSAN", "MAYIS", "HAZİRAN", "TEMMUZ", "AĞUSTOS", "EYLÜL", "EKİM", "KASIM", "ARALIK")

    /** Format codes of Excel's built-in date and time formats; the locale-specific ones are all day-level. */
    fun builtInCode(numFmtId: Int): String? = when (numFmtId) {
        14 -> "m/d/yyyy"
        15 -> "d-mmm-yy"
        16 -> "d-mmm"
        17 -> "mmm-yy"
        18 -> "h:mm AM/PM"
        19 -> "h:mm:ss AM/PM"
        20 -> "h:mm"
        21 -> "h:mm:ss"
        22 -> "m/d/yyyy h:mm"
        45 -> "mm:ss"
        46 -> "[h]:mm:ss"
        47 -> "mmss.0"
        in 27..36, in 50..58 -> "yyyy/m/d"
        else -> null
    }

    /** True for a format code that shows a date or a time ("mmm/yy", "[$-F800]dddd, mmmm dd, yyyy", "h:mm"). */
    fun isDateFormat(code: String): Boolean {
        val tokens = tokens(code).replace(FRACTIONAL_SECONDS, "s")
        if (tokens.contains('#') || tokens.contains('0') || tokens.contains('@')) return false
        return tokens.any { it in "ymdhs" }
    }

    /** "ss.00" shows tenths of a second; the zeros there are not a number format. */
    private val FRACTIONAL_SECONDS = Regex("s\\.0+")

    fun serialToText(serial: Double, date1904: Boolean, format: String): String {
        val tokens = tokens(format)
        val hasDay = 'd' in tokens
        val hasYear = 'y' in tokens
        val hasMonth = 'm' in tokens && !(tokens.contains('h') || tokens.contains('s')) || hasYear && 'm' in tokens
        if (!hasDay && !hasYear && !hasMonth) return timeText(serial)
        val date = serialToDate(serial, date1904)
        val month = MONTHS[date.monthValue - 1]
        return when {
            hasDay -> "${date.dayOfMonth} $month ${date.year}"
            hasMonth -> "$month ${date.year}"
            else -> date.year.toString()
        }
    }

    /** Day count to a calendar date, including Excel's imaginary 29 February 1900. */
    fun serialToDate(serial: Double, date1904: Boolean): LocalDate {
        val days = floor(serial).toLong()
        return when {
            date1904 -> LocalDate.of(1904, 1, 1).plusDays(days)
            days < 61 -> LocalDate.of(1899, 12, 31).plusDays(days)
            else -> LocalDate.of(1899, 12, 30).plusDays(days)
        }
    }

    private fun timeText(serial: Double): String {
        val minutes = ((serial - floor(serial)) * 24 * 60).roundToLong() % (24 * 60)
        return "%02d:%02d".format(minutes / 60, minutes % 60)
    }

    /**
     * The format letters that matter, lower-cased, with quoted text, bracketed sections
     * ([$-F800], [Red]) and escaped or padding characters removed. Only the first section
     * counts: "dd/mm/yyyy;@" shows dates for positive numbers, and the "@" after the
     * semicolon is about text cells.
     */
    private fun tokens(code: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < code.length) {
            when (val ch = code[i]) {
                '"' -> i = code.indexOf('"', i + 1).let { if (it < 0) code.length else it }
                '[' -> i = code.indexOf(']', i + 1).let { if (it < 0) code.length else it }
                '\\', '_', '*' -> i++
                ';' -> break
                else -> out.append(ch.lowercaseChar())
            }
            i++
        }
        return out.toString()
    }
}
