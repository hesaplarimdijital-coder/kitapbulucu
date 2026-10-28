package com.hesaplarimdijital.kitapbulucu.ui.add

import android.content.Context
import android.net.Uri
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.FakeDocuments
import com.hesaplarimdijital.kitapbulucu.store.FileLinks
import com.hesaplarimdijital.kitapbulucu.store.LibraryCache
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Made-up lists for the suggestion tests: authors and publishers in both libraries. */
object SuggestFixtures {
    private fun book(n: Int, author: String, publisher: String) = Book("Defter %02d".format(n), author, "A" + (n % 4 + 1), publisher = publisher)

    private val dad = BookTable(
        listOf(
            book(1, "Ayşe İnce", "Türkiye İş Bankası Yayınları"),
            book(2, "Ayhan Ergin", "Türkiye İş Bankası Yayınları"),
            book(3, "Aysel Tuna", "Türkiye İş Bankası Yayınları"),
            book(4, "Cemal Aykut", "İş Matbaası"),
            book(5, "Leyla Umut", "Can Yayınları"),
        ),
    )

    private val me = BookTable(
        listOf(
            book(6, "Ayşe İnce", "İşaret Yayınları"),
            book(7, "Ayten Kaya", "Işık Yayınları"),
            book(8, "Selim Toprak", "Isparta Kitabevi"),
            book(9, "Nur Akın", "Can Yayınları"),
            book(10, "Selim Toprak", "İş Matbaası"),
        ),
    )

    /** A store over two made-up files, plus the settings of a device that is "mine". */
    fun store(context: Context, name: String): LibraryStore {
        val documents = FakeDocuments()
        val dadUri = Uri.parse("content://test/Babam.xlsx")
        val meUri = Uri.parse("content://test/Benim.xlsx")
        documents.bytes[dadUri] = XlsxWriter.write(listOf(dad.toSheetSpec("Babam")))
        documents.bytes[meUri] = XlsxWriter.write(listOf(me.toSheetSpec("Benim")))
        val links = FileLinks(context.getSharedPreferences("$name-links", Context.MODE_PRIVATE))
        links.set(Owner.DAD, dadUri, "Babam.xlsx")
        links.set(Owner.ME, meUri, "Benim.xlsx")
        val cache = LibraryCache(File(context.cacheDir, "$name-library"))
        return LibraryStore(links, documents, cache, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).also { it.start() }
    }

    fun settings(context: Context, name: String): AppSettings =
        AppSettings(context.getSharedPreferences("$name-app", Context.MODE_PRIVATE)).also { it.updateMe(Owner.ME) }
}
