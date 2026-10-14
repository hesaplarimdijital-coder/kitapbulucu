package com.hesaplarimdijital.kitapbulucu.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
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
import com.hesaplarimdijital.kitapbulucu.ui.DataSection
import com.hesaplarimdijital.kitapbulucu.ui.FormRequest
import com.hesaplarimdijital.kitapbulucu.ui.importing.PickedFile
import com.hesaplarimdijital.kitapbulucu.xlsx.Sheet
import com.hesaplarimdijital.kitapbulucu.xlsx.Workbook
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hesaplarimdijital.kitapbulucu.ui.Routes
import com.hesaplarimdijital.kitapbulucu.ui.rememberFilePicker
import com.hesaplarimdijital.kitapbulucu.ui.setup.MappingScreen
import com.hesaplarimdijital.kitapbulucu.ui.setup.SetupScreen
import com.hesaplarimdijital.kitapbulucu.ui.shelves.ShelfTarget
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
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

    private fun capture(name: String, theme: ThemeMode, input: InputMode, dialogs: Boolean = false, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, accent = AppPalette.Bordo, inputMode = input, fonts = AppFonts.desk, type = AppFonts.type, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            val canvas = Canvas(bitmap)
            window.draw(canvas)
            if (dialogs) drawDialogs(canvas, window, bitmap.width, bitmap.height)
        }
        ShellScreenshotTest.save(name, bitmap)
    }

    /**
     * A dialog is a window of its own and the activity's view does not draw it: the other root
     * views (from WindowManagerGlobal) are drawn on top, centred, over a dimmed screen.
     */
    private fun drawDialogs(canvas: Canvas, activityRoot: View, width: Int, height: Int) {
        val roots: List<View> = try {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            val field = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            (field.get(global) as List<View>).toList()
        } catch (e: Exception) {
            emptyList()
        }
        for (root in roots) {
            if (root === activityRoot || root.width <= 0 || root.height <= 0 || !root.isAttachedToWindow) continue
            canvas.drawColor(0x8C000000.toInt())
            canvas.save()
            canvas.translate((width - root.width) / 2f, (height - root.height) / 2f)
            root.draw(canvas)
            canvas.restore()
        }
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

    private fun shell(
        name: String, theme: ThemeMode, input: InputMode, widthClass: WidthClass, route: String, query: String = "", store: LibraryStore = sampleStore(),
        open: Boolean = false, addFocus: Int = 0, form: FormRequest? = null, importPreview: Pair<PickedFile, Owner>? = null,
        shelf: ShelfTarget? = null, shelfQuery: String? = null, place: Pair<Owner, List<Book>>? = null,
    ) {
        val settings = sampleSettings()
        capture(name, theme, input, dialogs = place != null) {
            val prefs = rememberDeskPreferences(accents = AppPalette.accents)
            val navController = rememberNavController()
            LaunchedEffect(route) { if (route != Routes.SEARCH) ShellNavigator(navController).navigate(route) }
            AppShell(
                prefs, widthClass, navController, store, settings, rememberFilePicker(store), initialSearch = query, initialSearchOpen = open, addFocus = addFocus,
                initialForm = form, importPreview = importPreview, initialShelf = shelf, initialShelfQuery = shelfQuery, initialPlace = place,
            )
        }
    }

    /** An old list to merge into mine: a moved book, a signed marker in the title, a second volume, two new books (one twice). */
    private val oldList: PickedFile
        get() = PickedFile(
            "Eski liste.xlsx",
            Workbook(
                listOf(
                    Sheet(
                        "Liste",
                        listOf(
                            listOf("Kitap Adı", "Yazar", "Raf"),
                            listOf("ÇATIDAKİ RASATHANE", "AYŞE İNCE", "2A"),
                            listOf("Çatıdaki Kuşların Son Şarkısı - İMZALI", "Ferit Ağaoğlu", ""),
                            listOf("Çatıdakiler 2", "Leyla Umut", ""),
                            listOf("Rüzgârlı Kasaba", "Selim Toprak", "4D"),
                            listOf("Mavi Defter", "Nur Akın", "4D"),
                            listOf("Mavi Defter", "Nur Akın", "4D"),
                        ),
                    ),
                ),
            ),
        )

    private fun merge(name: String, theme: ThemeMode, input: InputMode, widthClass: WidthClass) =
        shell(name, theme, input, widthClass, Routes.SEARCH, store = sampleStore(myBooks), form = FormRequest.Import, importPreview = oldList to Owner.ME)

    private fun data(name: String, theme: ThemeMode, input: InputMode) {
        val store = sampleStore(myBooks)
        capture(name, theme, input) {
            Column(Modifier.verticalScroll(rememberScrollState())) { DataSection(store, onMerge = {}) }
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

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun addDexLight() = shell("dex-light-add", ThemeMode.Light, InputMode.Pointer, WidthClass.ExtraLarge, Routes.SEARCH, store = sampleStore(myBooks), addFocus = 1)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun addPhoneDark() = shell("phone-dark-add", ThemeMode.Dark, InputMode.Touch, WidthClass.Compact, Routes.SEARCH, store = sampleStore(myBooks), addFocus = 1)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun libraryDexLight() = shell("dex-light-library", ThemeMode.Light, InputMode.Pointer, WidthClass.ExtraLarge, Routes.LIBRARY, store = sampleStore(myBooks))

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun libraryDexDark() = shell("dex-dark-library", ThemeMode.Dark, InputMode.Pointer, WidthClass.ExtraLarge, Routes.LIBRARY, store = sampleStore(myBooks))

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun libraryPhoneLight() = shell("phone-light-library", ThemeMode.Light, InputMode.Touch, WidthClass.Compact, Routes.LIBRARY, store = sampleStore(myBooks))

    /** The sample lists plus shelves created on the device: two empty ones in Babam's list, a description for my 3C. */
    private fun shelfStore(): LibraryStore = sampleStore(myBooks).also {
        it.shelfCatalog.add(Owner.DAD, "B4", "Salon, sağ dolap")
        it.shelfCatalog.add(Owner.DAD, "C1", "Çalışma odası")
        it.shelfCatalog.add(Owner.ME, "3C", "Yatak odası")
    }

    private fun shelves(name: String, theme: ThemeMode, input: InputMode, widthClass: WidthClass) =
        shell(name, theme, input, widthClass, Routes.SHELVES, store = shelfStore())

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun shelvesDexLight() = shelves("dex-light-shelves", ThemeMode.Light, InputMode.Pointer, WidthClass.ExtraLarge)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun shelvesDexDark() = shelves("dex-dark-shelves", ThemeMode.Dark, InputMode.Pointer, WidthClass.ExtraLarge)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun shelvesPhoneLight() = shelves("phone-light-shelves", ThemeMode.Light, InputMode.Touch, WidthClass.Compact)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun shelvesPhoneDark() = shelves("phone-dark-shelves", ThemeMode.Dark, InputMode.Touch, WidthClass.Compact)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun shelfAddDexLight() = shell(
        "dex-light-shelf-add", ThemeMode.Light, InputMode.Pointer, WidthClass.ExtraLarge, Routes.SHELVES, store = shelfStore(),
        shelf = ShelfTarget(Owner.ME, "3C"), shelfQuery = "çatıdaki",
    )

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun shelfAddPhoneDark() = shell(
        "phone-dark-shelf-add", ThemeMode.Dark, InputMode.Touch, WidthClass.Compact, Routes.SHELVES, store = shelfStore(),
        shelf = ShelfTarget(Owner.ME, "3C"), shelfQuery = "çatıdaki",
    )

    private fun picker(name: String, theme: ThemeMode, input: InputMode, widthClass: WidthClass) {
        val store = shelfStore()
        val book = store.state(Owner.DAD).table!!.books.first { it.title.startsWith("Kürk") }
        shell(name, theme, input, widthClass, Routes.LIBRARY, store = store, place = Owner.DAD to listOf(book))
    }

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun shelfPickerDexDark() = picker("dex-dark-shelf-picker", ThemeMode.Dark, InputMode.Pointer, WidthClass.ExtraLarge)

    // No phone picker screenshot: on the phone qualifiers a dialog with a focused text field never
    // lets Robolectric's Compose go idle (AppNotIdleException), with a lazy or a plain list alike.

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun mergeDexLight() = merge("dex-light-merge", ThemeMode.Light, InputMode.Pointer, WidthClass.ExtraLarge)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun mergeDexDark() = merge("dex-dark-merge", ThemeMode.Dark, InputMode.Pointer, WidthClass.ExtraLarge)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun mergePhoneLight() = merge("phone-light-merge", ThemeMode.Light, InputMode.Touch, WidthClass.Compact)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun dataDexLight() = data("dex-light-settings-data", ThemeMode.Light, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun dataDexDark() = data("dex-dark-settings-data", ThemeMode.Dark, InputMode.Pointer)
}
