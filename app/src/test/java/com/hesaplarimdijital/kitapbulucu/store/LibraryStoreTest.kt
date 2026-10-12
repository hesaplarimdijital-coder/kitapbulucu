package com.hesaplarimdijital.kitapbulucu.store

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.xlsx.SheetSpec
import com.hesaplarimdijital.kitapbulucu.xlsx.TestWorkbooks
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxReader
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Needs Robolectric for Uri and SharedPreferences; everything runs inline on unconfined dispatchers. */
@RunWith(AndroidJUnit4::class)
class LibraryStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Documents in memory: a uri maps to bytes, or to a failure. */
    private class FakeDocuments : Documents {
        val bytes = HashMap<Uri, ByteArray>()
        val failures = HashMap<Uri, DocumentFailure>()
        val infos = HashMap<Uri, DocumentInfo>()
        var children = listOf<String>()
        var reads = 0

        override fun info(uri: Uri): DocumentInfo? = infos[uri]
        override fun readBytes(uri: Uri, maxBytes: Int): ByteArray {
            reads++
            failures[uri]?.let { throw DocumentException(it, "test failure") }
            return bytes[uri] ?: throw DocumentException(DocumentFailure.NOT_FOUND, "no such document")
        }
        override fun childNames(treeUri: Uri): List<String> = children
    }

    private val dadUri: Uri = Uri.parse("content://test/Babam.xlsx")
    private val meUri: Uri = Uri.parse("content://test/Benim.xlsx")
    private val documents = FakeDocuments()
    private var now = 1_700_000_000_000L
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var prefsName = "links-test"

    private fun links() = FileLinks(context.getSharedPreferences(prefsName, Context.MODE_PRIVATE))
    private fun cache() = LibraryCache(folder.root.resolve("library"))
    private fun store() = LibraryStore(links(), documents, cache(), CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined, clock = { now })

    private fun workbook(vararg books: Book, sheet: String = "Babam"): ByteArray =
        XlsxWriter.write(listOf(BookTable(books.toList()).toSheetSpec(sheet)))

    private val dadBooks = arrayOf(Book("Sefil Fenerciler", "Halit Oruç", "A1", 2), Book("Çatıdaki Rasathane", "Ayşe İnce", "C2"))

    @Test
    fun nothingLinkedMeansNothingToRead() {
        val store = store()
        store.start()
        assertEquals(LoadStatus.NOT_LINKED, store.state(Owner.DAD).status)
        assertEquals(LoadStatus.NOT_LINKED, store.state(Owner.ME).status)
        assertEquals(0, store.index.value!!.size)
        assertEquals(0, documents.reads)
    }

    @Test
    fun readsALinkedFileAndRemembersIt() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        documents.infos[dadUri] = DocumentInfo("Babam.xlsx", lastModified = now - 60_000, size = 3000)
        val store = store()
        store.start()

        val state = store.state(Owner.DAD)
        assertEquals(LoadStatus.READY, state.status)
        assertEquals(dadBooks.toList(), state.table!!.books)
        assertFalse(state.fromCache)
        assertEquals(now, state.readAt)
        assertEquals(now - 60_000, state.updatedAt) // the provider's time; the first read is not a change
        assertEquals("Babam.xlsx", state.displayName)
        assertEquals(2, store.index.value!!.size)
        assertEquals("Sefil Fenerciler", store.index.value!!.search("sefil").single().book.title)
        assertNotNull(cache().load(Owner.DAD))
        assertEquals(LoadStatus.NOT_LINKED, store.state(Owner.ME).status)
    }

    @Test
    fun theCachedCopyStaysWhenTheFileCannotBeRead() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        store().start()

        documents.failures[dadUri] = DocumentFailure.NO_PERMISSION
        now += 5 * 60_000
        val second = store()
        second.start()
        val state = second.state(Owner.DAD)
        assertEquals(LoadStatus.FAILED, state.status)
        assertEquals(LoadFailure.NO_PERMISSION, state.failure)
        assertEquals(dadBooks.toList(), state.table!!.books) // the last good list is still there
        assertTrue(state.fromCache)
        assertEquals(now - 5 * 60_000, state.readAt)
        assertEquals(2, second.index.value!!.size)
    }

    @Test
    fun unchangedBytesLeaveTheTableAndIndexAlone() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        val table = store.state(Owner.DAD).table
        val index = store.index.value
        now += 60_000
        store.refresh()
        assertSame(table, store.state(Owner.DAD).table)
        assertSame(index, store.index.value)
        assertEquals(now, store.state(Owner.DAD).readAt)
        assertEquals(LoadStatus.READY, store.state(Owner.DAD).status)
    }

    @Test
    fun changedBytesUpdateTheTableQuietly() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        val firstIndex = store.index.value
        now += 3 * 60_000
        documents.bytes[dadUri] = workbook(*dadBooks, Book("Kar Tanesi", "Orhan Pamir", "B4"))
        store.refresh()
        val state = store.state(Owner.DAD)
        assertEquals(3, state.bookCount)
        assertEquals(now, state.updatedAt) // the change was seen now
        assertNotSame(firstIndex, store.index.value)
        assertEquals(1, store.index.value!!.search("kar tanesi").size)
        assertEquals(3, cache().load(Owner.DAD)!!.let { BookTable.parse(XlsxReader.read(it.bytes.inputStream()).sheets.single())!!.table.books.size })
    }

    @Test
    fun explainsWhatWentWrong() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        links().set(Owner.ME, meUri, "Benim.xls")
        documents.failures[dadUri] = DocumentFailure.NOT_FOUND
        documents.bytes[meUri] = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(), 0, 0)
        val store = store()
        store.start()
        assertEquals(LoadFailure.NOT_FOUND, store.state(Owner.DAD).failure)
        assertEquals(LoadFailure.OLD_XLS, store.state(Owner.ME).failure)
        assertNull(store.state(Owner.ME).table)

        documents.failures.remove(dadUri)
        documents.bytes[dadUri] = "Kitap Adı;Yazar\n".toByteArray().copyOf(5) // a cut-off text file: no header row
        documents.bytes[meUri] = TestWorkbooks.fixture("bozuk.xlsx") // a cut-off workbook
        store.refresh()
        assertEquals(LoadFailure.NO_HEADER, store.state(Owner.DAD).failure)
        assertEquals(LoadFailure.DAMAGED, store.state(Owner.ME).failure)
    }

    @Test
    fun warnsOnceAboutStrayFiles() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        links().treeUri = Uri.parse("content://test/tree/kitaplar")
        documents.bytes[dadUri] = workbook(*dadBooks)
        documents.children = listOf("Babam.xlsx", "~\$Babam.xlsx", "Benim (çakışan kopya 2026-10-12).xlsx", "Notlar.txt")
        val store = store()
        store.start()
        assertEquals(listOf("~\$Babam.xlsx", "Benim (çakışan kopya 2026-10-12).xlsx"), store.warning.value!!.names)
        store.dismissWarning()
        assertNull(store.warning.value)
        store.refresh()
        assertNull(store.warning.value)
        documents.children = documents.children + "Babam (conflicted copy).xlsx"
        store.refresh()
        assertEquals(listOf("Babam (conflicted copy).xlsx"), store.warning.value!!.names)
    }

    @Test
    fun relinkingStartsAfresh() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        val other = Uri.parse("content://test/other/Babam2.xlsx")
        documents.bytes[other] = workbook(Book("Sessiz Harita", "Leyla Umut", "1"))
        store.relink(Owner.DAD, other, "Babam2.xlsx")
        val state = store.state(Owner.DAD)
        assertEquals(LoadStatus.READY, state.status)
        assertEquals(listOf("Sessiz Harita"), state.table!!.books.map { it.title })
        assertEquals("Babam2.xlsx", state.displayName)
        assertNull(state.updatedAt) // nothing is known about this file's history yet
        assertEquals(other, links().link(Owner.DAD)!!.uri)

        store.unlink(Owner.DAD)
        assertEquals(LoadStatus.NOT_LINKED, store.state(Owner.DAD).status)
        assertNull(links().link(Owner.DAD))
        assertNull(cache().load(Owner.DAD))
        assertEquals(0, store.index.value!!.size)
    }

    @Test
    fun offersTheFilledSheetsAndReadsTheChosenOne() {
        links().set(Owner.ME, meUri, "Ortak.xlsx")
        documents.bytes[meUri] = XlsxWriter.write(
            listOf(
                BookTable(listOf(dadBooks[0])).toSheetSpec("Babam"),
                SheetSpec("Boş", listOf("Kitap Adı", "Yazar"), emptyList()),
                BookTable(listOf(Book("Sessiz Harita", "Leyla Umut", "1"))).toSheetSpec("Benim"),
            ),
        )
        val store = store()
        store.start()
        assertEquals(listOf("Babam", "Boş", "Benim"), store.state(Owner.ME).sheetNames) // a header-only sheet counts as filled
        assertEquals("Sefil Fenerciler", store.state(Owner.ME).table!!.books.single().title)
        store.chooseSheet(Owner.ME, "Benim")
        assertEquals("Sessiz Harita", store.state(Owner.ME).table!!.books.single().title)
        assertEquals("Benim", links().sheetName(Owner.ME))
    }

    @Test
    fun recognisesStrayFileNames() {
        assertTrue(LibraryStore.isStrayFile("~\$Babam.xlsx"))
        assertTrue(LibraryStore.isStrayFile("Babam (çakışan kopya 2026-10-12).xlsx"))
        assertTrue(LibraryStore.isStrayFile("Babam (Çakışan Kopya).xlsx"))
        assertTrue(LibraryStore.isStrayFile("Benim (Ali's conflicted copy 2026-10-12).xlsx"))
        assertFalse(LibraryStore.isStrayFile("Babam.xlsx"))
        assertFalse(LibraryStore.isStrayFile("Kopya Babam.xlsx"))
    }
}
