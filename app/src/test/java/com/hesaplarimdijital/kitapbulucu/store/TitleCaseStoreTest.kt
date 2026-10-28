package com.hesaplarimdijital.kitapbulucu.store

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxReader
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** The one-time title case fix through the store: one undo step, one safe write per list, a backup first. */
@RunWith(AndroidJUnit4::class)
class TitleCaseStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dadUri: Uri = Uri.parse("content://test/Babam.xlsx")
    private val meUri: Uri = Uri.parse("content://test/Benim.xlsx")
    private val documents = FakeDocuments()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val dadBooks = listOf(
        Book("ÇATIDAKİ RASATHANE", "AYŞE İNCE", "C2", 2, signed = false, "MAVİ YAYINLARI", "1998", "9750000000001", "KENARI YIRTIK", listOf("DEDEMDEN")),
        Book("İSTANBUL'UN SAATLERİ", "A.HAMDİ TANPINAR", "", 1, signed = true, "", "", "", "BÜLENT ECEVİT'E İMZALI", listOf("")),
        Book("Sefil Fenerciler", "Halit Oruç", "A1", 1, extra = listOf("")),
        Book("TBMM VE KÜÇÜK ADAM", "Leyla Umut", "B4", 1, extra = listOf("YARIM")),
    )
    private val myBooks = listOf(Book("KAR VE KÜL", "ORHAN PAMİR", "1B"), Book("Mavi Defter", "Nur Akın", "3C"))

    private fun table(file: ByteArray): BookTable = BookTable.parse(XlsxReader.read(file.inputStream()).sheets.first())!!.table

    private fun store(): LibraryStore {
        val links = FileLinks(context.getSharedPreferences("title-case-links", Context.MODE_PRIVATE))
        links.set(Owner.DAD, dadUri, "Babam.xlsx")
        links.set(Owner.ME, meUri, "Benim.xlsx")
        documents.bytes[dadUri] = XlsxWriter.write(listOf(BookTable(dadBooks, listOf("Bağışlayan")).toSheetSpec("Babam")))
        documents.bytes[meUri] = XlsxWriter.write(listOf(BookTable(myBooks).toSheetSpec("Benim")))
        return LibraryStore(links, documents, LibraryCache(folder.root.resolve("library")), CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined)
            .also { it.start() }
    }

    @Test
    fun thePlanCountsWhatWouldChange() {
        val store = store()
        val plan = runBlocking { store.planTitleCase() }
        assertEquals(3, plan.count(Owner.DAD))
        assertEquals(1, plan.count(Owner.ME))
        assertEquals(
            listOf("ÇATIDAKİ RASATHANE" to "Çatıdaki Rasathane", "AYŞE İNCE" to "Ayşe İnce", "İSTANBUL'UN SAATLERİ" to "İstanbul'un Saatleri"),
            plan.examples,
        )
        assertEquals(0, documents.writes) // asking writes nothing
    }

    @Test
    fun fixesBothListsInOneStepWithOneWriteAndABackupEach() {
        val store = store()
        val originalDad = documents.bytes.getValue(dadUri)
        var done: TitleCaseFix.Plan? = null
        store.fixTitleCase(label = { "${it.total} kitap düzeltildi" }, onDone = { done = it })

        assertEquals(4, done!!.total)
        assertEquals(2, documents.writes) // one write per list
        assertEquals(1, store.backups(Owner.DAD).size)
        assertEquals(1, store.backups(Owner.ME).size)
        assertTrue(store.backups(Owner.DAD).single().file.readBytes().contentEquals(originalDad))
        assertTrue(store.pendingChanges(Owner.DAD).isEmpty() && store.pendingChanges(Owner.ME).isEmpty())
        assertEquals("4 kitap düzeltildi", store.lastStep.value!!.label)

        val dad = table(documents.bytes.getValue(dadUri))
        assertEquals(listOf("Bağışlayan"), dad.extraHeaders) // the unknown column is still there
        assertEquals(
            listOf(
                dadBooks[0].copy(title = "Çatıdaki Rasathane", author = "Ayşe İnce"),
                dadBooks[1].copy(title = "İstanbul'un Saatleri", author = "A.Hamdi Tanpınar"),
                dadBooks[2],
                dadBooks[3].copy(title = "TBMM ve Küçük Adam"),
            ),
            dad.books,
        )
        // shelf, copies, signed, publisher, year, ISBN, note and the unknown column did not move
        assertEquals("MAVİ YAYINLARI", dad.books[0].publisher)
        assertEquals("BÜLENT ECEVİT'E İMZALI", dad.books[1].note)
        assertEquals(listOf("YARIM"), dad.books[3].extra)
        assertEquals(listOf(Book("Kar ve Kül", "Orhan Pamir", "1B"), myBooks[1]), table(documents.bytes.getValue(meUri)).books)
        assertEquals(dad.books, store.state(Owner.DAD).table!!.books)

        // nothing is left to fix
        assertEquals(0, runBlocking { store.planTitleCase() }.total)
    }

    @Test
    fun oneUndoTakesBothListsBack() {
        val store = store()
        store.fixTitleCase(label = { "düzeltildi" })
        assertTrue(store.undo())
        assertFalse(store.canUndo)
        assertNull(store.lastStep.value)
        assertEquals(4, documents.writes) // the undo is written once per list too
        assertEquals(dadBooks, table(documents.bytes.getValue(dadUri)).books)
        assertEquals(myBooks, table(documents.bytes.getValue(meUri)).books)
        assertEquals(4, runBlocking { store.planTitleCase() }.total)
    }

    @Test
    fun aFailedWriteKeepsTheFixPending() {
        val store = store()
        documents.writeFailure = DocumentFailure.NO_PERMISSION
        store.fixTitleCase(label = { "düzeltildi" })
        assertEquals(3, store.pendingChanges(Owner.DAD).size)
        assertEquals(1, store.pendingChanges(Owner.ME).size)
        assertEquals("Çatıdaki Rasathane", store.state(Owner.DAD).table!!.books[0].title) // shown at once
        documents.writeFailure = null
        store.refresh()
        assertTrue(store.pendingChanges(Owner.DAD).isEmpty())
        assertEquals("Çatıdaki Rasathane", table(documents.bytes.getValue(dadUri)).books[0].title)
    }
}
