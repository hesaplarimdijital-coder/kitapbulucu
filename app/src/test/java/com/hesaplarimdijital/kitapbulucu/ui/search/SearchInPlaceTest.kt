package com.hesaplarimdijital.kitapbulucu.ui.search

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.FakeDocuments
import com.hesaplarimdijital.kitapbulucu.store.FileLinks
import com.hesaplarimdijital.kitapbulucu.store.LibraryCache
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppPalette
import com.hesaplarimdijital.kitapbulucu.xlsx.XlsxWriter
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** On DeX a clicked result opens where it is: the row neither moves to the top nor jumps. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class SearchInPlaceTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Thirty made-up books that all match "defter". */
    private fun store(): LibraryStore {
        val books = (1..30).map { Book("Defter %02d".format(it), "Deniz Yalın", "A" + (it % 5 + 1), year = "20" + (10 + it)) }
        val documents = FakeDocuments()
        val me = Uri.parse("content://test/Benim.xlsx")
        documents.bytes[me] = XlsxWriter.write(listOf(BookTable(books).toSheetSpec("Benim")))
        val links = FileLinks(context.getSharedPreferences("in-place-links", Context.MODE_PRIVATE))
        links.set(Owner.ME, me, "Benim.xlsx")
        val cache = LibraryCache(File(context.cacheDir, "in-place-library"))
        return LibraryStore(links, documents, cache, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).also { it.start() }
    }

    @Test
    fun aClickedRowOpensInPlace() {
        val store = store()
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, accent = AppPalette.Bordo, inputMode = InputMode.Pointer, fonts = AppFonts.desk, type = AppFonts.type, colors = { AppPalette.warm(it, AppPalette.Bordo) }) {
                SearchScreen(store, initialQuery = "defter")
            }
        }
        compose.waitUntil(5_000) { store.index.value != null }
        compose.waitForIdle()
        val rows = compose.onAllNodes(hasText("Defter ", substring = true) and hasClickAction()).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
        assertTrue("expected a screenful of results, got ${rows.size}", rows.size >= 6)
        val target = rows[4]
        val title = target.config[SemanticsProperties.Text].first().text
        // a SemanticsNode reads the live layout: keep the numbers from before the click
        val before = target.boundsInRoot.top
        val heightBefore = target.boundsInRoot.height
        val firstBefore = rows[0].boundsInRoot.top

        compose.onNode(hasText(title) and hasClickAction()).performClick()
        compose.waitForIdle()

        // the details (the year is among them) are shown in the row itself
        compose.onNodeWithText("Yıl", useUnmergedTree = true).assertExists()
        val after = compose.onNode(hasText(title) and hasClickAction()).fetchSemanticsNode()
        assertEquals("the opened row stays where it was", before, after.boundsInRoot.top, 1f)
        // the clickable header keeps its size; the details open right under it, not at the top of the list
        val details = compose.onNodeWithText("Yıl", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertTrue("the details open under the row: row $before, details $details", details > before && details < before + 600f)
        val firstAfter = compose.onNode(hasText(rows[0].config[SemanticsProperties.Text].first().text) and hasClickAction()).fetchSemanticsNode()
        assertEquals("the rows above did not move", firstBefore, firstAfter.boundsInRoot.top, 1f)
    }
}
