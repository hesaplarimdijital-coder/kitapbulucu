package com.hesaplarimdijital.kitapbulucu.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookColumn
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.model.Shelves
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.LibraryState
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.text.TurkishText
import com.hesaplarimdijital.kitapbulucu.ui.AppIcons
import com.hesaplarimdijital.kitapbulucu.ui.AppSheet
import com.hesaplarimdijital.kitapbulucu.ui.BookActions
import com.hesaplarimdijital.kitapbulucu.ui.LibraryTexts
import com.hesaplarimdijital.kitapbulucu.ui.TouchIcons
import com.hesaplarimdijital.kitapbulucu.ui.TouchSelection
import com.hesaplarimdijital.kitapbulucu.ui.WideSegmentedChoice
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import desktopkit.components.ColumnWidth
import desktopkit.components.DenseTable
import desktopkit.components.DeskButton
import desktopkit.components.DeskIcons
import desktopkit.components.HorizontalRule
import desktopkit.components.MenuEntry
import desktopkit.components.SegmentedChoice
import desktopkit.components.TableColumn
import desktopkit.components.TableSort
import desktopkit.components.collectState
import desktopkit.components.focusRing
import desktopkit.screens.PageHeader
import desktopkit.shell.CompactTopBar
import desktopkit.shell.TopBarAction
import desktopkit.shell.TopBarBackIcon
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode
import desktopkit.theme.DeskMotion
import desktopkit.shell.WidthClass
import java.text.Collator
import java.util.Locale
import desktopkit.shell.currentWidthClass
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.itemsIndexed
import desktopkit.theme.LocalMotionTier
import desktopkit.shell.rememberShellContentPadding
import desktopkit.components.rememberListAppear
import desktopkit.components.PullToRefresh
import desktopkit.shell.ShellPrimaryAction
import desktopkit.components.listAppear
import desktopkit.components.animatedStateBackground
import desktopkit.components.DeskListRow
import desktopkit.components.DeskCheckbox
import desktopkit.components.MoreSheet
import desktopkit.components.ActionGroup

/** One row of a library table: the book and where it sits in the file, for a stable key. */
class LibraryRow(val owner: Owner, val position: Int, val book: Book)

/**
 * Every book of one list (S-20): MKE / Barış tabs and the + button for a new book.
 *
 * DeX and tablets: a dense table with sortable columns in Turkish order, a context menu per row
 * (edit, delete, move, put on a shelf, other books on the shelf, copy), F2 and Del on the
 * selected row. Phone: a two-line list, a tap opens the book's actions in a bottom sheet, a long
 * press starts selecting rows (the top bar turns into "N seçili"), "Kitap ekle" floats bottom right.
 */
@Composable
fun LibraryScreen(
    store: LibraryStore,
    settings: AppSettings?,
    actions: BookActions,
    title: String,
    onAdd: () -> Unit,
    onShelf: (Owner, String) -> Unit,
    /** For screenshots (phone): the rows, by position in the list, that start ticked. */
    previewSelected: List<Int> = emptyList(),
    /** For screenshots (phone): the row, by position in the list, whose action sheet starts open. */
    previewSheet: Int? = null,
) {
    val states by store.states.collectAsState()
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    var owner by rememberSaveable { mutableStateOf(settings?.me ?: Owner.DAD) }
    val state = states.getValue(owner)
    val now = System.currentTimeMillis()

    if (currentWidthClass() == WidthClass.Compact) {
        val refreshing by store.userRefreshing.collectAsState()
        PhoneLibrary(state, owner, { owner = it }, actions, title, onAdd, onShelf, previewSelected, previewSheet, refreshing, store::refresh)
        return
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(title, subtitle = LibraryTexts.updated(resources, owner, state, now) + " · " + LibraryTexts.status(resources, state))
        Row(Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding).padding(bottom = dimens.gapS), verticalAlignment = Alignment.CenterVertically) {
            SegmentedChoice(
                options = Owner.entries.map { it to LibraryTexts.ownerLabel(resources, it) },
                selected = owner,
                onSelect = { owner = it },
            )
            Spacer(Modifier.weight(1f))
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

// ------------------------------------------------------------------------------------ phone

/** Orders of the phone list; the table on DeX sorts by its column headers instead. */
enum class PhoneSort { TITLE, AUTHOR, SHELF, FILE }

/**
 * [rows] in the [sort] order, Turkish alphabetical (Ç after C, I before İ). Books without an
 * author or a shelf come last in those orders; equal keys keep the order of the file.
 */
internal fun sortRows(rows: List<LibraryRow>, sort: PhoneSort, collator: Collator = turkishCollator()): List<LibraryRow> {
    if (sort == PhoneSort.FILE) return rows
    val titles = rows.associateWith { collator.getCollationKey(it.book.title.trim()) }
    val byTitle = Comparator<LibraryRow> { a, b -> titles.getValue(a).compareTo(titles.getValue(b)) }.thenBy { it.position }
    return when (sort) {
        PhoneSort.TITLE -> rows.sortedWith(byTitle)
        PhoneSort.AUTHOR -> {
            val authors = rows.associateWith { collator.getCollationKey(it.book.author.trim()) }
            rows.sortedWith(
                compareBy<LibraryRow> { it.book.author.isBlank() }
                    .then(Comparator { a, b -> authors.getValue(a).compareTo(authors.getValue(b)) })
                    .then(byTitle),
            )
        }
        PhoneSort.SHELF -> {
            // a list has a few hundred shelves at most: order those once, then rank the books by them
            val keys = rows.associateWith { Shelves.key(it.book.shelf) }
            val rank = keys.values.distinct().sortedWith(Shelves.NaturalOrder).withIndex().associate { it.value to it.index }
            rows.sortedWith(
                compareBy<LibraryRow> { it.book.shelf.isBlank() }
                    .thenBy { rank.getValue(keys.getValue(it)) }
                    .then(byTitle),
            )
        }
        PhoneSort.FILE -> rows
    }
}

internal fun turkishCollator(): Collator = Collator.getInstance(Locale("tr", "TR"))

/** What a phone list of books keeps: the ticked rows and the row whose action sheet is open. */
@Stable
class PhoneListState(selection: TouchSelection = TouchSelection.None, sheet: LibraryRow? = null) {
    var selection by mutableStateOf(selection)
    var sheet by mutableStateOf(sheet)
}

internal fun rowKey(row: LibraryRow): Any = "${row.owner.name}:${row.position}"

@Composable
private fun PhoneLibrary(
    state: LibraryState,
    owner: Owner,
    onOwner: (Owner) -> Unit,
    actions: BookActions,
    title: String,
    onAdd: () -> Unit,
    onShelf: (Owner, String) -> Unit,
    previewSelected: List<Int>,
    previewSheet: Int?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val dimens = DeskTheme.dimens
    val now = System.currentTimeMillis()
    var sortName by rememberSaveable { mutableStateOf(PhoneSort.TITLE.name) }
    val sort = PhoneSort.valueOf(sortName)
    var sorting by remember { mutableStateOf(false) }
    val books = state.table?.books
    val rows = remember(books, owner) { books.orEmpty().mapIndexed { index, book -> LibraryRow(owner, index, book) } }
    val sorted = remember(rows, sort) { sortRows(rows, sort) }
    val list = remember(owner) { PhoneListState(TouchSelection(previewSelected.mapTo(HashSet()) { "${owner.name}:$it" })) }
    // screenshots: the sheet opens once the list has been read
    val previewShown = remember { booleanArrayOf(false) }
    LaunchedEffect(rows) {
        if (previewSheet != null && !previewShown[0] && rows.isNotEmpty()) {
            previewShown[0] = true
            list.sheet = rows.getOrNull(previewSheet)
        }
    }
    val listState = rememberSaveable(owner, saver = LazyListState.Saver) { LazyListState() }

    PhoneListTopBar(list, sorted, actions, normalActions = listOf(TopBarAction(DeskIcons.Sort, stringResource(R.string.touchlist_sort), { sorting = true })))

    Column(Modifier.fillMaxSize()) {
        PageHeader(title, subtitle = LibraryTexts.updated(resources, owner, state, now) + " · " + LibraryTexts.status(resources, state))
        WideSegmentedChoice(
            options = Owner.entries.map { it to LibraryTexts.ownerLabel(resources, it) },
            selected = owner,
            onSelect = onOwner,
            modifier = Modifier.padding(horizontal = dimens.pagePadding).padding(bottom = dimens.gapS),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.isLinked -> Message(stringResource(R.string.library_not_linked))
                books == null -> Message(LibraryTexts.status(resources, state))
                books.isEmpty() -> Message(stringResource(R.string.library_empty))
                else -> PhoneBookList(
                    sorted, list, actions, onShelf, listState, Modifier.fillMaxSize(), appearKey = owner,
                    refreshing = refreshing, onRefresh = onRefresh,
                )
            }
        }
    }
    // "Kitap ekle": the kit's floating button, above the bottom bar, the label folding away once the list scrolls
    if (!list.selection.active && state.isLinked) ShellPrimaryAction(stringResource(R.string.library_add), DeskIcons.Plus, onAdd)
    // the kit's "⋯" sheet: the orders, the chosen one ticked
    val sortLabels = PhoneSort.entries.associateWith { sortLabel(it) }
    MoreSheet(
        visible = sorting,
        onDismiss = { sorting = false },
        title = stringResource(R.string.touchlist_sort),
        groups = listOf(
            ActionGroup(null, PhoneSort.entries.map { option ->
                MenuEntry.Item(sortLabels.getValue(option), { sortName = option.name }, icon = if (option == sort) DeskIcons.Check else null)
            }),
        ),
    )
}

@Composable
private fun sortLabel(sort: PhoneSort): String = stringResource(
    when (sort) {
        PhoneSort.TITLE -> R.string.touchlist_sort_title
        PhoneSort.AUTHOR -> R.string.touchlist_sort_author
        PhoneSort.SHELF -> R.string.touchlist_sort_shelf
        PhoneSort.FILE -> R.string.touchlist_sort_file
    },
)

/**
 * The phone top bar for a list of books. In selection mode: × (ends it), "N seçili", "Rafa koy…"
 * and "Tümünü seç"; otherwise [title] (null: the screen's name), [onBack] and [normalActions].
 */
@Composable
fun PhoneListTopBar(
    list: PhoneListState,
    rows: List<LibraryRow>,
    actions: BookActions,
    normalActions: List<TopBarAction> = emptyList(),
    title: String? = null,
    onBack: (() -> Unit)? = null,
) {
    val selection = list.selection
    if (selection.active) {
        val keys = remember(rows) { rows.map(::rowKey) }
        CompactTopBar(
            title = stringResource(R.string.touchlist_selected_count, selection.size),
            onBack = { list.selection = TouchSelection.None },
            backIcon = TopBarBackIcon.Close,
            actions = listOf(
                TopBarAction(AppIcons.Shelf, stringResource(R.string.action_place), {
                    rows.filter { rowKey(it) in selection }.groupBy { it.owner }.forEach { (owner, chosen) -> actions.placeOnShelf(owner, chosen.map { it.book }) }
                }, primary = true),
                TopBarAction(TouchIcons.SelectAll, stringResource(R.string.touchlist_select_all), { list.selection = selection.selectAll(keys) }, enabled = !selection.hasAll(keys)),
            ),
            showSearch = false,
        )
    } else {
        CompactTopBar(title = title, onBack = onBack, actions = normalActions)
    }
}

/**
 * The phone list of books: two-line rows (title over author, the shelf on the right). A tap opens
 * the book's action sheet, a long press starts selecting; while selecting a tap ticks a row.
 * [header] goes above the rows. The last rows stay clear of the floating button (the kit counts it
 * in the shell's padding). With [onRefresh] a pull from the top of the list reads both files again.
 */
@Composable
fun PhoneBookList(
    rows: List<LibraryRow>,
    state: PhoneListState,
    actions: BookActions,
    onShelf: ((Owner, String) -> Unit)?,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    header: (LazyListScope.() -> Unit)? = null,
    /** False on an open shelf, where every row would repeat the shelf's code. */
    showShelf: Boolean = true,
    /**
     * The rows come in one after the other when this changes after the list has appeared (the other
     * person's list); a list that opens with its rows shows them at once (kit 2.8.0).
     */
    appearKey: Any? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    // Rows are not animated (no animateItem): a list holds thousands of books, and a new sort
    // order moving every row at once looked jerky on a phone. The list simply shows the new order.
    // a change of the list (a book put on a shelf, another list) ends the selection, as on DeX
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(rows) {
        if (first[0]) first[0] = false else state.selection = TouchSelection.None
    }
    val selection = state.selection
    // the rows run on under the glass bottom bar; the kit's padding keeps the last ones clear of the floating button
    val shellPadding = rememberShellContentPadding()
    val appear = rememberListAppear(appearKey)
    PullToRefresh(refreshing, onRefresh ?: {}, modifier, enabled = onRefresh != null && DeskTheme.inputMode == InputMode.Touch) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = shellPadding.calculateBottomPadding())) {
            header?.invoke(this)
            itemsIndexed(rows, key = { _, row -> rowKey(row) }, contentType = { _, _ -> "book" }) { index, row ->
                val key = rowKey(row)
                PhoneBookRow(
                    row,
                    modifier = Modifier.listAppear(appear, index),
                    selected = key in selection,
                    selecting = selection.active,
                    showShelf = showShelf,
                    onClick = { if (state.selection.active) state.selection = state.selection.toggle(key) else state.sheet = row },
                    onLongClick = { state.selection = state.selection.toggle(key) },
                    onEdit = { actions.edit(row.owner, row.book) },
                    onDelete = { actions.delete(row.owner, row.book) },
                )
            }
        }
    }
    state.sheet?.let { row -> BookSheet(row, actions, onShelf, onDismiss = { state.sheet = null }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneBookRow(
    row: LibraryRow,
    selected: Boolean,
    selecting: Boolean,
    showShelf: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val book = row.book
    // the kit's colours: instant under a mouse, eased on touch
    val background by animatedStateBackground(state, selected)
    val fade = DeskMotion.fade(LocalMotionTier.current, DeskMotion.color<Color>())
    val main by animateColorAsState(if (selected) colors.onSelection else colors.text, fade, label = "rowText")
    val second by animateColorAsState(if (selected) colors.onSelection else colors.textSecondary, fade, label = "rowSecond")
    val shelfColor by animateColorAsState(if (selected) colors.onSelection else colors.accentText, fade, label = "rowShelf")
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(background)
                .focusRing(state, RectangleShape)
                .hoverable(interaction)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    onClick = onClick,
                )
                // a keyboard on the phone layout (a narrow DeX window): F2 edits, Del deletes, as in the table
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.F2 -> { onEdit(); true }
                        Key.Delete -> { onDelete(); true }
                        else -> false
                    }
                }
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = dimens.pagePadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                selecting,
                enter = fadeIn(tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.StandardDecelerate)) +
                    expandHorizontally(tween(DeskMotion.MEDIUM_MS + 50, easing = DeskMotion.EmphasizedDecelerate)),
                exit = fadeOut(tween(DeskMotion.SHORT_MS, easing = DeskMotion.StandardAccelerate)) +
                    shrinkHorizontally(tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.EmphasizedAccelerate)),
            ) {
                Row {
                    // the kit's check box, as in its list rows' selection mode
                    DeskCheckbox(selected, onCheckedChange = null)
                    Spacer(Modifier.width(dimens.gapL))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    book.title,
                    style = TitleStyle,
                    color = main,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank() || book.signed || book.copies > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (book.author.isNotBlank()) {
                            Text(
                                TurkishText.upper(book.author),
                                style = DeskTheme.type.labelSmall.copy(letterSpacing = 0.4.sp),
                                color = second,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        if (book.signed) SmallPill(stringResource(R.string.search_signed), selected)
                        if (book.copies > 1) Text(stringResource(R.string.touchlist_copies, book.copies), style = DeskTheme.type.labelSmall, color = second, maxLines = 1)
                    }
                }
            }
            if (!showShelf) {
                Unit
            } else if (book.shelf.isBlank()) {
                Spacer(Modifier.width(dimens.gapM))
                Text(stringResource(R.string.touchlist_no_shelf), style = DeskTheme.type.labelSmall.copy(fontStyle = FontStyle.Italic), color = if (selected) colors.onSelection else colors.textTertiary, maxLines = 1)
            } else {
                Spacer(Modifier.width(dimens.gapM))
                Text(
                    book.shelf,
                    style = ShelfStyle,
                    color = shelfColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 128.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = dimens.pagePadding).height(1.dp).background(colors.border))
    }
}

/** Book titles in Literata, as on the search screen (Draft A). */
private val TitleStyle = TextStyle(fontFamily = AppFonts.Literata, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp)

/** Shelf codes: bold, mono, accent, 20 sp as in the search results (S-30). */
private val ShelfStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp)

/** "İmzalı" beside the author, outlined as on the search screen. */
@Composable
private fun SmallPill(text: String, onSelection: Boolean) {
    val colors = DeskTheme.colors
    val color = if (onSelection) colors.onSelection else colors.textSecondary
    Text(
        text,
        style = DeskTheme.type.caption,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .border(1.dp, if (onSelection) colors.onSelection else colors.borderStrong, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** A book's details and everything that can be done with it, from a tap on its row (phone). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookSheet(row: LibraryRow, actions: BookActions, onShelf: ((Owner, String) -> Unit)?, onDismiss: () -> Unit) {
    val resources = LocalContext.current.resources
    val clipboard = LocalClipboardManager.current
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val book = row.book
    AppSheet(onDismiss = onDismiss) { close ->
        Column {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding).padding(top = dimens.gapXs, bottom = dimens.gapM),
                verticalArrangement = Arrangement.spacedBy(dimens.gapXs),
            ) {
                Text(book.title, style = TitleStyle.copy(fontSize = 20.sp, lineHeight = 26.sp), color = colors.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Text(TurkishText.upper(book.author), style = DeskTheme.type.label.copy(letterSpacing = 0.4.sp), color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(dimens.gapS))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapXl), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                    Fact(BookColumn.SHELF.header) {
                        if (book.shelf.isBlank()) {
                            Text(stringResource(R.string.search_no_shelf), style = DeskTheme.type.body.copy(fontStyle = FontStyle.Italic), color = colors.textTertiary)
                        } else {
                            Text(book.shelf, style = ShelfStyle, color = colors.accentText)
                        }
                    }
                    Fact(stringResource(R.string.touchlist_list)) { FactText(LibraryTexts.ownerLabel(resources, row.owner)) }
                    Fact(BookColumn.COPIES.header) { FactText(book.copies.toString()) }
                    Fact(BookColumn.SIGNED.header) { FactText(stringResource(if (book.signed) R.string.add_yes else R.string.touchlist_no)) }
                }
                val more = listOf(
                    BookColumn.PUBLISHER.header to book.publisher,
                    BookColumn.YEAR.header to book.year,
                    BookColumn.ISBN.header to book.isbn,
                    BookColumn.NOTE.header to book.note,
                ).filter { it.second.isNotBlank() }
                if (more.isNotEmpty()) {
                    Spacer(Modifier.height(dimens.gapXs))
                    more.forEach { (label, value) ->
                        Row {
                            Text(label, style = DeskTheme.type.label, color = colors.textTertiary, modifier = Modifier.width(88.dp))
                            Text(value, style = DeskTheme.type.body, color = colors.text)
                        }
                    }
                }
            }
            HorizontalRule()
            // the actions as the kit's list rows: icon and label, the whole line tappable
            SheetRow(stringResource(R.string.action_edit), TouchIcons.Pencil) { close { actions.edit(row.owner, book) } }
            SheetRow(stringResource(R.string.action_place), AppIcons.Shelf) { close { actions.placeOnShelf(row.owner, listOf(book)) } }
            SheetRow(stringResource(R.string.action_move), TouchIcons.Swap) { close { actions.move(row.owner, book) } }
            if (onShelf != null && book.shelf.isNotBlank()) {
                SheetRow(stringResource(R.string.action_shelf_books), DeskIcons.ListView) { close { onShelf(row.owner, book.shelf) } }
            }
            SheetRow(stringResource(R.string.action_copy), TouchIcons.Copy) {
                clipboard.setText(AnnotatedString(copyText(resources, row)))
                close {}
            }
            HorizontalRule()
            SheetRow(stringResource(R.string.action_delete), DeskIcons.Delete, tint = colors.danger) { close { actions.delete(row.owner, book) } }
        }
    }
}

/** One action of the book's sheet: the kit's list row with an icon, no divider. */
@Composable
private fun SheetRow(label: String, icon: ImageVector, tint: Color = DeskTheme.colors.textSecondary, onClick: () -> Unit) {
    DeskListRow(label, leading = icon, leadingTint = tint, divider = false, onClick = onClick)
}

@Composable
private fun Fact(label: String, value: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textTertiary)
        value()
    }
}

@Composable
private fun FactText(text: String) {
    Text(text, style = DeskTheme.type.bodyStrong, color = DeskTheme.colors.text, modifier = Modifier.padding(top = 2.dp))
}

// ------------------------------------------------------------------------------------ DeX / tablet

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
        AnimatedVisibility(
            chosen.size > 1 || touch,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
        ) {
            TableSelectionBar(
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

/**
 * "× 3 kitap seçili … Rafa koy…" in the kit's selection bar while several rows are chosen (or, on
 * touch, while taps tick rows); on touch otherwise only the "Seç" button that starts ticking. The
 * × ends the selection ("Seçimi kaldır") or the ticking ("Seçmeyi bitir").
 */
@Composable
private fun TableSelectionBar(count: Int, picking: Boolean, showPick: Boolean, onPick: () -> Unit, onPlace: () -> Unit, onClear: () -> Unit) {
    val dimens = DeskTheme.dimens
    if (count > 1 || picking) {
        desktopkit.components.SelectionBar(
            text = stringResource(R.string.select_count, count),
            onClose = if (picking) onPick else onClear,
            closeLabel = stringResource(if (picking) R.string.select_done else R.string.select_clear),
        ) {
            DeskButton(stringResource(R.string.action_place), onPlace, icon = AppIcons.Shelf, primary = true, enabled = count > 0)
        }
    } else if (showPick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs)) {
            DeskButton(stringResource(R.string.select_start), onPick, icon = DeskIcons.Check)
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
            // a tablet window with the rail (medium width) leaves these two out, so title and author keep their room
            TableColumn(BookColumn.PUBLISHER.header, ColumnWidth.Weight(1.5f), hideBelow = WidthClass.Expanded) { it.book.publisher },
            TableColumn(BookColumn.YEAR.header, ColumnWidth.Fixed(64.dp), hideBelow = WidthClass.Expanded) { it.book.year },
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
