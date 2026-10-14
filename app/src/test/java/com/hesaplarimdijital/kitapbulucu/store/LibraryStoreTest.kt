package com.hesaplarimdijital.kitapbulucu.store

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookColumn
import com.hesaplarimdijital.kitapbulucu.model.ColumnMapping
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

    @Test
    fun createsAnEmptyListWithHeaders() {
        val fresh = Uri.parse("content://test/new/Benim.xlsx")
        val store = store()
        store.start()
        var result: Boolean? = null
        store.createEmpty(Owner.ME, fresh) { result = it }
        assertEquals(true, result)
        val written = XlsxReader.read(documents.bytes.getValue(fresh).inputStream()).sheets.single()
        assertEquals("Benim", written.name)
        assertEquals(BookTable.HEADERS, written.rows.single())
        assertEquals(LoadStatus.READY, store.state(Owner.ME).status)
        assertEquals(0, store.state(Owner.ME).bookCount)
        assertEquals(fresh, links().link(Owner.ME)!!.uri)
        assertTrue(fresh in documents.kept)

        documents.writeFailure = DocumentFailure.NO_PERMISSION
        val other = Uri.parse("content://test/new/Babam.xlsx")
        store.createEmpty(Owner.DAD, other) { result = it }
        assertEquals(false, result)
        assertNull(links().link(Owner.DAD))
    }

    @Test
    fun aMappingOpensAFileWithoutHeaders() {
        links().set(Owner.DAD, dadUri, "Kütüphane.xlsx")
        documents.bytes[dadUri] = TestWorkbooks.fixture("ornek-eski-bicim.xlsx")
        val store = store()
        store.start()
        // the old layout's "KİTAP | YAZAR" row sits below five books, so it is not taken as the header
        assertEquals(LoadFailure.NO_HEADER, store.state(Owner.DAD).failure)
        val preview = store.previews.value.getValue(Owner.DAD)
        assertEquals("Sayfa1", preview.selected)
        store.setMapping(Owner.DAD, ColumnMapping(-1, mapOf(0 to BookColumn.TITLE, 1 to BookColumn.AUTHOR)))
        val state = store.state(Owner.DAD)
        assertEquals(LoadStatus.READY, state.status)
        assertEquals("ISLIKLI KUŞLARIN SOKAĞI", state.table!!.books.first().title)
        assertEquals("NEVİN ÇAĞLAR", state.table!!.books.first().author)
        assertEquals(8, state.bookCount) // nine rows less the repeated header
        assertTrue(state.table!!.books.none { it.title == "KİTAP" })
        assertNotNull(store.mapping(Owner.DAD))
    }

    // ------------------------------------------------------------------ writing (S-15)

    private val karTanesi = Book("Kar Tanesi", "Orhan Pamir", "B4")

    private fun fileBooks(uri: Uri = dadUri): List<Book> =
        BookTable.parse(XlsxReader.read(documents.bytes.getValue(uri).inputStream()).sheets.first())!!.table.books

    @Test
    fun writesAnAddedBookAfterRereadingTheFile() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        now += 60_000
        store.submit(Change.Add(Owner.DAD, Book("  Kar   Tanesi ", "Orhan Pamir ", "B4")))
        val state = store.state(Owner.DAD)
        assertEquals(LoadStatus.READY, state.status)
        assertEquals(0, state.pendingChanges)
        assertNull(state.writeFailure)
        assertEquals(dadBooks.toList() + karTanesi, state.table!!.books) // tidied on the way in
        assertEquals(dadBooks.toList() + karTanesi, fileBooks())
        assertEquals(1, documents.writes)
        assertEquals(1, store.backups(Owner.DAD).size)
        assertEquals(dadBooks.toList(), BookTable.parse(XlsxReader.read(store.backups(Owner.DAD).single().file.readBytes().inputStream()).sheets.first())!!.table.books)
        assertEquals(now, state.updatedAt)
        assertEquals(1, store.index.value!!.search("kar tanesi").size)
        assertEquals(dadBooks.toList() + karTanesi, BookTable.parse(XlsxReader.read(cache().load(Owner.DAD)!!.bytes.inputStream()).sheets.first())!!.table.books)
        assertEquals(listOf(StoreNotice.FirstWrite), store.notices.value)
        store.dismiss(StoreNotice.FirstWrite)
        store.submit(Change.Add(Owner.DAD, Book("Sessiz Harita", "Leyla Umut")))
        assertEquals(emptyList<StoreNotice>(), store.notices.value) // the format notice shows once
        assertEquals(2, store.backups(Owner.DAD).size)
    }

    @Test
    fun whatOthersWroteInTheMeantimeSurvives() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        // Excel on another device adds a row and fixes a shelf before the app writes
        val external = Book("Sessiz Harita", "Leyla Umut", "E1")
        documents.bytes[dadUri] = workbook(dadBooks[0].copy(shelf = "A9"), dadBooks[1], external)
        store.submit(Change.Add(Owner.DAD, karTanesi))
        assertEquals(listOf(dadBooks[0].copy(shelf = "A9"), dadBooks[1], external, karTanesi), fileBooks())
        assertEquals(listOf(dadBooks[0].copy(shelf = "A9"), dadBooks[1], external, karTanesi), store.state(Owner.DAD).table!!.books)
        assertEquals(emptyList<StoreNotice>(), store.notices.value.filterIsInstance<StoreNotice.ChangesLanded>())
    }

    @Test
    fun aFailedWriteKeepsTheChangeShownAndPending() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        documents.writeFailure = DocumentFailure.NO_PERMISSION
        store.submit(Change.Add(Owner.DAD, karTanesi))
        val failed = store.state(Owner.DAD)
        assertEquals(WriteFailure.NO_PERMISSION, failed.writeFailure)
        assertEquals(1, failed.pendingChanges)
        assertEquals(LoadStatus.READY, failed.status)
        assertEquals(dadBooks.toList() + karTanesi, failed.table!!.books) // shown as if written
        assertEquals(dadBooks.toList(), fileBooks())                     // but the file is untouched
        assertEquals(1, store.backups(Owner.DAD).size)                   // the backup was taken before the attempt
        assertEquals(1, store.pendingChanges(Owner.DAD).size)

        documents.writeFailure = null
        store.refresh() // the next foreground or F5
        val written = store.state(Owner.DAD)
        assertNull(written.writeFailure)
        assertEquals(0, written.pendingChanges)
        assertEquals(dadBooks.toList() + karTanesi, fileBooks())
    }

    @Test
    fun pendingChangesSurviveARestartOfTheApp() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        documents.writeFailure = DocumentFailure.IO
        store().apply {
            start()
            submit(Change.Add(Owner.DAD, karTanesi))
            assertEquals(WriteFailure.CANNOT_WRITE, state(Owner.DAD).writeFailure)
        }
        documents.writeFailure = null
        val restarted = store()
        restarted.start()
        assertEquals(0, restarted.state(Owner.DAD).pendingChanges)
        assertEquals(dadBooks.toList() + karTanesi, fileBooks())
        assertEquals(dadBooks.toList() + karTanesi, restarted.state(Owner.DAD).table!!.books)
    }

    @Test
    fun aDocumentThatReadsBackWrongIsReported() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        documents.corruptWrites = true
        store.submit(Change.Add(Owner.DAD, karTanesi))
        assertEquals(WriteFailure.READ_BACK, store.state(Owner.DAD).writeFailure)
        assertEquals(1, store.state(Owner.DAD).pendingChanges)
        assertEquals(1, store.backups(Owner.DAD).size)
        assertEquals(dadBooks.toList(), BookTable.parse(XlsxReader.read(store.backups(Owner.DAD).single().file.readBytes().inputStream()).sheets.first())!!.table.books)
    }

    @Test
    fun otherSheetsAreCarriedAlongAsText() {
        links().set(Owner.ME, meUri, "Benim.xlsx")
        documents.bytes[meUri] = XlsxWriter.write(
            listOf(
                BookTable(listOf(dadBooks[0])).toSheetSpec("Benim"),
                SheetSpec("Notlar", listOf("Not", "Tarih"), listOf(listOf("Ödünç verdim", "Nis.53"), listOf("", "3-4"))),
            ),
        )
        val store = store()
        store.start()
        assertEquals(listOf("Benim", "Notlar"), store.state(Owner.ME).sheetNames)
        store.submit(Change.Add(Owner.ME, karTanesi))
        assertEquals(LoadStatus.READY, store.state(Owner.ME).status)
        assertNull(store.state(Owner.ME).writeFailure)
        val written = XlsxReader.read(documents.bytes.getValue(meUri).inputStream())
        assertEquals(listOf("Benim", "Notlar"), written.sheets.map { it.name })
        assertEquals(listOf(dadBooks[0], karTanesi), BookTable.parse(written.sheets[0])!!.table.books)
        assertEquals(listOf(listOf("Not", "Tarih"), listOf("Ödünç verdim", "Nis.53"), listOf("", "3-4")), written.sheets[1].rows)
        assertTrue(store.notices.value.contains(StoreNotice.OtherSheetsKept(Owner.ME, listOf("Notlar"))))
        store.submit(Change.Add(Owner.ME, Book("Sessiz Harita", "Leyla Umut")))
        assertEquals(1, store.notices.value.count { it is StoreNotice.OtherSheetsKept }) // warned once
    }

    @Test
    fun refusesToWriteATextFile() {
        links().set(Owner.DAD, dadUri, "Babam.csv")
        documents.bytes[dadUri] = "Kitap Adı;Yazar;Raf\nSefil Fenerciler;Halit Oruç;A1\n".toByteArray()
        val store = store()
        store.start()
        assertEquals(1, store.state(Owner.DAD).bookCount)
        store.submit(Change.Add(Owner.DAD, karTanesi))
        assertEquals(WriteFailure.NOT_XLSX, store.state(Owner.DAD).writeFailure)
        assertEquals(1, store.state(Owner.DAD).pendingChanges)
        assertEquals(0, documents.writes)
        assertEquals(0, store.backups(Owner.DAD).size)
    }

    @Test
    fun changesThatLandDifferentlyAreReported() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        store.submit(Change.Add(Owner.DAD, Book("sefil fenerciler", "Halit Oruç", "Z9")))
        store.submit(Change.Remove(Owner.DAD, karTanesi))
        assertEquals(listOf(dadBooks[0].copy(copies = 3), dadBooks[1]), fileBooks())
        val landed = store.notices.value.filterIsInstance<StoreNotice.ChangesLanded>()
        assertEquals(listOf(ChangeApplier.Outcome.MERGED_INTO_EXISTING, ChangeApplier.Outcome.ALREADY_GONE), landed.flatMap { it.notes }.map { it.outcome })
    }

    @Test
    fun anEmptyDocumentGetsHeadersWithTheFirstBook() {
        links().set(Owner.ME, meUri, "Benim.xlsx")
        documents.bytes[meUri] = ByteArray(0)
        val store = store()
        store.start()
        assertEquals(LoadStatus.FAILED, store.state(Owner.ME).status)
        store.submit(Change.Add(Owner.ME, karTanesi))
        val state = store.state(Owner.ME)
        assertEquals(LoadStatus.READY, state.status)
        assertNull(state.writeFailure)
        assertEquals(listOf(karTanesi), state.table!!.books)
        val written = XlsxReader.read(documents.bytes.getValue(meUri).inputStream()).sheets.single()
        assertEquals("Benim", written.name)
        assertEquals(BookTable.HEADERS, written.rows[0])
    }

    @Test
    fun editsAndRemovalsReachTheFile() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        store.submit(Change.Edit(Owner.DAD, dadBooks[0], dadBooks[0].copy(shelf = "B7", note = "Yeni not")))
        assertEquals(listOf(dadBooks[0].copy(shelf = "B7", note = "Yeni not"), dadBooks[1]), fileBooks())
        store.submit(Change.Remove(Owner.DAD, dadBooks[1]))
        assertEquals(listOf(dadBooks[0].copy(shelf = "B7", note = "Yeni not")), fileBooks())
        assertEquals(1, store.state(Owner.DAD).bookCount)
        assertEquals(2, store.backups(Owner.DAD).size)
    }

    // ------------------------------------------------------------------ undo and moves (S-18, S-19)

    @Test
    fun undoTakesBackAnAddAMergeAnEditAndADelete() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        store.submit(Change.Add(Owner.DAD, karTanesi), "eklendi")
        assertEquals(dadBooks.toList() + karTanesi, fileBooks())
        assertEquals("eklendi", store.lastStep.value!!.label)
        assertTrue(store.undo())
        assertEquals(dadBooks.toList(), fileBooks())
        assertNull(store.lastStep.value)

        store.submit(Change.Add(Owner.DAD, dadBooks[0].copy(copies = 1)), "bir kopya daha")
        assertEquals(3, fileBooks()[0].copies)
        store.undo()
        assertEquals(dadBooks.toList(), fileBooks())

        store.submit(Change.Edit(Owner.DAD, dadBooks[1], dadBooks[1].copy(shelf = "Z1")), "değişti")
        store.submit(Change.Remove(Owner.DAD, dadBooks[0]), "silindi")
        assertEquals(listOf(dadBooks[1].copy(shelf = "Z1")), fileBooks())
        store.undo()
        assertEquals(setOf(dadBooks[0], dadBooks[1].copy(shelf = "Z1")), fileBooks().toSet())
        store.undo()
        assertEquals(setOf(dadBooks[0], dadBooks[1]), fileBooks().toSet())
        assertFalse(store.undo())
    }

    @Test
    fun aMoveWritesTheNewFileFirst() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        links().set(Owner.ME, meUri, "Benim.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        documents.bytes[meUri] = workbook(sheet = "Benim")
        val store = store()
        store.start()
        // the new file cannot be written: the book must stay in the old one
        documents.failures[meUri] = DocumentFailure.IO
        val add = Change.Add(Owner.ME, dadBooks[0])
        store.submitAll(listOf(add, Change.Remove(Owner.DAD, dadBooks[0])), "taşındı")
        assertEquals(dadBooks.toList(), fileBooks(dadUri))
        assertEquals(listOf(dadBooks[1]), store.state(Owner.DAD).table!!.books) // shown as moved
        assertEquals(1, store.pendingChanges(Owner.DAD).size)

        documents.failures.remove(meUri)
        store.refresh()
        assertEquals(listOf(dadBooks[0]), fileBooks(meUri))
        assertEquals(listOf(dadBooks[1]), fileBooks(dadUri))
        assertEquals(0, store.pendingChanges(Owner.DAD).size + store.pendingChanges(Owner.ME).size)

        store.undo()
        assertEquals(setOf(dadBooks[0], dadBooks[1]), fileBooks(dadUri).toSet())
        assertEquals(emptyList<Book>(), fileBooks(meUri))
    }

    @Test
    fun aMoveSurvivesARestartInTheRightOrder() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        links().set(Owner.ME, meUri, "Benim.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        documents.bytes[meUri] = workbook(sheet = "Benim")
        documents.failures[meUri] = DocumentFailure.IO
        store().apply {
            start()
            submitAll(listOf(Change.Add(Owner.ME, dadBooks[1]), Change.Remove(Owner.DAD, dadBooks[1])), "taşındı")
        }
        documents.failures.remove(meUri)
        store().start()
        assertEquals(listOf(dadBooks[1]), fileBooks(meUri))
        assertEquals(listOf(dadBooks[0]), fileBooks(dadUri))
    }

    @Test
    fun aBackupIsPutBackAndTheCurrentFileIsBackedUpFirst() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        now += 60_000
        store.submit(Change.Add(Owner.DAD, Book("Kar Tanesi", "Orhan Pamir", "B4")))
        assertEquals(3, store.state(Owner.DAD).bookCount)
        val backup = store.backups(Owner.DAD).single()
        assertEquals(2, kotlinx.coroutines.runBlocking { store.backupBookCount(Owner.DAD, backup.file) })

        now += 60_000
        var restored: Boolean? = null
        store.restore(Owner.DAD, backup.file) { restored = it }
        assertEquals(true, restored)
        assertEquals(dadBooks.toList(), store.state(Owner.DAD).table!!.books)
        assertEquals(2, store.backups(Owner.DAD).size) // the three-book file was kept too
        assertEquals(3, kotlinx.coroutines.runBlocking { store.backupBookCount(Owner.DAD, store.backups(Owner.DAD).first().file) })
    }

    @Test
    fun bothListsAreExportedAsOneWorkbook() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        links().set(Owner.ME, meUri, "Benim.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        documents.bytes[meUri] = workbook(Book("Kar Tanesi", "Orhan Pamir", "B4"), sheet = "Benim")
        val store = store()
        store.start()
        val target = Uri.parse("content://test/Kitaplar.xlsx")
        var ok: Boolean? = null
        store.exportTo(target) { ok = it }
        assertEquals(true, ok)
        val sheets = com.hesaplarimdijital.kitapbulucu.xlsx.SpreadsheetReader.read(documents.bytes.getValue(target)).filledSheets
        assertEquals(listOf("Babam", "Benim"), sheets.map { it.name })
        assertEquals(dadBooks.toList(), BookTable.parse(sheets[0])!!.table.books)
    }

    @Test
    fun aMergeIsOneUndoStep() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        val incoming = BookTable(List(300) { Book("Yeni $it", "Yazar") })
        val plan = com.hesaplarimdijital.kitapbulucu.importing.MergePlan.plan(Owner.DAD, store.state(Owner.DAD).table!!.books, incoming)
        val (changes, result) = com.hesaplarimdijital.kitapbulucu.importing.MergePlan.changes(plan, com.hesaplarimdijital.kitapbulucu.importing.MergePlan.Choices())!!
        assertEquals(300, result.added)
        store.submitAll(changes, "300 kitap eklendi")
        assertEquals(302, store.state(Owner.DAD).bookCount)
        store.undo()
        assertEquals(dadBooks.toList(), store.state(Owner.DAD).table!!.books)
    }

    // ------------------------------------------------------------------ shelves (S-33, S-34)

    @Test
    fun renamingAShelfIsOneUndoableStepForTheBooksAndTheList() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        val onB2 = Book("Kar Tanesi", "Orhan Pamir", "B2")
        val alsoB2 = Book("Sessiz Harita", "Leyla Umut", "b2")
        documents.bytes[dadUri] = workbook(*dadBooks, onB2, alsoB2)
        val store = store()
        store.start()
        store.shelfCatalog.add(Owner.DAD, "B2", "Salon")
        store.shelfCatalog.add(Owner.DAD, "D1")
        store.renameShelf(Owner.DAD, "B2", "B5", "Salon, üst", "B2 rafının adı B5 oldu")
        assertEquals(dadBooks.toList() + onB2.copy(shelf = "B5") + alsoB2.copy(shelf = "B5"), fileBooks())
        assertEquals(listOf("B5", "D1"), store.shelfCatalog.list(Owner.DAD).map { it.code })
        assertEquals("Salon, üst", store.shelfCatalog.list(Owner.DAD).first().note)
        assertEquals("B2 rafının adı B5 oldu", store.lastStep.value!!.label)

        assertTrue(store.undo())
        assertEquals(dadBooks.toList() + onB2 + alsoB2, fileBooks())
        assertEquals(listOf("B2", "D1"), store.shelfCatalog.list(Owner.DAD).map { it.code })
        assertEquals("Salon", store.shelfCatalog.list(Owner.DAD).first().note)
        assertFalse(store.undo())
    }

    @Test
    fun renamingAnEmptyShelfTouchesNoFile() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        val store = store()
        store.start()
        store.shelfCatalog.add(Owner.DAD, "D1")
        store.renameShelf(Owner.DAD, "D1", "D2", "", "D1 rafının adı D2 oldu")
        assertEquals(0, documents.writes)
        assertEquals(listOf("D2"), store.shelfCatalog.list(Owner.DAD).map { it.code })
        assertFalse(store.canUndo)
        // a shelf only the books name joins the list when renamed
        store.renameShelf(Owner.DAD, "A1", "A1", "Giriş", "A1")
        assertEquals(listOf("D2", "A1"), store.shelfCatalog.list(Owner.DAD).map { it.code })
        assertEquals(0, documents.writes)
    }

    @Test
    fun placingBooksOnAShelfIsOneUndoableStep() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        val unshelved = Book("Sessiz Harita", "Leyla Umut")
        documents.bytes[dadUri] = workbook(*dadBooks, unshelved)
        val store = store()
        store.start()
        val books = store.state(Owner.DAD).table!!.books
        val plan = ShelfPlacement.plan(Owner.DAD, listOf(books[1], books[2]), "D7")
        assertEquals(listOf(dadBooks[1]), plan.moving)
        store.submitAll(plan.changes, "2 kitap D7 rafına kondu")
        assertEquals(listOf(dadBooks[0], dadBooks[1].copy(shelf = "D7"), unshelved.copy(shelf = "D7")), fileBooks())
        assertTrue(store.undo())
        assertEquals(dadBooks.toList() + unshelved, fileBooks())
        assertFalse(store.undo())
    }

    @Test
    fun theShelfListLivesNextToTheCacheNotInTheFile() {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        documents.bytes[dadUri] = workbook(*dadBooks)
        store().apply { start() }.shelfCatalog.add(Owner.DAD, "Z9", "Bodrum")
        assertEquals(0, documents.writes)
        assertTrue(folder.root.resolve("library/shelves.json").isFile)
        assertEquals(listOf("Z9"), store().shelfCatalog.list(Owner.DAD).map { it.code })
    }
}
