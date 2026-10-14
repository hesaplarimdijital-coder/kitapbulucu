package com.hesaplarimdijital.kitapbulucu.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookColumn
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.ui.BookActions
import com.hesaplarimdijital.kitapbulucu.ui.LibraryTexts
import com.hesaplarimdijital.kitapbulucu.ui.AppIcons
import androidx.compose.runtime.LaunchedEffect
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import androidx.compose.ui.text.font.FontWeight
import desktopkit.components.ColumnWidth
import desktopkit.components.DenseTable
import desktopkit.components.DeskButton
import desktopkit.components.DeskIcons
import desktopkit.components.MenuEntry
import desktopkit.components.SegmentedChoice
import desktopkit.components.TableColumn
import desktopkit.components.TableSort
import desktopkit.screens.PageHeader
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode
import java.text.Collator
import java.util.Locale

/** One row of a library table: the book and where it sits in the file, for a stable key. */
class LibraryRow(val owner: Owner, val position: Int, val book: Book)

/**
 * Every book of one list in a dense table (S-20): Babam / Benim tabs, sortable columns in Turkish
 * order, a context menu per row (edit, delete, move, put on a shelf, other books on the shelf,
 * copy), F2 and Del
 * on the selected row, and the + button for a new book.
 */
@Composable
fun LibraryScreen(
    store: LibraryStore,
    settings: AppSettings?,
    actions: BookActions,
    title: String,
    onAdd: () -> Unit,
    onShelf: (Owner, String) -> Unit,
) {
    val states by store.states.collectAsState()
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    var owner by rememberSaveable { mutableStateOf(settings?.me ?: Owner.DAD) }
    val state = states.getValue(owner)
    val now = System.currentTimeMillis()

    Column(Modifier.fillMaxSize()) {
        PageHeader(title, subtitle = LibraryTexts.updated(resources, owner, state, now) + " · " + LibraryTexts.status(resources, state))
        Row(Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding).padding(bottom = dimens.gapS), verticalAlignment = Alignment.CenterVertically) {
            SegmentedChoice(
                options = Owner.entries.map { it to LibraryTexts.ownerLabel(resources, it) },
                selected = owner,
                onSelect = { owner = it },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            DeskButton(stringResource(R.string.library_add), onAdd, icon = DeskIcons.Plus, primary = true)
        }
        val books = state.table?.books
        when {
            !state.isLinked -> Message(stringResource(R.string.library_not_linked))
            books == null -> Message(LibraryTexts.status(resources, state))
            books.isEmpty() -> Message(stringResource(R.string.library_empty))
            else -> {
                val rows = remember(books, owner) { books.mapIndexed { index, book -> LibraryRow(owner, index, book) } }
                BookTable(rows, actions, onShelf, Modifier.weight(1f))
                if (DeskTheme.inputMode == InputMode.Pointer) {
                    Text(
                        stringResource(R.string.library_hint),
                        style = DeskTheme.type.labelSmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapS),
                    )
                }
            }
        }
    }
}

/**
 * The table itself, shared with the Shelves screen: sorting, selection, the row menu and the
 * F2 / Del keys while a row has the focus.
 */
@Composable
fun BookTable(rows: List<LibraryRow>, actions: BookActions, onShelf: ((Owner, String) -> Unit)?, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val clipboard = LocalClipboardManager.current
    var sort by remember { mutableStateOf<TableSort?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }
    // several rows (Ctrl/Shift click, Ctrl+A; on touch the "Seç" mode) for "Rafa koy…"
    var selection by remember { mutableStateOf<Set<Any>>(emptySet()) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(rows) {
        selection = emptySet()
        selected = null
    }
    val touch = DeskTheme.inputMode == InputMode.Touch
    val collator = remember { Collator.getInstance(Locale("tr", "TR")) }
    val columns = rememberColumns()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < 640.dp
        val shown = if (narrow) listOf(columns[0], columns[2]) else columns
        val sorted = remember(rows, sort, narrow) {
            val current = sort ?: return@remember rows
            val column = shown.getOrNull(current.column) ?: return@remember rows
            val comparator: Comparator<LibraryRow> = when (column.title) {
                BookColumn.COPIES.header -> compareBy { it.book.copies }
                else -> Comparator { a, b -> collator.compare(column.value(a), column.value(b)) }
            }
            if (current.ascending) rows.sortedWith(comparator) else rows.sortedWith(comparator.reversed())
        }
        val selectedRow = sorted.firstOrNull { it.position == selected }
        val chosen = sorted.filter { rowKey(it) in selection }
        Column(Modifier.fillMaxSize()) {
        if (chosen.size > 1 || touch) {
            SelectionBar(
                count = chosen.size,
                picking = picking,
                showPick = touch,
                onPick = {
                    picking = !picking
                    if (!picking) selection = emptySet()
                },
                onPlace = { chosen.groupBy { it.owner }.forEach { (owner, list) -> actions.placeOnShelf(owner, list.map { it.book }) } },
                onClear = {
                    selection = emptySet()
                    picking = false
                },
            )
        }
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || selectedRow == null) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.F2 -> { actions.edit(selectedRow.owner, selectedRow.book); true }
                    Key.Delete, Key.Backspace -> { actions.delete(selectedRow.owner, selectedRow.book); true }
                    else -> false
                }
            },
        ) {
            DenseTable(
                columns = shown,
                rows = sorted,
                key = ::rowKey,
                onRowClick = { row, _ -> selected = row.position },
                selectedKeys = selection,
                onSelectionChange = { keys ->
                    selection = keys
                    if (keys.size == 1) selected = sorted.firstOrNull { rowKey(it) in keys }?.position
                },
                toggleOnClick = picking,
                sort = sort,
                onSortChange = { sort = it },
                rowMenu = { row, _ ->
                    selected = row.position
                    // the kit keeps the selection when the menu opens on a selected row
                    val many = sorted.filter { rowKey(it) in selection }.takeIf { rowKey(row) in selection && it.size > 1 }
                    listOfNotNull(
                        MenuEntry.Item(resources.getString(R.string.action_edit), { actions.edit(row.owner, row.book) }, shortcut = "F2"),
                        MenuEntry.Item(resources.getString(R.string.action_delete), { actions.delete(row.owner, row.book) }, shortcut = "Del", destructive = true),
                        MenuEntry.Item(resources.getString(R.string.action_move), { actions.move(row.owner, row.book) }),
                        if (many != null) {
                            MenuEntry.Item(resources.getString(R.string.action_place_many, many.size), { many.groupBy { it.owner }.forEach { (owner, list) -> actions.placeOnShelf(owner, list.map { it.book }) } })
                        } else {
                            MenuEntry.Item(resources.getString(R.string.action_place), { actions.placeOnShelf(row.owner, listOf(row.book)) })
                        },
                        if (onShelf != null && row.book.shelf.isNotBlank()) {
                            MenuEntry.Item(resources.getString(R.string.action_shelf_books), { onShelf(row.owner, row.book.shelf) })
                        } else {
                            null
                        },
                        MenuEntry.Separator,
                        MenuEntry.Item(resources.getString(R.string.action_copy), { clipboard.setText(AnnotatedString(copyText(resources, row))) }),
                    )
                },
            )
        }
        }
    }
}

private fun rowKey(row: LibraryRow): Any = "${row.owner.name}:${row.position}"

/** "3 kitap seçili · Rafa koy… · Seçimi kaldır"; on touch also the "Seç" switch that makes taps tick rows. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SelectionBar(count: Int, picking: Boolean, showPick: Boolean, onPick: () -> Unit, onPlace: () -> Unit, onClear: () -> Unit) {
    val dimens = DeskTheme.dimens
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dimens.gapS),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dimens.gapXs),
    ) {
        if (showPick) DeskButton(stringResource(if (picking) R.string.select_done else R.string.select_start), onPick, icon = DeskIcons.Check, primary = picking)
        if (count > 0) {
            Text(
                stringResource(R.string.select_count, count),
                style = DeskTheme.type.body,
                color = DeskTheme.colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            DeskButton(stringResource(R.string.action_place), onPlace, icon = AppIcons.Shelf, primary = !showPick || !picking)
            DeskButton(stringResource(R.string.select_clear), onClear)
        }
    }
}

@Composable
private fun rememberColumns(): List<TableColumn<LibraryRow>> {
    val yes = stringResource(R.string.add_yes)
    // book titles in Literata, like the search results (Draft A)
    val titleStyle = DeskTheme.type.bodyStrong.copy(fontFamily = AppFonts.Literata, fontWeight = FontWeight.Bold)
    return remember(yes, titleStyle) {
        listOf(
            TableColumn(BookColumn.TITLE.header, ColumnWidth.Weight(3f), textStyle = titleStyle) { it.book.title },
            TableColumn(BookColumn.AUTHOR.header, ColumnWidth.Weight(2f)) { it.book.author },
            TableColumn(BookColumn.SHELF.header, ColumnWidth.Fixed(84.dp), mono = true) { it.book.shelf },
            TableColumn(BookColumn.COPIES.header, ColumnWidth.Fixed(64.dp), alignEnd = true) { it.book.copies.toString() },
            TableColumn(BookColumn.SIGNED.header, ColumnWidth.Fixed(72.dp)) { if (it.book.signed) yes else "" },
            TableColumn(BookColumn.PUBLISHER.header, ColumnWidth.Weight(1.5f)) { it.book.publisher },
            TableColumn(BookColumn.YEAR.header, ColumnWidth.Fixed(64.dp)) { it.book.year },
        )
    }
}

private fun copyText(resources: android.content.res.Resources, row: LibraryRow): String {
    val book = row.book
    val shelf = book.shelf.ifBlank { resources.getString(R.string.search_no_shelf) }
    return listOf(book.title, book.author, LibraryTexts.ownerLabel(resources, row.owner) + " · " + resources.getString(R.string.search_shelf) + " " + shelf)
        .filter { it.isNotBlank() }
        .joinToString(" — ")
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = DeskTheme.type.body,
        color = DeskTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = DeskTheme.dimens.pagePadding, vertical = DeskTheme.dimens.gapL),
    )
}
