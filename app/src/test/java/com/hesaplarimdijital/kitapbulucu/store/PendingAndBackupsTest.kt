package com.hesaplarimdijital.kitapbulucu.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
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
class PendingAndBackupsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val book = Book("Çatıdaki Rasathane", "Ayşe İnce", "C2", 2, true, "Kuyu & Kalem", "1998", "9786050000017", "Kapak \"yırtık\"", listOf("Türkçe", ""))

    @Test
    fun pendingChangesSurviveARestart() {
        val file = File(folder.root, "pending.json")
        val first = PendingChanges(file)
        val add = Change.Add(Owner.ME, book, at = 5L)
        val edit = Change.Edit(Owner.DAD, book, book.copy(shelf = "D1"), at = 6L)
        val remove = Change.Remove(Owner.ME, book.copy(signed = false), at = 7L)
        first.add(add)
        first.add(edit)
        first.add(remove)
        assertTrue(file.isFile)

        val second = PendingChanges(file)
        assertEquals(listOf(add, edit, remove), second.all())
        assertEquals(listOf(add, remove), second.forOwner(Owner.ME))
        second.remove(listOf(add.id, remove.id))
        assertEquals(listOf(edit), PendingChanges(file).all())
        second.clear(Owner.DAD)
        assertFalse(file.exists()) // nothing pending, nothing on disk
        assertEquals(emptyList<Change>(), PendingChanges(file).all())
    }

    @Test
    fun anUnreadablePendingFileIsSetAsideNotLost() {
        val file = File(folder.root, "pending.json")
        file.writeText("{bozuk")
        val pending = PendingChanges(file)
        assertEquals(emptyList<Change>(), pending.all())
        assertFalse(file.exists())
        assertTrue(folder.root.listFiles()!!.any { it.name.startsWith("pending.json.bozuk-") })
    }

    @Test
    fun unknownRecordsAreSkippedNotFatal() {
        val file = File(folder.root, "pending.json")
        file.writeText("""{"changes":[{"type":"teleport","owner":"DAD","id":"x"},{"type":"add","owner":"DAD","id":"y","at":1,"book":{"title":"Kar"}}]}""")
        val all = PendingChanges(file).all()
        assertEquals(1, all.size)
        assertEquals(Book("Kar"), (all.single() as Change.Add).book)
    }

    @Test
    fun booksRoundTripThroughJson() {
        assertEquals(book, ChangeJson.book(ChangeJson.book(book)))
        assertEquals(Book("Kar"), ChangeJson.book(ChangeJson.book(Book("Kar"))))
    }

    @Test
    fun backupsKeepTheLastTen() {
        val backups = Backups(File(folder.root, "backups"))
        for (i in 1..12) backups.save(Owner.DAD, byteArrayOf(i.toByte()), 1_700_000_000_000L + i * 60_000L)
        val kept = backups.list(Owner.DAD)
        assertEquals(10, kept.size)
        assertEquals(12.toByte(), kept.first().file.readBytes().single()) // newest first
        assertEquals(3.toByte(), kept.last().file.readBytes().single())
        assertEquals(emptyList<Backups.Backup>(), backups.list(Owner.ME))
    }

    @Test
    fun twoBackupsInTheSameSecondBothStay() {
        val backups = Backups(File(folder.root, "backups"))
        val a = backups.save(Owner.ME, byteArrayOf(1), 1_700_000_000_000L)
        val b = backups.save(Owner.ME, byteArrayOf(2), 1_700_000_000_000L)
        assertTrue(a.name != b.name)
        assertEquals(2, backups.list(Owner.ME).size)
    }
}
