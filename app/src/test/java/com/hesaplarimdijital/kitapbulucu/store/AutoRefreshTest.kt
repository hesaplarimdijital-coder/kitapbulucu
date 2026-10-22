package com.hesaplarimdijital.kitapbulucu.store

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.coroutines.CoroutineContext

/**
 * Reading the files again without being asked: on start, on window focus, every minute while
 * resumed; passes never overlap; the "list updated" note only for real changes made elsewhere.
 * Everything runs inline on unconfined dispatchers, like [LibraryStoreTest].
 */
@RunWith(AndroidJUnit4::class)
class AutoRefreshTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dadUri: Uri = Uri.parse("content://test/Babam.xlsx")
    private val meUri: Uri = Uri.parse("content://test/Benim.xlsx")
    private val fake = FakeDocuments()
    private var now = 1_700_000_000_000L
    /** The monotonic clock [AutoRefresh] sees. */
    private var uptime = 5_000_000L
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun links() = FileLinks(context.getSharedPreferences("auto-refresh-links", Context.MODE_PRIVATE))
    private fun store(io: CoroutineDispatcher = Dispatchers.Unconfined) = LibraryStore(
        links(), fake, LibraryCache(folder.root.resolve("library")), CoroutineScope(Dispatchers.Unconfined),
        io = io, clock = { now },
    )

    private fun auto(store: LibraryStore) = AutoRefresh(store::refresh, store::refreshInBackground, clock = { uptime })

    private fun workbook(vararg books: Book, sheet: String = "Babam"): ByteArray =
        XlsxWriter.write(listOf(BookTable(books.toList()).toSheetSpec(sheet)))

    private val dadBooks = arrayOf(Book("Sefil Fenerciler", "Halit Oruç", "A1", 2), Book("Çatıdaki Rasathane", "Ayşe İnce", "C2"))
    private val karTanesi = Book("Kar Tanesi", "Orhan Pamir", "B4")

    /** Runs the store's file work only when the test says so, so a pass can be caught halfway. */
    class ManualDispatcher : CoroutineDispatcher() {
        val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }

    private fun linkedDad(io: CoroutineDispatcher = Dispatchers.Unconfined): LibraryStore {
        links().set(Owner.DAD, dadUri, "Babam.xlsx")
        fake.bytes[dadUri] = workbook(*dadBooks)
        return store(io).also {
            it.start()
            (io as? ManualDispatcher)?.runAll()
        }
    }

    // ------------------------------------------------------------------ when reads happen

    @Test
    fun focusAndTimerKeepTheirDistance() {
        var full = 0
        var background = 0
        val auto = AutoRefresh({ full++ }, { background++ }, clock = { uptime })
        auto.onStart()
        assertEquals(1, full)
        auto.onFocusGained() // the window gets focus right after the start
        uptime += 3_000
        auto.onFocusGained()
        assertEquals(0, background)
        uptime += 8_000 // 11 s after the start
        auto.onFocusGained()
        assertEquals(1, background)

        val waits = ArrayList<Long>()
        runBlocking {
            launch {
                auto.whileResumed { ms ->
                    waits.add(ms)
                    if (waits.size == 3) throw CancellationException("paused")
                    uptime += ms
                }
            }.join()
        }
        // a minute counted from the focus read, then a minute again; two timed reads
        assertEquals(listOf(60_000L, 60_000L, 60_000L), waits)
        assertEquals(3, background)
        auto.onFocusGained() // right after a timed read
        assertEquals(3, background)
        assertEquals(1, full)
    }

    @Test
    fun theTimerReadsAtOnceWhenAMinuteHasPassed() {
        var background = 0
        val auto = AutoRefresh({}, { background++ }, clock = { uptime })
        auto.onStart()
        uptime += 90_000 // paused for a while, then resumed
        val waits = ArrayList<Long>()
        runBlocking {
            launch { auto.whileResumed { ms -> waits.add(ms); throw CancellationException("paused") } }.join()
        }
        assertEquals(1, background)
        assertEquals(listOf(60_000L), waits)
    }

    @Test
    fun windowFocusPicksUpAChangedFile() {
        val store = linkedDad()
        val auto = auto(store)
        auto.onStart()
        fake.bytes[dadUri] = workbook(*dadBooks, karTanesi)
        uptime += 4_000
        val reads = fake.reads
        auto.onFocusGained() // too soon after the start: nothing read
        assertEquals(reads, fake.reads)
        assertEquals(2, store.state(Owner.DAD).bookCount)

        uptime += 10_000
        now += 14_000
        auto.onFocusGained()
        assertEquals(3, store.state(Owner.DAD).bookCount)
        assertEquals(1, store.index.value!!.search("kar tanesi").size)
        assertEquals(LibraryStore.ListsUpdated(setOf(Owner.DAD), now), store.updated.value)
    }

    @Test
    fun theTimerPicksUpAChangedFile() {
        val store = linkedDad()
        val auto = auto(store)
        auto.onStart()
        var ticks = 0
        runBlocking {
            launch {
                auto.whileResumed { ms ->
                    ticks++
                    if (ticks == 2) fake.bytes[dadUri] = workbook(*dadBooks, karTanesi) // changed during the second minute
                    if (ticks == 3) throw CancellationException("paused")
                    uptime += ms
                    now += ms
                }
            }.join()
        }
        assertEquals(3, store.state(Owner.DAD).bookCount)
        assertEquals(setOf(Owner.DAD), store.updated.value!!.owners)
    }

    @Test
    fun pendingChangesAreRetriedByTheTimer() {
        val store = linkedDad()
        fake.writeFailure = DocumentFailure.IO
        store.submit(Change.Add(Owner.DAD, karTanesi))
        assertEquals(WriteFailure.CANNOT_WRITE, store.state(Owner.DAD).writeFailure)
        assertEquals(1, store.backups(Owner.DAD).size)

        // retried in the background again and again: the same file is not backed up again
        repeat(3) {
            now += 60_000
            store.refreshInBackground()
        }
        assertEquals(1, store.backups(Owner.DAD).size)
        assertEquals(1, store.pendingChanges(Owner.DAD).size)

        fake.writeFailure = null
        now += 60_000
        store.refreshInBackground()
        assertEquals(0, store.pendingChanges(Owner.DAD).size)
        assertNull(store.state(Owner.DAD).writeFailure)
        assertEquals(3, BookTable.parse(com.hesaplarimdijital.kitapbulucu.xlsx.XlsxReader.read(fake.bytes[dadUri]!!.inputStream()).sheets.first())!!.table.books.size)
        assertEquals(1, store.backups(Owner.DAD).size) // the copy from before the first attempt is the one before this write
        assertNull(store.updated.value) // the app's own write is not news

        now += 60_000
        store.submit(Change.Remove(Owner.DAD, karTanesi))
        assertEquals(2, store.backups(Owner.DAD).size)
    }

    // ------------------------------------------------------------------ one pass at a time

    @Test
    fun requestsDuringAPassAreFoldedIntoOneMore() {
        val io = ManualDispatcher()
        val store = linkedDad(io)
        val reads = fake.reads
        store.refresh() // a pass starts and waits for its read
        assertEquals(1, io.tasks.size)
        repeat(3) { store.refresh() }
        repeat(3) { store.refreshInBackground() }
        assertEquals(1, io.tasks.size) // no second pass started next to it
        io.runAll()
        assertEquals(reads + 2, fake.reads) // the pass that ran and one more for all the requests
        assertEquals(LoadStatus.READY, store.state(Owner.DAD).status)
    }

    @Test
    fun backgroundRequestsDuringAPassAreDropped() {
        val io = ManualDispatcher()
        val store = linkedDad(io)
        val reads = fake.reads
        store.refreshInBackground()
        repeat(5) { store.refreshInBackground() }
        assertEquals(1, io.tasks.size)
        io.runAll()
        assertEquals(reads + 1, fake.reads)
    }

    @Test
    fun aChangeDuringAPassIsSeenByTheExtraPass() {
        val io = ManualDispatcher()
        val store = linkedDad(io)
        store.refreshInBackground()
        io.tasks.removeFirst().run() // the read of the unchanged file is done
        fake.bytes[dadUri] = workbook(*dadBooks, karTanesi)
        store.refresh() // F5 while the pass is still busy
        io.runAll()
        assertEquals(3, store.state(Owner.DAD).bookCount)
        assertEquals(setOf(Owner.DAD), store.updated.value!!.owners)
    }

    @Test
    fun aBackgroundReadDoesNotSayReading() {
        val io = ManualDispatcher()
        val store = linkedDad(io)
        store.refreshInBackground()
        assertEquals(LoadStatus.READY, store.state(Owner.DAD).status) // halfway: the list stays as it is
        io.runAll()
        store.refresh()
        assertEquals(LoadStatus.LOADING, store.state(Owner.DAD).status) // F5 and the start say "reading"
        io.runAll()
        assertEquals(LoadStatus.READY, store.state(Owner.DAD).status)
    }

    // ------------------------------------------------------------------ the "updated" note

    @Test
    fun theFirstReadIsNotAnUpdate() {
        val store = linkedDad()
        assertEquals(2, store.state(Owner.DAD).bookCount)
        assertNull(store.updated.value)
        store.refresh() // same bytes
        assertNull(store.updated.value)
    }

    @Test
    fun theSameBooksSavedAgainAreNotAnUpdate() {
        val store = linkedDad()
        fake.bytes[dadUri] = workbook(*dadBooks, sheet = "Liste") // other bytes, same books
        store.refreshInBackground()
        assertNull(store.updated.value)
    }

    @Test
    fun changedListsAreGatheredUntilDismissed() {
        links().set(Owner.ME, meUri, "Benim.xlsx")
        fake.bytes[meUri] = workbook(Book("Sessiz Harita", "Leyla Umut", "1"), sheet = "Benim")
        val store = linkedDad()
        assertNull(store.updated.value)

        fake.bytes[meUri] = workbook(Book("Sessiz Harita", "Leyla Umut", "2"), sheet = "Benim")
        now += 60_000
        store.refreshInBackground()
        assertEquals(LibraryStore.ListsUpdated(setOf(Owner.ME), now), store.updated.value)

        fake.bytes[dadUri] = workbook(*dadBooks, karTanesi)
        now += 60_000
        store.refreshInBackground()
        assertEquals(LibraryStore.ListsUpdated(setOf(Owner.ME, Owner.DAD), now), store.updated.value)

        store.dismissUpdated()
        assertNull(store.updated.value)
        store.refreshInBackground() // nothing new
        assertNull(store.updated.value)
    }

    @Test
    fun aChangeSinceTheLastSessionIsAnUpdate() {
        linkedDad()
        fake.bytes[dadUri] = workbook(*dadBooks, karTanesi)
        now += 3_600_000
        val restarted = store()
        restarted.start() // the cached copy first, then the changed file
        assertEquals(3, restarted.state(Owner.DAD).bookCount)
        assertEquals(setOf(Owner.DAD), restarted.updated.value!!.owners)
    }

    @Test
    fun readingAgainAfterRelinkingIsNotAnUpdate() {
        val store = linkedDad()
        val other = Uri.parse("content://test/other/Babam2.xlsx")
        fake.bytes[other] = workbook(Book("Sessiz Harita", "Leyla Umut", "1"))
        store.relink(Owner.DAD, other, "Babam2.xlsx")
        assertEquals(1, store.state(Owner.DAD).bookCount)
        assertNull(store.updated.value)
        store.chooseSheet(Owner.DAD, null)
        assertNull(store.updated.value)
        assertTrue(store.state(Owner.DAD).status == LoadStatus.READY)
    }
}
