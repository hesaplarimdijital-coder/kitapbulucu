package com.hesaplarimdijital.kitapbulucu.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.FakeDocuments
import com.hesaplarimdijital.kitapbulucu.store.FileLinks
import com.hesaplarimdijital.kitapbulucu.store.LibraryCache
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.ui.AppShell
import com.hesaplarimdijital.kitapbulucu.ui.rememberFilePicker
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxWriter
import desktopkit.settings.rememberDeskPreferences
import desktopkit.shell.WidthClass
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The quiet "MKE'nin listesi güncellendi" note after a re-read found the father's list changed
 * on another device: DeX light and phone dark, over made-up books.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RefreshScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun workbook(sheet: String, vararg books: Book): ByteArray = XlsxWriter.write(listOf(BookTable(books.toList()).toSheetSpec(sheet)))

    /** Both lists read once; then the father's list gets a new book and is read again. */
    private fun updatedStore(): LibraryStore {
        val documents = FakeDocuments()
        val dad = Uri.parse("content://test/Babam.xlsx")
        val me = Uri.parse("content://test/Benim.xlsx")
        val dadBooks = arrayOf(Book("Çatıdaki Rasathane", "Ayşe İnce", "C2"), Book("Sefil Fenerciler", "Halit Oruç", "A1", 2))
        documents.bytes[dad] = workbook("Babam", *dadBooks)
        documents.bytes[me] = workbook("Benim", Book("Çatıdaki Kuşların Son Şarkısı", "Ferit Ağaoğlu", "3C", signed = true), Book("Çatıdakiler", "Leyla Umut"))
        val links = FileLinks(context.getSharedPreferences("refresh-screenshot-links", Context.MODE_PRIVATE))
        links.set(Owner.DAD, dad, "Babam.xlsx")
        links.set(Owner.ME, me, "Benim.xlsx")
        val cache = LibraryCache(File(context.cacheDir, "refresh-screenshot-library"))
        val store = LibraryStore(links, documents, cache, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined)
        store.start()
        documents.bytes[dad] = workbook("Babam", *dadBooks, Book("Çatıdaki Saat", "Nur Akın", "C3"))
        store.refreshInBackground()
        assertEquals(setOf(Owner.DAD), store.updated.value!!.owners)
        return store
    }

    private fun capture(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode) {
        val store = updatedStore()
        val settings = AppSettings(context.getSharedPreferences("refresh-screenshot-app", Context.MODE_PRIVATE)).also { it.updateMe(Owner.ME) }
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, accent = AppPalette.Bordo, inputMode = input, fonts = AppFonts.desk, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    val prefs = rememberDeskPreferences(accents = AppPalette.accents)
                    AppShell(prefs, widthClass, rememberNavController(), store, settings, rememberFilePicker(store), initialSearch = "çatıdaki")
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun dexLight() = capture("dex-light-refresh-notice", WidthClass.ExtraLarge, ThemeMode.Light, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneDark() = capture("phone-dark-refresh-notice", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch)
}
