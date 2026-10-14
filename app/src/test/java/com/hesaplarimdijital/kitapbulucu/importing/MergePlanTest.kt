package com.hesaplarimdijital.kitapbulucu.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookColumn
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.ColumnMapping
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.Change
import com.hesaplarimdijital.kitapbulucu.xlsx.TestWorkbooks
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Needs Robolectric: the old-layout fixture goes through the Android XML parser. */
@RunWith(AndroidJUnit4::class)
class MergePlanTest {

    /** ornek-eski-bicim.xlsx as the first cleaning turned it into the app's layout, with shelves added since. */
    private val cleanedOld = listOf(
        Book("ISLIKLI KUŞLARIN SOKAĞI", "NEVİN ÇAĞLAR", "A1"),
        Book("SEFİL FENERCİLER", "HALİT ORUÇ", "A1", 2),
        Book("HÂLÂ BEKLEYEN MEKTUP", "ŞÜKRÜ ILGAZ", "A2", signed = true),
        Book("TARLA DERGİSİ SAYI 1", "NİSAN 1953"),
        Book("ÇATIDAKİ RASATHANE", "AYŞE İNCE", "C2"),
        Book("YAĞMURUN İSTANBUL'U", "CEMAL KILIÇ", "D4", signed = true, note = "Cemal Bey'e imzalı"),
        Book("YAZARSIZ BİR ALMANAK", ""),
    )

    private fun oldFile(): BookTable {
        val sheet = XlsxReader.read(TestWorkbooks.fixture("ornek-eski-bicim.xlsx").inputStream()).sheets.first()
        return BookTable.parse(sheet, ColumnMapping(-1, mapOf(0 to BookColumn.TITLE, 1 to BookColumn.AUTHOR)))!!.table
    }

    @Test
    fun theOldFileMergedAgainGivesNothingNew() {
        val plan = MergePlan.plan(Owner.DAD, cleanedOld, oldFile())
        assertEquals(emptyList<Book>(), plan.added)
        assertEquals(emptyList<MergePlan.Changed>(), plan.changed) // empty shelves in the file clear nothing
        assertEquals(emptyList<Book>(), plan.missing)
        assertEquals(emptyList<MergePlan.Ambiguous>(), plan.ambiguous)
        assertEquals(1, plan.mergedDuplicates)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun findsNewChangedAndMissingBooks() {
        val incoming = BookTable(
            listOf(
                Book("Islıklı Kuşların Sokağı", "Nevin Çağlar", "A3"),                       // shelf moved
                Book("SEFİL FENERCİLER", "HALİT ORUÇ"), Book("SEFİL FENERCİLER", "HALİT ORUÇ"), Book("SEFİL FENERCİLER", "HALİT ORUÇ"), // 3 copies now
                Book("HÂLÂ BEKLEYEN MEKTUP", "ŞÜKRÜ ILGAZ"),                                   // signed mark gone
                Book("ÇATIDAKI RASATHANE", "AYŞE İNCE"),                                      // the same, typed without Turkish letters
                Book("KAR TANESİ - İMZALI", "ORHAN PAMİR"),                                   // new, signed
            ),
        )
        val plan = MergePlan.plan(Owner.DAD, cleanedOld, incoming)
        assertEquals(listOf(Book("KAR TANESİ", "ORHAN PAMİR", signed = true)), plan.added)
        val changes = plan.changed.associate { it.existing.title to it.fields.map { f -> f.column to f.new } }
        assertEquals(listOf(BookColumn.SHELF to "A3"), changes["ISLIKLI KUŞLARIN SOKAĞI"])
        assertEquals(listOf(BookColumn.COPIES to "3"), changes["SEFİL FENERCİLER"])
        assertEquals(listOf(BookColumn.SIGNED to ""), changes["HÂLÂ BEKLEYEN MEKTUP"])
        assertEquals(3, plan.changed.size)
        assertEquals(listOf("TARLA DERGİSİ SAYI 1", "YAĞMURUN İSTANBUL'U", "YAZARSIZ BİR ALMANAK"), plan.missing.map { it.title })

        val (changesToApply, result) = MergePlan.changes(plan, MergePlan.Choices(missingOn = setOf(2), changedOff = setOf(2)))!!
        assertEquals(MergePlan.Result(added = 1, changed = 2, removed = 1), result)
        assertTrue(changesToApply.any { it is Change.Remove && it.before.title == "YAZARSIZ BİR ALMANAK" })
        assertEquals("A3", (changesToApply.first { it is Change.Edit } as Change.Edit).after.shelf)
    }

    @Test
    fun typosMatchButVolumesAreAsked() {
        val current = listOf(Book("Suç ve Ceza", "Dostoyevski", "B1"), Book("Kumarbaz Adam Romanı", "Dostoyevski"), Book("NTV Almanak 2006", "NTV"))
        val incoming = BookTable(
            listOf(
                Book("Kumarbaz Adam Romani", "Dostoyevski", "B2"), // one letter off: the same book
                Book("Suç ve Ceza 1. Cilt", "Dostoyevski"),        // the same book or a volume of it: ask
                Book("NTV Almanak 2007", "NTV"),                   // another year: ask
            ),
        )
        val plan = MergePlan.plan(Owner.ME, current, incoming)
        assertEquals(listOf(BookColumn.SHELF), plan.changed.single().fields.map { it.column })
        assertEquals(2, plan.ambiguous.size)
        assertEquals(emptyList<Book>(), plan.added)
        assertEquals(emptyList<Book>(), plan.missing)
        assertNull(MergePlan.changes(plan, MergePlan.Choices()))

        val answers = mapOf(plan.ambiguous[0].key to true, plan.ambiguous[1].key to false)
        val (_, result) = MergePlan.changes(plan, MergePlan.Choices(sameBook = answers))!!
        assertEquals(MergePlan.Result(added = 1, changed = 1, removed = 0), result)

        // the answers are remembered: the next merge does not ask again
        val again = MergePlan.plan(Owner.ME, current, incoming, decisions = answers)
        assertEquals(emptyList<MergePlan.Ambiguous>(), again.ambiguous)
        assertEquals(listOf("NTV Almanak 2007"), again.added.map { it.title })
    }

    @Test
    fun cleaningMergesIdenticalRowsAndMovesDedications() {
        val (books, duplicates) = MergePlan.clean(
            BookTable(listOf(Book("MAVİ KİTAP (BÜLENT ECEVİT'E İMZALI)", "X"), Book("A", "B"), Book(" A ", "B"), Book("", ""))),
        )
        assertEquals(listOf(Book("MAVİ KİTAP", "X", signed = true, note = "Bülent Ecevit'e imzalı"), Book("A", "B", copies = 2)), books)
        assertEquals(1, duplicates)
    }
}
