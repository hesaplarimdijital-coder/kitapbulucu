package com.hesaplarimdijital.kitapbulucu.store

import kotlinx.coroutines.delay

/**
 * When the files are read again without the user asking (ARCHITECTURE 7): on every start of the
 * activity, when its window gets focus (DeX and split screen keep the activity started while
 * the user works in other windows) and every [periodMillis] while it is resumed. Focus and the
 * timer never read more often than every [gapMillis]; reading itself is cheap (same bytes,
 * nothing parsed) and [LibraryStore] never runs two passes at once.
 */
class AutoRefresh(
    /** A full refresh, as F5 does. */
    private val refresh: () -> Unit,
    /** A refresh that is dropped while one runs and does not show "reading". */
    private val refreshInBackground: () -> Unit,
    /** A monotonic clock in milliseconds. */
    private val clock: () -> Long,
    private val gapMillis: Long = GAP_MILLIS,
    private val periodMillis: Long = PERIOD_MILLIS,
) {
    private var lastAt: Long? = null

    /** The activity came to the foreground. */
    fun onStart() {
        lastAt = clock()
        refresh()
    }

    /** The window got focus; ignored right after another read. */
    fun onFocusGained() {
        if (since() >= gapMillis) background()
    }

    /**
     * Reads every [periodMillis], counted from the last read, until cancelled; run it while the
     * activity is resumed. [wait] is `delay` outside tests.
     */
    suspend fun whileResumed(wait: suspend (Long) -> Unit = { delay(it) }) {
        while (true) {
            val remaining = periodMillis - since()
            if (remaining > 0) wait(remaining) else background()
        }
    }

    private fun since(): Long = lastAt?.let { clock() - it } ?: Long.MAX_VALUE

    private fun background() {
        lastAt = clock()
        refreshInBackground()
    }

    companion object {
        const val GAP_MILLIS = 10_000L
        const val PERIOD_MILLIS = 60_000L
    }
}
