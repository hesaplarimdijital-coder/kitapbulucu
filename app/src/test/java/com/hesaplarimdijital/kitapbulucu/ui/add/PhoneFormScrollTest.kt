package com.hesaplarimdijital.kitapbulucu.ui.add

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.WindowWidth
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.ui.AppShell
import com.hesaplarimdijital.kitapbulucu.ui.FormRequest
import com.hesaplarimdijital.kitapbulucu.ui.rememberFilePicker
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
import desktopkit.settings.rememberDeskPreferences
import desktopkit.shell.WidthClass
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * S-41: the phone's edit form, its title holding the cursor and the on-screen keyboard closed (as
 * on the S24 recording), scrolls and stays where the finger leaves it while the shell's bars would
 * slide; and while the form is open the shell's bottom navigation is not shown under its own
 * "Vazgeç / Kaydet" bar, so the form keeps one height and ends right above the system bar.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class PhoneFormScrollTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val book = Book("Rüzgârlı Kasaba", "Selim Toprak", "4D", publisher = "Can Yayınları", year = "1987", isbn = "9789750000000", note = "Deneme")

    private fun open() {
        val store = SuggestFixtures.store(context, "phone-form-scroll")
        val settings = SuggestFixtures.settings(context, "phone-form-scroll")
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, accent = AppPalette.Bordo, inputMode = InputMode.Touch, fonts = AppFonts.desk, colors = { AppPalette.warm(it, AppPalette.Bordo) }) { WindowWidth {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    val prefs = rememberDeskPreferences(accents = AppPalette.accents)
                    AppShell(
                        prefs, WidthClass.Compact, rememberNavController(), store, settings, rememberFilePicker(store),
                        initialForm = FormRequest.Edit(Owner.ME, book),
                    )
                }
            } }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun title(): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasText(book.title))

    private fun titleTop(): Dp = title().getBoundsInRoot().top

    /** The form's scrolling column: the tallest scrollable node on the screen. */
    private fun form(): SemanticsNodeInteraction {
        val nodes = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        val tallest = nodes.indices.maxBy { nodes[it].boundsInRoot.height }
        return compose.onAllNodes(hasScrollAction())[tallest]
    }

    /** Lets every animation, settle and scroll request run out. */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    private fun near(expected: Dp, actual: Dp, what: String) =
        assertTrue("$what: expected $expected, was $actual", abs((expected - actual).value) < 1f)

    @Test
    fun theFormStaysWhereTheFingerLeftIt() {
        open()
        // as on the recording: the title has the cursor, the keyboard is closed (Robolectric has none)
        title().assertIsFocused()
        val start = titleTop()

        // a slow drag up (no fling): the form scrolls, the title goes up out of the way
        form().performTouchInput { swipe(Offset(centerX, bottom - 40f), Offset(centerX, top + height * 0.35f), durationMillis = 800) }
        compose.waitForIdle()
        val up = titleTop()
        assertTrue("the form scrolled: title $start → $up", up < start - 150.dp)
        settle()
        near(up, titleTop(), "the form stays after the drag up")
        title().assertIsFocused()

        // a short drag back down (on the phone this brings the shell's bars back): no jump to the title
        form().performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY + 120f), durationMillis = 600) }
        compose.waitForIdle()
        val down = titleTop()
        assertTrue("the form moved back a little: $up → $down", down > up)
        assertTrue("the form did not jump back to the title: start $start, now $down", down < start - 60.dp)
        settle()
        near(down, titleTop(), "the form stays after the drag down")
    }

    @Test
    fun theBottomNavigationIsNotShownUnderThePhoneForm() {
        open()
        val library = context.getString(R.string.nav_library)
        assertEquals("the bottom navigation is hidden", 0, compose.onAllNodesWithText(library).fetchSemanticsNodes().size)
        // the form's own bar ends at the window's bottom (no system bar in Robolectric), no band under it
        val root = compose.onRoot().getBoundsInRoot()
        val save = compose.onNodeWithText(context.getString(R.string.add_save)).getBoundsInRoot()
        assertTrue("Kaydet near the bottom: ${save.bottom} of ${root.bottom}", root.bottom - save.bottom < 40.dp)

        // scrolling does not bring it in
        form().performTouchInput { swipe(Offset(centerX, bottom - 40f), Offset(centerX, top + height * 0.35f), durationMillis = 800) }
        settle()
        form().performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY + 200f), durationMillis = 600) }
        settle()
        assertEquals("still hidden after scrolling", 0, compose.onAllNodesWithText(library).fetchSemanticsNodes().size)

        // closing the form brings it back
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        settle()
        assertTrue("the bottom navigation is back", compose.onAllNodesWithText(library).fetchSemanticsNodes().isNotEmpty())
    }
}
