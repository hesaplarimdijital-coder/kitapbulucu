package com.hesaplarimdijital.kitapbulucu

import com.hesaplarimdijital.kitapbulucu.ui.AppShortcuts
import desktopkit.input.CommonShortcuts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipInputStream

class AppBasicsTest {

    @Test
    fun noTwoShortcutsShareAKey() {
        assertTrue(AppShortcuts.registry.conflicts().toString(), AppShortcuts.registry.conflicts().isEmpty())
    }

    @Test
    fun commonShortcutsAreRegistered() {
        for (id in listOf(CommonShortcuts.SEARCH, CommonShortcuts.SETTINGS, CommonShortcuts.SHORTCUT_LIST)) {
            assertNotNull(id, AppShortcuts.registry.getOrNull(id))
        }
    }

    @Test
    fun phonesAndTabletsAreToldApart() {
        assertTrue(isPhoneSized(1080, 2340, 2.625f))   // typical phone, about 411 dp wide
        assertTrue(isPhoneSized(2340, 1080, 2.625f))   // the same phone held sideways
        assertFalse(isPhoneSized(2960, 1848, 2.0f))    // large tablet, about 924 dp on the short side
        assertFalse(isPhoneSized(1920, 1080, 1.0f))    // DeX on an external monitor
    }

    /** The made-up sample workbooks used by the spreadsheet tests are in the test resources. */
    @Test
    fun fixtureWorkbooksArePresent() {
        for (name in listOf("ornek-kutuphane.xlsx", "ornek-eski-bicim.xlsx")) {
            val stream = javaClass.getResourceAsStream("/fixtures/$name")
            assertNotNull("missing fixture $name", stream)
            val entries = ZipInputStream(stream).use { zip ->
                generateSequence { zip.nextEntry }.map { it.name }.toSet()
            }
            assertTrue("$name: $entries", "xl/worksheets/sheet1.xml" in entries)
            assertTrue("$name: $entries", "[Content_Types].xml" in entries)
        }
    }
}
