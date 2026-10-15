package com.hesaplarimdijital.kitapbulucu.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
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
            DesktopKitTheme(themeMode = ThemeMode.Light, accent = AppPalette.Bordo, inputMode = InputMode.Touch, fonts = AppFonts.desk, type = AppFonts.type, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
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
            DesktopKitTheme(themeMode = ThemeMode.Light, accent = AppPalette.Bordo, inputMode = InputMode.Touch, fonts = AppFonts.desk, type = AppFonts.type, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
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
}
