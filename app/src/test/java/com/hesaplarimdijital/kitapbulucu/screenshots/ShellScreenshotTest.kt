package com.hesaplarimdijital.kitapbulucu.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.ui.AppShell
import com.hesaplarimdijital.kitapbulucu.ui.Routes
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
import desktopkit.settings.rememberDeskPreferences
import desktopkit.shell.ShellNavigator
import desktopkit.shell.WidthClass
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * The app's shell on a phone (portrait) and on DeX, light and dark, in Turkish. The PNGs land in
 * build/outputs/screenshots; CI compares them with src/test/screenshots and pushes them to the
 * ci/screenshots branch.
 *
 * The window is drawn into a bitmap directly: captureToImage() waits for a frame callback that
 * Robolectric's paused main looper never delivers.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode, route: String = Routes.SEARCH) {
        // the test activity's theme has an action bar; the app's theme has none
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, accent = AppPalette.Bordo, inputMode = input, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    val prefs = rememberDeskPreferences(accents = AppPalette.accents)
                    val navController = rememberNavController()
                    LaunchedEffect(route) {
                        if (route != Routes.SEARCH) ShellNavigator(navController).navigate(route)
                    }
                    AppShell(prefs, widthClass, navController)
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneLight() = capture("phone-light", WidthClass.Compact, ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneDark() = capture("phone-dark", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun dexLight() = capture("dex-light", WidthClass.ExtraLarge, ThemeMode.Light, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun dexDark() = capture("dex-dark", WidthClass.ExtraLarge, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun dexSettingsLight() = capture("dex-light-settings", WidthClass.ExtraLarge, ThemeMode.Light, InputMode.Pointer, Routes.SETTINGS)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun dexTrialLight() = capture("dex-light-trial", WidthClass.ExtraLarge, ThemeMode.Light, InputMode.Pointer, Routes.TRIAL)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneTrialDark() = capture("phone-dark-trial", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch, Routes.TRIAL)

    companion object {
        private val dir = File(System.getProperty("kitapbulucu.screenshotDir") ?: "build/outputs/screenshots")

        fun save(name: String, bitmap: Bitmap) {
            dir.mkdirs()
            FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
