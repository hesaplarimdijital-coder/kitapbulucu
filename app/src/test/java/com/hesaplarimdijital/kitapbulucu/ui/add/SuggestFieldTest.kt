package com.hesaplarimdijital.kitapbulucu.ui.add

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Typing in Yazar and Yayınevi offers what the lists already hold; choosing fills the field. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SuggestFieldTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val bank = "Türkiye İş Bankası Yayınları"

    private fun show(input: InputMode) {
        val store = SuggestFixtures.store(context, "suggest-ui")
        val settings = SuggestFixtures.settings(context, "suggest-ui")
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, accent = AppPalette.Bordo, inputMode = input, fonts = AppFonts.desk, type = AppFonts.type, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) { AddBookScreen(store, settings) }
            }
        }
        compose.waitForIdle()
    }

    private fun field(tag: String): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    private fun typed(tag: String): String = field(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun shown(text: String): Boolean = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { shown(text) }

    private fun openMore() = compose.onNodeWithText(context.getString(com.hesaplarimdijital.kitapbulucu.R.string.add_more)).performClick()

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun choosingASuggestionFillsThePublisher() {
        show(InputMode.Pointer)
        openMore()
        field("field-publisher").performClick()
        field("field-publisher").performTextInput("iş")
        waitFor(bank)
        compose.onNodeWithText(bank).performClick()
        compose.waitForIdle()
        assertEquals(bank, typed("field-publisher"))
        // the list is closed once the whole value is there
        assertEquals(false, shown("İş Matbaası"))
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun keyboardMovesAndAccepts() {
        show(InputMode.Pointer)
        openMore()
        field("field-publisher").performClick()
        field("field-publisher").performTextInput("iş")
        waitFor(bank)
        // nothing marked: free typing is untouched
        assertEquals("iş", typed("field-publisher"))
        // values that begin with the typed letters come first, the most used of them on top
        field("field-publisher").performKeyInput { pressKey(Key.DirectionDown) }
        field("field-publisher").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals("İş Matbaası", typed("field-publisher"))
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun escapeClosesAndTypingOpensAgain() {
        show(InputMode.Pointer)
        openMore()
        field("field-publisher").performClick()
        field("field-publisher").performTextInput("iş")
        waitFor(bank)
        field("field-publisher").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertEquals(false, shown(bank))
        assertEquals("iş", typed("field-publisher"))
        field("field-publisher").performTextInput(" b")
        waitFor(bank)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun tabAcceptsTheMarkedSuggestion() {
        show(InputMode.Pointer)
        field("field-author").performClick()
        field("field-author").performTextInput("ay")
        waitFor("Ayşe İnce")
        field("field-author").performKeyInput { pressKey(Key.DirectionDown) }
        field("field-author").performKeyInput { pressKey(Key.Tab) }
        compose.waitForIdle()
        // Ayşe İnce is in both lists, so she is the most used of the "ay" authors
        assertEquals("Ayşe İnce", typed("field-author"))
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun freeTypingNeverChanges() {
        show(InputMode.Pointer)
        openMore()
        field("field-publisher").performClick()
        field("field-publisher").performTextInput("Yeni Yayınevi")
        compose.waitForIdle()
        assertEquals("Yeni Yayınevi", typed("field-publisher"))
        assertEquals(false, shown(bank))
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneListSitsUnderTheField() {
        show(InputMode.Touch)
        field("field-author").performClick()
        field("field-author").performTextInput("ay")
        waitFor("Ayşe İnce")
        compose.onNodeWithText("Ayhan Ergin").performClick()
        compose.waitForIdle()
        assertEquals("Ayhan Ergin", typed("field-author"))
    }
}
