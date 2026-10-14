package com.hesaplarimdijital.kitapbulucu.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchSelectionTest {

    @Test
    fun startsInactive() {
        val none = TouchSelection.None
        assertFalse(none.active)
        assertEquals(0, none.size)
    }

    @Test
    fun longPressStartsAndTapsToggle() {
        val started = TouchSelection.None.toggle("DAD:3")
        assertTrue(started.active)
        assertTrue("DAD:3" in started)
        val two = started.toggle("DAD:7")
        assertEquals(2, two.size)
        val back = two.toggle("DAD:3")
        assertEquals(setOf<Any>("DAD:7"), back.keys)
    }

    @Test
    fun uncheckingTheLastRowEndsSelectionMode() {
        val one = TouchSelection.None.toggle("ME:0")
        assertFalse(one.toggle("ME:0").active)
    }

    @Test
    fun selectAllAndHasAll() {
        val all = listOf<Any>("DAD:0", "DAD:1", "DAD:2")
        val some = TouchSelection.None.toggle("DAD:1")
        assertFalse(some.hasAll(all))
        val every = some.selectAll(all)
        assertEquals(3, every.size)
        assertTrue(every.hasAll(all))
        assertFalse(TouchSelection.None.hasAll(emptyList()))
    }

    @Test
    fun retainDropsRowsThatLeftTheList() {
        val chosen = TouchSelection(setOf("DAD:0", "DAD:5"))
        assertEquals(setOf<Any>("DAD:0"), chosen.retain(listOf("DAD:0", "DAD:1")).keys)
        assertSame(chosen, chosen.retain(listOf("DAD:0", "DAD:5", "DAD:9")))
        assertFalse(chosen.retain(emptyList()).active)
    }
}
