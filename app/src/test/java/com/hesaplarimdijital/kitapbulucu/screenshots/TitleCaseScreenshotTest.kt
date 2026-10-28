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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
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
 * Settings, "Ad ve yazarları düzelt": the question with the count per list and examples, over
 * made-up lists typed in capitals (the father's) and mostly by hand (mine). This device is mine,
 * so the question also says the father's list changes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleCaseScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun workbook(sheet: String, books: List<Book>): ByteArray = XlsxWriter.write(listOf(BookTable(books).toSheetSpec(sheet)))

    private fun sampleStore(): LibraryStore {
        val documents = FakeDocuments()
        val dad = Uri.parse("content://test/Babam.xlsx")
        val me = Uri.parse("content://test/Benim.xlsx")
        documents.bytes[dad] = workbook(
            "Babam",
            listOf(
                Book("ÇATIDAKİ RASATHANE", "AYŞE İNCE", "C2"),
                Book("İSTANBUL'UN SAATLERİ", "A.HAMDİ KORUR", "C2"),
                Book("SEFİL FENERCİLER VE DENİZ", "HALİT ORUÇ", "A1", 2),
                Book("XIX. YÜZYIL MEKTUPLARI", "LEYLA UMUT", "A3"),
                Book("Mavi Defter", "Nur Akın", "4D"),
            ),
        )
        documents.bytes[me] = workbook("Benim", listOf(Book("KAR VE KÜL", "ORHAN PAMİR", "1B"), Book("Çatıdakiler", "Leyla Umut")))
        val links = FileLinks(context.getSharedPreferences("title-case-screenshot-links", Context.MODE_PRIVATE))
        links.set(Owner.DAD, dad, "Babam.xlsx")
        links.set(Owner.ME, me, "Benim.xlsx")
        val cache = LibraryCache(File(context.cacheDir, "title-case-screenshot-library"))
        return LibraryStore(links, documents, cache, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).also { it.start() }
    }

    private fun capture(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode) {
        val store = sampleStore()
        // the Settings screen reads whose device this is from the app's own preferences
        val settings = AppSettings.of(context).also { it.updateMe(Owner.ME) }
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, accent = AppPalette.Bordo, inputMode = input, fonts = AppFonts.desk, type = AppFonts.type, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    val prefs = rememberDeskPreferences(accents = AppPalette.accents)
                    val navController = rememberNavController()
                    LaunchedEffect(Unit) { ShellNavigator(navController).navigate(Routes.SETTINGS) }
                    AppShell(prefs, widthClass, navController, store, settings, rememberFilePicker(store))
                }
            }
        }
        compose.waitForIdle()
        val button = "Düzelt…"
        // the row may be composed but below the fold (phone): scroll the list that holds it first
        val holder = hasScrollToNodeAction() and hasAnyDescendant(hasText(button))
        val list = if (compose.onAllNodes(holder).fetchSemanticsNodes().isNotEmpty()) compose.onAllNodes(holder).onFirst() else compose.onAllNodes(hasScrollToNodeAction()).onFirst()
        list.performScrollToNode(hasText(button))
        compose.waitForIdle()
        compose.onNodeWithText(button).performClick()
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            val canvas = Canvas(bitmap)
            window.draw(canvas)
            drawDialogs(canvas, window, bitmap.width, bitmap.height)
        }
        ShellScreenshotTest.save(name, bitmap)
    }

    /** A dialog is a window of its own: the other root views are drawn on top, centred, over a dimmed screen. */
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

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun dexLight() = capture("dex-light-title-case", WidthClass.ExtraLarge, ThemeMode.Light, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun dexDark() = capture("dex-dark-title-case", WidthClass.ExtraLarge, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneLight() = capture("phone-light-title-case", WidthClass.Compact, ThemeMode.Light, InputMode.Touch)
}
