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
import com.hesaplarimdijital.kitapbulucu.ui.Routes
import com.hesaplarimdijital.kitapbulucu.ui.SheetHost
import com.hesaplarimdijital.kitapbulucu.ui.library.LibraryScreen
import com.hesaplarimdijital.kitapbulucu.ui.rememberAppSpec
import com.hesaplarimdijital.kitapbulucu.ui.rememberBookActions
import com.hesaplarimdijital.kitapbulucu.ui.shelves.ShelfFormDialog
import com.hesaplarimdijital.kitapbulucu.ui.shelves.ShelfTarget
import com.hesaplarimdijital.kitapbulucu.ui.shelves.ShelvesScreen
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
import com.hesaplarimdijital.kitapbulucu.xlsx.TestWorkbooks
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxWriter
import desktopkit.shell.DesktopShell
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
 * The phone layout of the Library and Shelves screens over made-up lists: the two-line list,
 * selection mode, a book's action sheet, the shelves, an open shelf, adding books to it and the
 * "Rafa koy…" sheet. Each in the light and the dark theme, at 411 dp portrait.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneListsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** My list: a book both libraries hold, a signed one, one without a shelf. */
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

    /** Babam's list is the sample workbook; mine the three books above; shelves created on the device. */
    private fun sampleStore(): LibraryStore {
        val documents = FakeDocuments()
        val dad = Uri.parse("content://test/Babam.xlsx")
        val me = Uri.parse("content://test/Benim.xlsx")
        documents.bytes[dad] = TestWorkbooks.fixture("ornek-kutuphane.xlsx")
        documents.bytes[me] = myBooks
        val links = FileLinks(context.getSharedPreferences("phone-lists-links", Context.MODE_PRIVATE))
        links.set(Owner.DAD, dad, "Babam.xlsx")
        links.set(Owner.ME, me, "Benim.xlsx")
        val cache = LibraryCache(File(context.cacheDir, "phone-lists-library"))
        return LibraryStore(links, documents, cache, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).also {
            it.start()
            it.shelfCatalog.add(Owner.DAD, "B4", "Salon, sağ dolap")
            it.shelfCatalog.add(Owner.DAD, "C1", "Çalışma odası")
            it.shelfCatalog.add(Owner.ME, "3C", "Yatak odası")
        }
    }

    private fun sampleSettings(): AppSettings =
        AppSettings(context.getSharedPreferences("phone-lists-app", Context.MODE_PRIVATE)).also { it.updateMe(Owner.DAD) }

    private fun capture(name: String, theme: ThemeMode, input: InputMode = InputMode.Touch, dialogs: Boolean = false, content: @Composable () -> Unit) {
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

    /** Dialog windows (the sheets) are drawn on top of the activity, over a dimmed screen. */
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

    /** The two screens in the kit's shell at phone width, with the screenshot hooks of each. */
    private fun phone(
        name: String,
        theme: ThemeMode,
        route: String,
        selected: (LibraryStore) -> List<Int> = { emptyList() },
        sheet: ((LibraryStore) -> Int)? = null,
        shelf: ShelfTarget? = null,
        shelfQuery: String? = null,
        place: ((LibraryStore) -> Pair<Owner, List<Book>>)? = null,
        dialogs: Boolean = false,
        extra: @Composable () -> Unit = {},
    ) {
        val store = sampleStore()
        val settings = sampleSettings()
        val selectedRows = selected(store)
        val sheetRow = sheet?.invoke(store)
        val placing = place?.invoke(store)
        capture(name, theme, dialogs = dialogs) {
            val spec = rememberAppSpec()
            val navController = rememberNavController()
            val actions = rememberBookActions(store, settings) { _, _ -> }
            LaunchedEffect(route) { ShellNavigator(navController).navigate(route) }
            LaunchedEffect(placing) { placing?.let { actions.placeOnShelf(it.first, it.second) } }
            // as in AppShell: the sheets are drawn in the app's window, over the shell
            SheetHost {
                DesktopShell(spec = spec, widthClass = WidthClass.Compact, navController = navController, onSearch = {}) { current, _ ->
                    val label = spec.item(current)?.label.orEmpty()
                    when (current) {
                        Routes.LIBRARY -> LibraryScreen(store, settings, actions, label, onAdd = {}, onShelf = { _, _ -> }, previewSelected = selectedRows, previewSheet = sheetRow)
                        Routes.SHELVES -> ShelvesScreen(store, settings, actions, label, shelf, initialAddQuery = shelfQuery)
                        else -> Unit
                    }
                }
                actions.Dialogs()
                extra()
            }
        }
    }

    private fun dadPosition(store: LibraryStore, prefix: String): Int =
        store.state(Owner.DAD).table!!.books.indexOfFirst { it.title.startsWith(prefix) }.coerceAtLeast(0)

    private fun kurk(store: LibraryStore): Book = store.state(Owner.DAD).table!!.books.first { it.title.startsWith("Kürk") }

    // ---------------------------------------------------------------- library

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun libraryLight() = phone("phone-light-library-list", ThemeMode.Light, Routes.LIBRARY)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun libraryDark() = phone("phone-dark-library-list", ThemeMode.Dark, Routes.LIBRARY)

    private val twoRows: (LibraryStore) -> List<Int> = { store -> listOf(dadPosition(store, "Kürk"), 0) }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun selectingLight() = phone("phone-light-library-selecting", ThemeMode.Light, Routes.LIBRARY, selected = twoRows)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun selectingDark() = phone("phone-dark-library-selecting", ThemeMode.Dark, Routes.LIBRARY, selected = twoRows)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun bookSheetLight() = phone("phone-light-library-sheet", ThemeMode.Light, Routes.LIBRARY, sheet = { dadPosition(it, "Kürk") }, dialogs = true)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun bookSheetDark() = phone("phone-dark-library-sheet", ThemeMode.Dark, Routes.LIBRARY, sheet = { dadPosition(it, "Kürk") }, dialogs = true)

    /** A short window (a phone with a tall navigation bar, or split screen): every action, Sil too, is on the sheet. */
    @Test @Config(qualifiers = "tr-w411dp-h640dp-hdpi")
    fun bookSheetShortLight() = phone("phone-light-library-sheet-short", ThemeMode.Light, Routes.LIBRARY, sheet = { dadPosition(it, "Kürk") })

    @Test @Config(qualifiers = "tr-w411dp-h640dp-hdpi")
    fun bookSheetShortDark() = phone("phone-dark-library-sheet-short", ThemeMode.Dark, Routes.LIBRARY, sheet = { dadPosition(it, "Kürk") })

    // ---------------------------------------------------------------- shelves

    /** "Yeni raf" from the FAB: the form sheet over the shelves (the cursor is left out: Robolectric would not go idle). */
    private fun newShelf(name: String, theme: ThemeMode) = phone(name, theme, Routes.SHELVES) {
        ShelfFormDialog(
            title = "Yeni raf",
            existing = listOf("B4", "C1"),
            onSave = { _, _ -> },
            onDismiss = {},
            focusOnOpen = false,
        )
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun newShelfLight() = newShelf("phone-light-shelf-new", ThemeMode.Light)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun newShelfDark() = newShelf("phone-dark-shelf-new", ThemeMode.Dark)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun shelvesLight() = phone("phone-light-shelves-list", ThemeMode.Light, Routes.SHELVES)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun shelvesDark() = phone("phone-dark-shelves-list", ThemeMode.Dark, Routes.SHELVES)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun openShelfLight() = phone("phone-light-shelf-open", ThemeMode.Light, Routes.SHELVES, shelf = ShelfTarget(Owner.ME, "3C"))

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun openShelfDark() = phone("phone-dark-shelf-open", ThemeMode.Dark, Routes.SHELVES, shelf = ShelfTarget(Owner.ME, "3C"))

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun addBooksLight() = phone("phone-light-shelf-adding", ThemeMode.Light, Routes.SHELVES, shelf = ShelfTarget(Owner.ME, "3C"), shelfQuery = "çatıdaki")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun addBooksDark() = phone("phone-dark-shelf-adding", ThemeMode.Dark, Routes.SHELVES, shelf = ShelfTarget(Owner.ME, "3C"), shelfQuery = "çatıdaki")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun pickerLight() = phone("phone-light-shelf-picker", ThemeMode.Light, Routes.LIBRARY, place = { Owner.DAD to listOf(kurk(it)) }, dialogs = true)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun pickerDark() = phone("phone-dark-shelf-picker", ThemeMode.Dark, Routes.LIBRARY, place = { Owner.DAD to listOf(kurk(it)) }, dialogs = true)
}
