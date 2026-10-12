package com.hesaplarimdijital.kitapbulucu.store

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = 1_700_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun picksTheRightUnit() {
        assertEquals(RelativeTime.JustNow, RelativeTime.of(now, now))
        assertEquals(RelativeTime.JustNow, RelativeTime.of(now - 59_000, now))
        assertEquals(RelativeTime.JustNow, RelativeTime.of(now + 5 * minute, now)) // a clock ahead of ours is "just now", never negative
        assertEquals(RelativeTime.Minutes(1), RelativeTime.of(now - minute, now))
        assertEquals(RelativeTime.Minutes(3), RelativeTime.of(now - 3 * minute - 20_000, now))
        assertEquals(RelativeTime.Minutes(59), RelativeTime.of(now - 59 * minute, now))
        assertEquals(RelativeTime.Hours(1), RelativeTime.of(now - hour, now))
        assertEquals(RelativeTime.Hours(23), RelativeTime.of(now - 23 * hour - 59 * minute, now))
        assertEquals(RelativeTime.Yesterday, RelativeTime.of(now - day, now))
        assertEquals(RelativeTime.Yesterday, RelativeTime.of(now - 47 * hour, now))
        assertEquals(RelativeTime.Days(2), RelativeTime.of(now - 2 * day, now))
        assertEquals(RelativeTime.Days(6), RelativeTime.of(now - 6 * day - 23 * hour, now))
        assertEquals(RelativeTime.OnDate(now - 7 * day), RelativeTime.of(now - 7 * day, now))
    }
}
