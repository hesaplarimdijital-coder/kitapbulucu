package com.hesaplarimdijital.kitapbulucu.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.model.ShelfEntry
import com.hesaplarimdijital.kitapbulucu.model.Shelves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** Needs Robolectric for org.json. */
@RunWith(AndroidJUnit4::class)
class ShelfCatalogTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun file() = File(folder.root, "library/shelves.json")

    @Test
    fun shelvesAreKeptPerListAndSurviveARestart() {
        val first = ShelfCatalog(file())
        assertTrue(first.add(Owner.ME, " B3 ", "Salon,  sağ dolap"))
        assertTrue(first.add(Owner.ME, "A1"))
        assertTrue(first.add(Owner.DAD, "B3"))
        assertEquals(listOf(ShelfEntry("B3", "Salon, sağ dolap"), ShelfEntry("A1")), first.entries.value[Owner.ME])
        assertTrue(file().isFile)

        val second = ShelfCatalog(file())
        assertEquals(listOf(ShelfEntry("B3", "Salon, sağ dolap"), ShelfEntry("A1")), second.list(Owner.ME))
        assertEquals(listOf(ShelfEntry("B3")), second.list(Owner.DAD))
        assertTrue(second.contains(Owner.ME, "b 3"))
        assertFalse(second.contains(Owner.ME, "C1"))
    }

    @Test
    fun anEmptyOrExistingCodeIsNotAdded() {
        val catalog = ShelfCatalog(file())
        assertFalse(catalog.add(Owner.ME, "   "))
        assertTrue(catalog.add(Owner.ME, "b3"))
        assertFalse(catalog.add(Owner.ME, "B3"))
        assertFalse(catalog.add(Owner.ME, "B 3"))
        assertEquals(listOf(ShelfEntry("b3")), catalog.list(Owner.ME))
        assertEquals(emptyList<ShelfEntry>(), catalog.list(Owner.DAD))
    }

    @Test
    fun renameKeepsThePlaceAndTakesInShelvesOnlyBooksNamed() {
        val catalog = ShelfCatalog(file())
        catalog.add(Owner.ME, "A1")
        catalog.add(Owner.ME, "B3", "Salon")
        catalog.add(Owner.ME, "C1")
        catalog.rename(Owner.ME, "b3", "B5", "Salon, üst raf")
        assertEquals(listOf(ShelfEntry("A1"), ShelfEntry("B5", "Salon, üst raf"), ShelfEntry("C1")), catalog.list(Owner.ME))
        // a shelf only the books named joins the list under its new name
        catalog.rename(Owner.ME, "D4", "D5", "")
        assertEquals(listOf("A1", "B5", "C1", "D5"), catalog.list(Owner.ME).map { it.code })
        // the spelling may change; the list does not grow
        catalog.rename(Owner.ME, "D5", "d5", "Kiler")
        assertEquals(listOf(ShelfEntry("A1"), ShelfEntry("B5", "Salon, üst raf"), ShelfEntry("C1"), ShelfEntry("d5", "Kiler")), ShelfCatalog(file()).list(Owner.ME))
        // an empty new name changes nothing
        catalog.rename(Owner.ME, "A1", " ", "")
        assertEquals(4, catalog.list(Owner.ME).size)
    }

    @Test
    fun removeTakesOnlyThatShelf() {
        val catalog = ShelfCatalog(file())
        catalog.add(Owner.ME, "A1")
        catalog.add(Owner.ME, "B3")
        catalog.add(Owner.DAD, "B3")
        assertTrue(catalog.remove(Owner.ME, "b3"))
        assertFalse(catalog.remove(Owner.ME, "B3"))
        assertEquals(listOf(ShelfEntry("A1")), catalog.list(Owner.ME))
        assertEquals(listOf(ShelfEntry("B3")), ShelfCatalog(file()).list(Owner.DAD))
    }

    @Test
    fun anUnreadableFileIsKeptAsideAndTheListStartsEmpty() {
        file().parentFile!!.mkdirs()
        file().writeText("{bozuk")
        val catalog = ShelfCatalog(file())
        assertEquals(emptyList<ShelfEntry>(), catalog.list(Owner.ME))
        assertTrue(folder.root.resolve("library").listFiles()!!.any { it.name.startsWith("shelves.json.bozuk-") })
        assertTrue(catalog.add(Owner.ME, "A1"))
    }

    // ------------------------------------------------------------------ placing books (S-34, S-35)

    private val onB2 = Book("Kar Tanesi", "Orhan Pamir", "B2")
    private val unshelved = Book("Sessiz Harita", "Leyla Umut")
    private val onB3 = Book("Mavi Defter", "Nur Akın", "B3")
    private val onB3Lower = Book("Rüzgârlı Kasaba", "Selim Toprak", "b3")

    @Test
    fun placingWorksOutWhatChangesAndWhatMoves() {
        val plan = ShelfPlacement.plan(Owner.ME, listOf(onB2, unshelved, onB3, onB3Lower), " B3 ")
        assertEquals("B3", plan.shelf)
        assertEquals(
            listOf(
                Change.Edit(Owner.ME, onB2, onB2.copy(shelf = "B3")),
                Change.Edit(Owner.ME, unshelved, unshelved.copy(shelf = "B3")),
                Change.Edit(Owner.ME, onB3Lower, onB3Lower.copy(shelf = "B3")),
            ).map { it.owner to it.before to it.after },
            plan.changes.map { it.owner to it.before to it.after },
        )
        assertEquals(listOf(onB2), plan.moving)
        assertEquals(listOf(onB3, onB3Lower), plan.already)
        assertEquals(listOf("B2"), plan.fromShelves)
    }

    @Test
    fun aBookAlreadyThereNeedsNothing() {
        val plan = ShelfPlacement.plan(Owner.DAD, listOf(onB3), "B3")
        assertTrue(plan.changes.isEmpty())
        assertTrue(plan.moving.isEmpty())
    }

    @Test
    fun renamingChangesEveryBookOnThatShelfOnly() {
        val changes = ShelfPlacement.rename(Owner.ME, listOf(onB2, onB3, unshelved, onB3Lower), "B3", "B4")
        assertEquals(listOf(onB3.copy(shelf = "B4"), onB3Lower.copy(shelf = "B4")), changes.map { it.after })
        assertEquals(listOf("B3", "b3"), changes.map { it.before.shelf })
        assertTrue(ShelfPlacement.rename(Owner.ME, listOf(unshelved), "", "B4").isEmpty())
        assertEquals(Shelves.key("B4"), Shelves.key(changes.first().after.shelf))
    }
}
