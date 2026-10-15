package com.hesaplarimdijital.kitapbulucu.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

/** How far the results scroll to show a row: never to the top, only the least that shows it. */
class RevealDeltaTest {

    @Test
    fun aRowAlreadyInViewDoesNotMove() {
        assertEquals(0, revealDelta(start = 300, end = 360, viewStart = 0, viewEnd = 800))
        assertEquals(0, revealDelta(start = 0, end = 800, viewStart = 0, viewEnd = 800))
    }

    @Test
    fun aRowCutAtTheBottomScrollsUpOnlyByTheHiddenPart() {
        // an opened row from 600 to 950 in a viewport ending at 800: 150 down, not 600
        assertEquals(150, revealDelta(start = 600, end = 950, viewStart = 0, viewEnd = 800))
    }

    @Test
    fun aRowCutAtTheTopScrollsBackByTheHiddenPart() {
        assertEquals(-40, revealDelta(start = -40, end = 20, viewStart = 0, viewEnd = 800))
    }

    @Test
    fun aRowTallerThanTheViewportShowsItsTop() {
        assertEquals(100, revealDelta(start = 100, end = 1200, viewStart = 0, viewEnd = 800))
        assertEquals(-50, revealDelta(start = -50, end = 1200, viewStart = 0, viewEnd = 800))
    }

    @Test
    fun aViewportWithPaddingBeforeItIsRespected() {
        assertEquals(-10, revealDelta(start = -30, end = 30, viewStart = -20, viewEnd = 780))
        assertEquals(0, revealDelta(start = -20, end = 30, viewStart = -20, viewEnd = 780))
    }
}
