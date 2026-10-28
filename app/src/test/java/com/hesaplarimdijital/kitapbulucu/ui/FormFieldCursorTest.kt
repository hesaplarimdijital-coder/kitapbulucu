package com.hesaplarimdijital.kitapbulucu.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A text set from outside (a chosen suggestion) puts the cursor at its end; typing goes on from there. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h640dp-hdpi")
class FormFieldCursorTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun typingAfterAnOutsideChangeContinuesAtTheEnd() {
        var text by mutableStateOf("Ab")
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, accent = AppPalette.Bordo, inputMode = InputMode.Touch, fonts = AppFonts.desk, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                FormField("Yazar", text, { text = it })
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { text = "Abcdef" }
        field.performTextInput("x")
        compose.runOnIdle { assertEquals("Abcdefx", text) }
    }

    @Test
    fun typingKeepsItsOwnCursor() {
        var text by mutableStateOf("")
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, accent = AppPalette.Bordo, inputMode = InputMode.Touch, fonts = AppFonts.desk, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                FormField("Yazar", text, { text = it })
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performSemanticsAction(SemanticsActions.RequestFocus)
        field.performTextInput("Orhan")
        field.performTextInput(" Pamuk")
        compose.runOnIdle { assertEquals("Orhan Pamuk", text) }
    }
}
