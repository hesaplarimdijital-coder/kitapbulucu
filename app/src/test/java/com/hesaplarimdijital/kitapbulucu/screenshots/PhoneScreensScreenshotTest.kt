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
import com.hesaplarimdijital.kitapbulucu.store.Change
import com.hesaplarimdijital.kitapbulucu.ui.ConfirmDialog
import com.hesaplarimdijital.kitapbulucu.ui.DataSection
import com.hesaplarimdijital.kitapbulucu.ui.DialogAction
import com.hesaplarimdijital.kitapbulucu.ui.search.SearchScreen
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
 * The phone (411 dp wide, touch) layouts of search, the add/edit form, the merge screen,
 * settings, setup, column mapping, dialogs, notices and the undo bar, over made-up files.
 * Names start with "phone-light-" or "phone-dark-".
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneScreensScreenshotTest {

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

    private fun search(name: String, theme: ThemeMode, query: String, open: Boolean = false) =
        shell(name, theme, InputMode.Touch, WidthClass.Compact, Routes.SEARCH, query, sampleStore(myBooks), open)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun searchResultsLight() = search("phone-light-search-results", ThemeMode.Light, "çatıdaki")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun searchResultsDark() = search("phone-dark-search-results", ThemeMode.Dark, "çatıdaki")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun searchOpenedLight() = search("phone-light-search-opened", ThemeMode.Light, "çatıdaki", open = true)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun searchOpenedDark() = search("phone-dark-search-opened", ThemeMode.Dark, "çatıdaki", open = true)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun searchNothingDark() = search("phone-dark-search-nothing", ThemeMode.Dark, "kürk mantolu kaplan")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun searchStartLight() = search("phone-light-search-start", ThemeMode.Light, "")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun addLight() = shell("phone-light-add-new", ThemeMode.Light, InputMode.Touch, WidthClass.Compact, Routes.SEARCH, store = sampleStore(myBooks), addFocus = 1)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun addDark() = shell("phone-dark-add-new", ThemeMode.Dark, InputMode.Touch, WidthClass.Compact, Routes.SEARCH, store = sampleStore(myBooks), addFocus = 1)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun editDark() {
        val store = sampleStore(myBooks)
        val book = store.state(Owner.ME).table!!.books.first { it.signed }
        shell("phone-dark-edit-form", ThemeMode.Dark, InputMode.Touch, WidthClass.Compact, Routes.SEARCH, store = store, form = FormRequest.Edit(Owner.ME, book))
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun mergeStartLight() = shell("phone-light-merge-start", ThemeMode.Light, InputMode.Touch, WidthClass.Compact, Routes.SEARCH, store = sampleStore(myBooks), form = FormRequest.Import)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun mergeReviewDark() = merge("phone-dark-merge-review", ThemeMode.Dark, InputMode.Touch, WidthClass.Compact)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun settingsLight() = shell("phone-light-settings-top", ThemeMode.Light, InputMode.Touch, WidthClass.Compact, Routes.SETTINGS, store = sampleStore(myBooks))

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun settingsDark() = shell("phone-dark-settings-top", ThemeMode.Dark, InputMode.Touch, WidthClass.Compact, Routes.SETTINGS, store = sampleStore(myBooks))

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun settingsDataLight() {
        val store = sampleStore(myBooks)
        capture("phone-light-settings-data", ThemeMode.Light, InputMode.Touch) {
            Column(Modifier.verticalScroll(rememberScrollState())) { DataSection(store, onMerge = {}) }
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun setupLight() {
        val store = sampleStore()
        val settings = sampleSettings()
        capture("phone-light-setup-bottom", ThemeMode.Light, InputMode.Touch) {
            SetupScreen(store, settings, rememberFilePicker(store), onMap = {}, onDone = {})
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun mappingLight() {
        val store = sampleStore()
        capture("phone-light-mapping-bottom", ThemeMode.Light, InputMode.Touch) { MappingScreen(Owner.ME, store, onClose = {}) }
    }

    private fun dialog(name: String, theme: ThemeMode, actions: List<DialogAction>) {
        val store = sampleStore(myBooks)
        capture(name, theme, InputMode.Touch, dialogs = true) {
            SearchScreen(store, initialQuery = "çatıdaki")
            ConfirmDialog("“Çatıdaki Rasathane” silinsin mi?", "Kitap Barış'ın listesinden çıkar. Geri al düğmesiyle döndürebilirsiniz.", actions, onDismiss = {})
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun dialogTwoDark() = dialog("phone-dark-dialog-two", ThemeMode.Dark, listOf(DialogAction("Sil", primary = true) {}, DialogAction("Vazgeç") {}))

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun dialogThreeLight() = dialog(
        "phone-light-dialog-three", ThemeMode.Light,
        listOf(DialogAction("Birini sil", primary = true) {}, DialogAction("Hepsini sil (3)") {}, DialogAction("Vazgeç") {}),
    )

    /** The old-format list of mine asks for a column mapping: a notice card above the search screen. */
    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun noticeLight() = shell("phone-light-notice-top", ThemeMode.Light, InputMode.Touch, WidthClass.Compact, Routes.SEARCH)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun undoDark() {
        val store = sampleStore(myBooks)
        store.submit(Change.Add(Owner.ME, Book("Mavi Defter", "Nur Akın", "4D")), "“Mavi Defter” eklendi")
        shell("phone-dark-undo-bar", ThemeMode.Dark, InputMode.Touch, WidthClass.Compact, Routes.SEARCH, "çatıdaki", store)
    }
}
