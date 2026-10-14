package com.hesaplarimdijital.kitapbulucu.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.hesaplarimdijital.kitapbulucu.ui.Routes
import com.hesaplarimdijital.kitapbulucu.ui.rememberFilePicker
import com.hesaplarimdijital.kitapbulucu.ui.setup.MappingScreen
import com.hesaplarimdijital.kitapbulucu.ui.setup.SetupScreen
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
import com.hesaplarimdijital.kitapbulucu.xlsx.TestWorkbooks
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxWriter
import desktopkit.settings.rememberDeskPreferences
import desktopkit.shell.ShellNavigator
import desktopkit.shell.WidthClass
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Setup, column mapping and the library settings, over made-up files: Babam's list reads
 * (ornek-kutuphane.xlsx), my list is the old header-less layout (ornek-eski-bicim.xlsx) and
 * asks for a mapping.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** My list for the search screenshots: one book both libraries hold, two only I hold. */
    private val myBooks: ByteArray
        get() = XlsxWriter.write(
            listOf(
                BookTable(
                    listOf(
                        Book("Çatıdaki Rasathane", "Ayşe İnce", "1B"),
                        Book("Çatıdaki Kuşların Son Şarkısı", "Ferit Ağaoğlu", "3C", signed = true),
                        Book("Çatıdakiler", "Leyla Umut"),
                    ),
                ).toSheetSpec("Benim"),
            ),
        )

    private fun sampleStore(myList: ByteArray = TestWorkbooks.fixture("ornek-eski-bicim.xlsx")): LibraryStore {
        val documents = FakeDocuments()
        val dad = Uri.parse("content://test/Babam.xlsx")
        val me = Uri.parse("content://test/Benim.xlsx")
        documents.bytes[dad] = TestWorkbooks.fixture("ornek-kutuphane.xlsx")
        documents.bytes[me] = myList
        val links = FileLinks(context.getSharedPreferences("screenshot-links", Context.MODE_PRIVATE))
        links.set(Owner.DAD, dad, "Babam.xlsx")
        links.set(Owner.ME, me, "Benim.xlsx")
        val cache = LibraryCache(File(context.cacheDir, "screenshot-library"))
        return LibraryStore(links, documents, cache, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).also { it.start() }
    }

    private fun sampleSettings(): AppSettings =
        AppSettings(context.getSharedPreferences("screenshot-app", Context.MODE_PRIVATE)).also { it.updateMe(Owner.DAD) }

    private fun capture(name: String, theme: ThemeMode, input: InputMode, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, accent = AppPalette.Bordo, inputMode = input, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    private fun setup(name: String, theme: ThemeMode, input: InputMode) {
        val store = sampleStore()
        val settings = sampleSettings()
        capture(name, theme, input) {
            SetupScreen(store, settings, rememberFilePicker(store), onMap = {}, onDone = {})
        }
    }

    private fun mapping(name: String, theme: ThemeMode, input: InputMode) {
        val store = sampleStore()
        capture(name, theme, input) { MappingScreen(Owner.ME, store, onClose = {}) }
    }

    private fun shell(name: String, theme: ThemeMode, input: InputMode, widthClass: WidthClass, route: String, query: String = "", store: LibraryStore = sampleStore(), open: Boolean = false) {
        val settings = sampleSettings()
        capture(name, theme, input) {
            val prefs = rememberDeskPreferences(accents = AppPalette.accents)
            val navController = rememberNavController()
            LaunchedEffect(route) { if (route != Routes.SEARCH) ShellNavigator(navController).navigate(route) }
            AppShell(prefs, widthClass, navController, store, settings, rememberFilePicker(store), initialSearch = query, initialSearchOpen = open)
        }
    }

    private fun search(name: String, theme: ThemeMode, input: InputMode, widthClass: WidthClass, query: String, open: Boolean = false) =
        shell(name, theme, input, widthClass, Routes.SEARCH, query, sampleStore(myBooks), open)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun setupDexLight() = setup("dex-light-setup", ThemeMode.Light, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun setupDexDark() = setup("dex-dark-setup", ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun setupPhoneLight() = setup("phone-light-setup", ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun setupPhoneDark() = setup("phone-dark-setup", ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun mappingDexLight() = mapping("dex-light-mapping", ThemeMode.Light, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun mappingPhoneDark() = mapping("phone-dark-mapping", ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun settingsDexDark() = shell("dex-dark-settings-library", ThemeMode.Dark, InputMode.Pointer, WidthClass.ExtraLarge, Routes.SETTINGS)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun noticeDexLight() = shell("dex-light-notice", ThemeMode.Light, InputMode.Pointer, WidthClass.ExtraLarge, Routes.LIBRARY)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun searchDexLight() = search("dex-light-search", ThemeMode.Light, InputMode.Pointer, WidthClass.ExtraLarge, "çatıdaki")

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun searchDexDark() = search("dex-dark-search", ThemeMode.Dark, InputMode.Pointer, WidthClass.ExtraLarge, "çatıdaki")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun searchPhoneLight() = search("phone-light-search", ThemeMode.Light, InputMode.Touch, WidthClass.Compact, "çatıdaki")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun searchPhoneDark() = search("phone-dark-search", ThemeMode.Dark, InputMode.Touch, WidthClass.Compact, "çatıdaki")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun searchNothingPhoneLight() = search("phone-light-search-none", ThemeMode.Light, InputMode.Touch, WidthClass.Compact, "kürk mantolu kaplan")

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun searchEmptyDexDark() = search("dex-dark-search-empty", ThemeMode.Dark, InputMode.Pointer, WidthClass.ExtraLarge, "")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun searchOpenedPhoneLight() = search("phone-light-search-open", ThemeMode.Light, InputMode.Touch, WidthClass.Compact, "çatıdaki", open = true)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun searchOpenedDexDark() = search("dex-dark-search-open", ThemeMode.Dark, InputMode.Pointer, WidthClass.ExtraLarge, "çatıdaki", open = true)
}
