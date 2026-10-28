package com.hesaplarimdijital.kitapbulucu.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.ui.add.AddBookScreen
import com.hesaplarimdijital.kitapbulucu.ui.add.SuggestFixtures
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The add form with a suggestion list open: DeX (floating list) and phone (list under the field). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SuggestScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, openMore: Boolean, tag: String, typed: String, waitFor: String) {
        val store = SuggestFixtures.store(context, "suggest-shot")
        val settings = SuggestFixtures.settings(context, "suggest-shot")
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, accent = AppPalette.Bordo, inputMode = input, fonts = AppFonts.desk, type = AppFonts.type, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) { AddBookScreen(store, settings) }
            }
        }
        compose.waitForIdle()
        if (openMore) compose.onNodeWithText(context.getString(R.string.add_more)).performClick()
        val field = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))
        field.performClick()
        field.performTextInput(typed)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(waitFor).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            val canvas = Canvas(bitmap)
            window.draw(canvas)
            drawPopups(canvas, window)
        }
        ShellScreenshotTest.save(name, bitmap)
    }

    /** A popup is a window of its own, which the activity's view does not draw: draw it where it was placed. */
    private fun drawPopups(canvas: Canvas, activityRoot: View) {
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
            val params = root.layoutParams as? WindowManager.LayoutParams
            canvas.save()
            canvas.translate((params?.x ?: 0).toFloat(), (params?.y ?: 0).toFloat())
            root.draw(canvas)
            canvas.restore()
        }
    }

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun dexLightPublisher() =
        capture("dex-light-suggest-publisher", ThemeMode.Light, InputMode.Pointer, true, "field-publisher", "iş", "Türkiye İş Bankası Yayınları")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneDarkAuthor() =
        capture("phone-dark-suggest-author", ThemeMode.Dark, InputMode.Touch, false, "field-author", "ay", "Ayşe İnce")
}
