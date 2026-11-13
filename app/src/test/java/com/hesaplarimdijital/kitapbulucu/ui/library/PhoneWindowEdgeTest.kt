package com.hesaplarimdijital.kitapbulucu.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
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
import desktopkit.DesktopKitRoot
import desktopkit.settings.rememberDeskPreferences
import desktopkit.shell.ShellNavigator
import desktopkit.theme.GlassMode
import desktopkit.theme.LocalGlassMode
import desktopkit.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * S-42: on the phone (a Galaxy S24: 1080 × 2340, density 3, three-button navigation, dark theme)
 * the library list runs on under the glass bars, and nothing of it shows below them: the kit's
 * bottom glass reaches the window's bottom edge and the last pixel rows are the chrome, not the
 * list. The app is laid out as MainActivity lays it out (DesktopKitRoot around AppShell, edge to
 * edge), the glass blurs as on the phone and the window is drawn with the hardware renderer.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
class PhoneWindowEdgeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var view: View
    private lateinit var navigator: ShellNavigator

    /** Two made-up lists long enough to fill the window. */
    private fun store(): LibraryStore {
        val documents = FakeDocuments()
        val dadUri = Uri.parse("content://test/Babam.xlsx")
        val meUri = Uri.parse("content://test/Benim.xlsx")
        fun table(prefix: String) = BookTable((1..60).map { Book("$prefix Kitabı %02d".format(it), "YAZAR ADI SOYADI $it", "A" + (it % 5 + 1)) })
        documents.bytes[dadUri] = XlsxWriter.write(listOf(table("Deneme").toSheetSpec("Babam")))
        documents.bytes[meUri] = XlsxWriter.write(listOf(table("Örnek").toSheetSpec("Benim")))
        val links = FileLinks(context.getSharedPreferences("edge-links", Context.MODE_PRIVATE))
        links.set(Owner.DAD, dadUri, "Babam.xlsx")
        links.set(Owner.ME, meUri, "Benim.xlsx")
        val cache = LibraryCache(File(context.cacheDir, "edge-library"))
        return LibraryStore(links, documents, cache, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).also { it.start() }
    }

    private fun open(navigationPx: Int, cutoutPx: Int = 0) {
        val store = store()
        val settings = AppSettings(context.getSharedPreferences("edge-app", Context.MODE_PRIVATE)).also { it.updateMe(Owner.DAD) }
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            val prefs = rememberDeskPreferences(accents = AppPalette.accents, defaultTheme = ThemeMode.Dark)
            val navController = rememberNavController()
            navigator = ShellNavigator(navController)
            CompositionLocalProvider(LocalGlassMode provides GlassMode.Blur) {
                DesktopKitRoot(prefs, fonts = AppFonts.desk, colors = { AppPalette.warm(it, prefs.accent) }) { widthClass ->
                    AppShell(prefs, widthClass, navController, store, settings, rememberFilePicker(store))
                }
            }
        }
        compose.waitForIdle()
        // the shell's graph is set up inside the root's subcomposition: the library is opened once it is there
        compose.runOnUiThread { navigator.navigate(Routes.LIBRARY) }
        compose.waitForIdle()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(Type.statusBars(), Insets.of(0, STATUS_PX, 0, 0))
            .setVisible(Type.statusBars(), true)
            .setInsets(Type.navigationBars(), Insets.of(0, 0, 0, navigationPx))
            .setVisible(Type.navigationBars(), true)
            .setInsets(Type.tappableElement(), Insets.of(0, STATUS_PX, 0, navigationPx))
            .apply { if (cutoutPx > 0) setInsets(Type.displayCutout(), Insets.of(0, 0, 0, cutoutPx)) }
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    /** The app's window as the hardware renderer draws it. */
    private fun capture(): Bitmap {
        val window = compose.activity.window
        val decor = window.decorView
        val full = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { PixelCopy.request(window, full, {}, Handler(Looper.getMainLooper())) }
        val at = IntArray(2).also { view.getLocationInWindow(it) }
        return Bitmap.createBitmap(full, at[0], at[1], view.width, view.height)
    }

    private fun channels(argb: Int) = intArrayOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    /**
     * The band behind the system's navigation bar, [band] px down to the window's edge: no light
     * pixel of the list's text, and the last rows the colour of the band above them.
     */
    private fun assertBandIsChrome(bitmap: Bitmap, band: Int) {
        val top = bitmap.height - band + 6
        val light = (top until bitmap.height).sumOf { y -> (0 until bitmap.width).count { x -> channels(bitmap.getPixel(x, y)).max() > 150 } }
        assertTrue("$light light pixels of the list under the system's navigation bar", light == 0)
        val reference = bitmap.height - band / 2
        for (y in bitmap.height - 3 until bitmap.height) {
            val off = (0 until bitmap.width).count { x ->
                val a = channels(bitmap.getPixel(x, y))
                val b = channels(bitmap.getPixel(x, reference))
                (0..2).any { abs(a[it] - b[it]) > 40 }
            }
            assertTrue("row $y: $off pixels are not the band's colour", off == 0)
        }
    }

    private fun assertGlassReachesTheBottom() {
        val window = compose.onRoot().getBoundsInRoot()
        val glass = compose.onNodeWithTag("dk-bottom-glass", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the bottom glass ends at ${glass.bottom}, the window at ${window.bottom}", glass.bottom >= window.bottom - 0.5.dp)
    }

    @Test
    fun threeButtonNavigation() {
        open(navigationPx = 144)
        assertGlassReachesTheBottom()
        val bitmap = capture()
        assertBandIsChrome(bitmap, 144)
        // above the shell: the root's background under the status bar, no text of the list
        val light = (0 until STATUS_PX).sumOf { y -> (0 until bitmap.width).count { x -> channels(bitmap.getPixel(x, y)).max() > 150 } }
        assertTrue("$light light pixels under the status bar", light == 0)
    }

    @Test
    fun gestureNavigation() {
        open(navigationPx = 48)
        assertGlassReachesTheBottom()
        assertBandIsChrome(capture(), 48)
    }

    /** A cutout reported taller than the navigation bar: the chrome covers the taller of the two. */
    @Test
    fun bottomInsetsThatDisagree() {
        open(navigationPx = 144, cutoutPx = 150)
        assertGlassReachesTheBottom()
        assertBandIsChrome(capture(), 150)
    }

    private companion object {
        const val STATUS_PX = 117
    }
}
