package com.hesaplarimdijital.kitapbulucu.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
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

/** Phone sheets are drawn in the app's own window (which is padded for the navigation bar and the keyboard). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h640dp-hdpi")
class SheetHostTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aSheetIsDrawnInTheAppWindowNotADialog() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, accent = AppPalette.Bordo, inputMode = InputMode.Touch, fonts = AppFonts.desk, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                SheetHost {
                    Text("Liste")
                    AppSheet(onDismiss = {}, title = "Kitap") { _ -> Text("Sil") }
                }
            }
        }
        compose.onNodeWithText("Sil").assertIsDisplayed()
        assertEquals("one window: no dialog window of its own", 1, compose.onAllNodes(isRoot()).fetchSemanticsNodes().size)
    }

    @Test
    fun anActionRunsAfterTheSheetHasClosed() {
        var dismissed = false
        var ran = false
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, accent = AppPalette.Bordo, inputMode = InputMode.Touch, fonts = AppFonts.desk, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                var open by remember { mutableStateOf(true) }
                SheetHost {
                    if (open) {
                        AppSheet(onDismiss = { open = false; dismissed = true }) { close ->
                            Text("Sil", Modifier.clickable { close { ran = dismissed } })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Sil").performClick()
        compose.waitForIdle()
        assertTrue("dismissed, then the action ran", dismissed && ran)
        compose.onNodeWithText("Sil").assertDoesNotExist()
    }

    /** A sheet with [body] in a host; [onDismissed] runs once it has closed. */
    private fun openSheet(onDismissed: () -> Unit, body: @Composable () -> Unit) {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, accent = AppPalette.Bordo, inputMode = InputMode.Touch, fonts = AppFonts.desk, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                var open by remember { mutableStateOf(true) }
                SheetHost {
                    Text("Liste")
                    if (open) {
                        AppSheet(onDismiss = { open = false; onDismissed() }, title = "Kitap") { _ -> body() }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun draggingTheSheetDownClosesIt() {
        var dismissed = false
        openSheet({ dismissed = true }) { Text("Sil", Modifier.height(240.dp)) }
        compose.onNodeWithText("Sil").performTouchInput { swipe(center, center + Offset(0f, 900f), durationMillis = 150) }
        compose.waitForIdle()
        assertTrue("a quick pull down closes the sheet", dismissed)
        compose.onNodeWithText("Sil").assertDoesNotExist()
    }

    @Test
    fun aShortSlowDragSpringsBack() {
        var dismissed = false
        openSheet({ dismissed = true }) { Text("Sil", Modifier.height(400.dp)) }
        val before = compose.onNodeWithText("Sil").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Sil").performTouchInput { swipe(center, center + Offset(0f, 60f), durationMillis = 1200) }
        compose.waitForIdle()
        assertFalse("a short slow pull does not close the sheet", dismissed)
        val after = compose.onNodeWithText("Sil").fetchSemanticsNode().boundsInRoot.top
        assertEquals("the sheet is back in its place", before, after, 1f)
    }

    @Test
    fun scrollingContentAtItsTopHandsTheDragToTheSheet() {
        var dismissed = false
        openSheet({ dismissed = true }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Sil")
                Spacer(Modifier.height(2000.dp))
            }
        }
        compose.onNodeWithText("Sil").performTouchInput { swipe(center, center + Offset(0f, 900f), durationMillis = 150) }
        compose.waitForIdle()
        assertTrue("pulled down at the top of its content, the sheet closes", dismissed)
    }

    @Test
    fun scrolledContentScrollsBackBeforeTheSheetMoves() {
        var dismissed = false
        openSheet({ dismissed = true }) {
            val scroll = rememberScrollState(initial = 3000)
            Column(Modifier.verticalScroll(scroll)) {
                Spacer(Modifier.height(1000.dp))
                Text("Sil")
                Spacer(Modifier.height(1000.dp))
            }
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipe(center, center + Offset(0f, 300f), durationMillis = 400) }
        compose.waitForIdle()
        assertFalse("the content scrolled; the sheet stayed open", dismissed)
    }
}
