package com.hesaplarimdijital.kitapbulucu.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.LibraryState
import com.hesaplarimdijital.kitapbulucu.store.LoadFailure
import com.hesaplarimdijital.kitapbulucu.store.LoadStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Turkish sentences about a library's state. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr")
class LibraryTextsTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    private val now = 1_700_000_000_000L
    private val table = BookTable(listOf(Book("Sefil Fenerciler", "Halit Oruç"), Book("Kar Tanesi", "Orhan Pamir")))

    @Test
    fun saysWhenEachListWasUpdated() {
        val ready = LibraryState(Owner.DAD, LoadStatus.READY, table, readAt = now, updatedAt = now - 3 * 60_000)
        assertEquals("Babam'ın listesi 3 dk önce güncellendi", LibraryTexts.updated(resources, Owner.DAD, ready, now))
        assertEquals("Benim listem az önce güncellendi", LibraryTexts.updated(resources, Owner.ME, ready.copy(owner = Owner.ME, updatedAt = now), now))
        assertEquals("Benim listem dün güncellendi", LibraryTexts.updated(resources, Owner.ME, ready.copy(updatedAt = now - 30 * 3_600_000), now))
        assertEquals("Babam'ın listesi 2 sa önce güncellendi", LibraryTexts.updated(resources, Owner.DAD, ready.copy(updatedAt = now - 2 * 3_600_000), now))
        assertEquals("Babam'ın listesi henüz okunmadı", LibraryTexts.updated(resources, Owner.DAD, LibraryState.notLinked(Owner.DAD), now))
        assertEquals("Babam'ın listesi az önce güncellendi", LibraryTexts.updated(resources, Owner.DAD, ready.copy(updatedAt = null), now)) // falls back to the read time
    }

    @Test
    fun describesTheStatus() {
        assertEquals("Dosya seçilmedi", LibraryTexts.status(resources, LibraryState.notLinked(Owner.DAD)))
        assertEquals("Okunuyor…", LibraryTexts.status(resources, LibraryState(Owner.DAD, LoadStatus.LOADING)))
        assertEquals("Son kopya gösteriliyor; dosya okunuyor…", LibraryTexts.status(resources, LibraryState(Owner.DAD, LoadStatus.LOADING, table, fromCache = true)))
        assertEquals("2 kitap", LibraryTexts.status(resources, LibraryState(Owner.DAD, LoadStatus.READY, table)))
        assertEquals("Dosyaya erişim izni kalmamış. Dosyayı yeniden seçin.", LibraryTexts.status(resources, LibraryState(Owner.DAD, LoadStatus.FAILED, table, failure = LoadFailure.NO_PERMISSION)))
        assertEquals("Bu eski bir Excel dosyası (.xls). Excel'de .xlsx olarak kaydedip tekrar deneyin.", LibraryTexts.failure(resources, LoadFailure.OLD_XLS))
        assertEquals("Dosyada sütun başlıkları (Kitap Adı, Yazar…) bulunamadı. Hangi sütunda ne olduğunu bir kez seçin.", LibraryTexts.failure(resources, LoadFailure.NO_HEADER))
    }
}
