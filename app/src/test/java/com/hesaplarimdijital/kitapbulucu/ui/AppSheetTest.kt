package com.hesaplarimdijital.kitapbulucu.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
import desktopkit.shell.DesktopShell
import desktopkit.shell.WidthClass
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

/** [AppSheet] on the kit's glass bottom sheet, inside the kit's shell at phone width. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h640dp-hdpi")
class AppSheetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** The shell with [content] on its first screen. */
    private fun inShell(content: @Composable () -> Unit) {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, accent = AppPalette.Bordo, inputMode = InputMode.Touch, fonts = AppFonts.desk, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                DesktopShell(spec = rememberAppSpec(), widthClass = WidthClass.Compact) { _, _ -> content() }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aSheetIsDrawnInTheShellWindowNotADialog() {
        inShell { AppSheet(onDismiss = {}, title = "Kitap") { _ -> Text("Sil") } }
        compose.onNodeWithText("Sil").assertIsDisplayed()
        assertEquals("one window: no dialog or pop-up window of its own", 1, compose.onAllNodes(isRoot()).fetchSemanticsNodes().size)
    }

    @Test
    fun anActionRunsAfterTheSheetHasClosed() {
        var dismissed = false
        var ran = false
        inShell {
            var open by remember { mutableStateOf(true) }
            if (open) {
                AppSheet(onDismiss = { open = false; dismissed = true }) { close ->
                    Text("Sil", Modifier.clickable { close { ran = dismissed } })
                }
            }
        }
        compose.onNodeWithText("Sil").performClick()
        compose.waitForIdle()
        assertTrue("dismissed, then the action ran", dismissed && ran)
        compose.onNodeWithText("Sil").assertDoesNotExist()
    }

    @Test
    fun pullingTheSheetDownClosesIt() {
        var dismissed = false
        inShell {
            var open by remember { mutableStateOf(true) }
            if (open) AppSheet(onDismiss = { open = false; dismissed = true }, title = "Kitap") { _ -> Text("Sil", Modifier.height(240.dp)) }
        }
        compose.onNodeWithText("Sil").performTouchInput { swipe(center, center + Offset(0f, 900f), durationMillis = 150) }
        compose.waitForIdle()
        assertTrue("a quick pull down closes the sheet", dismissed)
        compose.onNodeWithText("Sil").assertDoesNotExist()
    }
}
