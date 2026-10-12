package com.hesaplarimdijital.kitapbulucu.xlsx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateFormatsTest {

    @Test
    fun recognisesDateAndTimeFormats() {
        for (code in listOf(
            "mmm/yy", "dd/mmm/yy", "m/d/yyyy", "d-mmm-yy", "yyyy-mm-dd", "dd\\.mm\\.yyyy", "h:mm", "[h]:mm:ss", "h:mm:ss AM/PM",
            "[\$-F800]dddd, mmmm dd, yyyy", "[\$-41F]d mmmm yyyy;@", "dd/mm/yyyy;@", "\"Tarih:\" dd.mm.yyyy", "yyyy\"m\"", "MMM/YY",
        )) {
            assertTrue(code, DateFormats.isDateFormat(code))
        }
    }

    @Test
    fun leavesNumberAndTextFormatsAlone() {
        for (code in listOf(
            "General", "0", "0.00", "#,##0", "#,##0.00", "0%", "0.00E+00", "# ?/?", "@", "\"Yıl: \"0", "0\" adet\"",
            "[\$₺-41F]#,##0.00", "[Red]0", "[>=100]0;[<100]0.0", "_(* #,##0_);_(* (#,##0);_(* \"-\"_);_(@_)", "",
        )) {
            assertFalse(code, DateFormats.isDateFormat(code))
        }
    }

    @Test
    fun knowsExcelsBuiltInDateFormats() {
        assertEquals("m/d/yyyy", DateFormats.builtInCode(14))
        assertEquals("mmm-yy", DateFormats.builtInCode(17))
        assertEquals("h:mm", DateFormats.builtInCode(20))
        assertEquals("yyyy/m/d", DateFormats.builtInCode(30))
        assertEquals("yyyy/m/d", DateFormats.builtInCode(58))
        assertEquals(null, DateFormats.builtInCode(0))
        assertEquals(null, DateFormats.builtInCode(49))
        for (id in listOf(14, 15, 16, 17, 18, 19, 20, 21, 22, 45, 46, 47)) assertTrue("$id", DateFormats.isDateFormat(DateFormats.builtInCode(id)!!))
    }

    @Test
    fun writesWhatTheFormatShows() {
        assertEquals("15 MART 1998", DateFormats.serialToText(35869.0, false, "m/d/yyyy"))
        assertEquals("15 MART 1998", DateFormats.serialToText(35869.0, false, "dd/mmm/yy"))
        assertEquals("15 MART 1998", DateFormats.serialToText(35869.6, false, "d.m.yyyy h:mm")) // the time is dropped
        assertEquals("NİSAN 1953", DateFormats.serialToText(19450.0, false, "mmm/yy"))
        assertEquals("NİSAN 1953", DateFormats.serialToText(19450.0, false, "mmmm yyyy"))
        assertEquals("1998", DateFormats.serialToText(35869.0, false, "yyyy"))
        assertEquals("1 ARALIK 2016", DateFormats.serialToText(42705.0, false, "m/d/yyyy"))
        assertEquals("12:00", DateFormats.serialToText(0.5, false, "h:mm"))
        assertEquals("18:30", DateFormats.serialToText(45000.77083, false, "hh:mm:ss"))
        assertEquals("00:00", DateFormats.serialToText(0.0, false, "[h]:mm:ss"))
    }

    @Test
    fun followsExcelsCalendarQuirks() {
        assertEquals(LocalDate.of(1900, 1, 1), DateFormats.serialToDate(1.0, false))
        assertEquals(LocalDate.of(1900, 2, 28), DateFormats.serialToDate(59.0, false))
        assertEquals(LocalDate.of(1900, 3, 1), DateFormats.serialToDate(60.0, false)) // Excel's imaginary 29 February 1900
        assertEquals(LocalDate.of(1900, 3, 1), DateFormats.serialToDate(61.0, false))
        assertEquals(LocalDate.of(1998, 3, 15), DateFormats.serialToDate(35869.0, false))
        assertEquals(LocalDate.of(1904, 1, 1), DateFormats.serialToDate(0.0, true))
        assertEquals(LocalDate.of(1953, 4, 1), DateFormats.serialToDate(17988.0, true))
        assertEquals("1 OCAK 1904", DateFormats.serialToText(0.0, true, "m/d/yyyy"))
    }

    @Test
    fun monthsAreTurkishCapitals() {
        assertEquals(12, DateFormats.MONTHS.size)
        assertEquals("OCAK", DateFormats.MONTHS.first())
        assertEquals("ARALIK", DateFormats.MONTHS.last())
        assertEquals("ŞUBAT", DateFormats.MONTHS[1])
    }
}
