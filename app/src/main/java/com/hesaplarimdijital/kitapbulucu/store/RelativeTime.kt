package com.hesaplarimdijital.kitapbulucu.store

/** "3 dk önce" style distance between two instants, decided here and worded by the screens. */
sealed class RelativeTime {
    object JustNow : RelativeTime()
    data class Minutes(val minutes: Int) : RelativeTime()
    data class Hours(val hours: Int) : RelativeTime()
    object Yesterday : RelativeTime()
    data class Days(val days: Int) : RelativeTime()
    /** Longer ago than a week: show the date. */
    data class OnDate(val at: Long) : RelativeTime()

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR

        fun of(at: Long, now: Long): RelativeTime {
            val elapsed = (now - at).coerceAtLeast(0)
            return when {
                elapsed < MINUTE -> JustNow
                elapsed < HOUR -> Minutes((elapsed / MINUTE).toInt())
                elapsed < DAY -> Hours((elapsed / HOUR).toInt())
                elapsed < 2 * DAY -> Yesterday
                elapsed < 7 * DAY -> Days((elapsed / DAY).toInt())
                else -> OnDate(at)
            }
        }
    }
}
