package com.hesaplarimdijital.kitapbulucu.ui.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.model.ShelfGroup
import com.hesaplarimdijital.kitapbulucu.model.Shelves
import com.hesaplarimdijital.kitapbulucu.search.SearchHit
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.ui.BookActions
import com.hesaplarimdijital.kitapbulucu.ui.ConfirmDialog
import com.hesaplarimdijital.kitapbulucu.ui.DialogAction
import com.hesaplarimdijital.kitapbulucu.ui.FormField
import com.hesaplarimdijital.kitapbulucu.ui.LibraryTexts
import com.hesaplarimdijital.kitapbulucu.ui.library.BookTable
import com.hesaplarimdijital.kitapbulucu.ui.library.LibraryRow
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import desktopkit.components.DeskButton
import desktopkit.components.DeskIcons
import desktopkit.components.SegmentedChoice
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.stateBackground
import desktopkit.screens.PageHeader
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode

/** Where the Shelves screen should open: a list and, optionally, one shelf. */
data class ShelfTarget(val owner: Owner, val shelf: String?)

/** How many search results the add-books panel lists; "put all" still takes every result. */
private const val SHOWN_HITS = 6

/**
 * Shelves of one list (S-21, S-33): the shelves created on this device and the shelves the books
 * name, with their book counts, the unshelved group last. "Yeni raf" creates a shelf; an open
 * shelf shows its books and can be renamed, deleted when empty, and filled with "Kitap ekle"
 * (S-34): search, click or Enter, and the book goes on the shelf as one undoable step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShelvesScreen(
    store: LibraryStore,
    settings: AppSettings?,
    actions: BookActions,
    title: String,
    target: ShelfTarget? = null,
    /** For screenshots: the open shelf starts with the add-books search holding this text. */
    initialAddQuery: String? = null,
) {
    val states by store.states.collectAsState()
    val catalog by store.shelfCatalog.entries.collectAsState()
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    var owner by rememberSaveable(target) { mutableStateOf(target?.owner ?: settings?.me ?: Owner.DAD) }
    var openShelf by rememberSaveable(target) { mutableStateOf(target?.shelf) }
    var adding by rememberSaveable(target) { mutableStateOf(initialAddQuery != null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ShelfGroup?>(null) }
    var deleting by remember { mutableStateOf<ShelfGroup?>(null) }
    val books = states.getValue(owner).table?.books.orEmpty()
    val saved = catalog[owner].orEmpty()
    val groups = remember(books, saved) { Shelves.overview(books, saved) }
    val codes = remember(groups) { groups.filterNot { it.isUnshelved }.map { it.shelf } }
    val me = settings?.me

    Column(Modifier.fillMaxSize()) {
        PageHeader(title, subtitle = stringResource(R.string.fill_left, Shelves.unshelvedCount(books)))
        Row(Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding).padding(bottom = dimens.gapS), verticalAlignment = Alignment.CenterVertically) {
            SegmentedChoice(Owner.entries.map { it to LibraryTexts.ownerLabel(resources, it) }, owner, {
                owner = it
                openShelf = null
                adding = false
            })
            Spacer(Modifier.weight(1f))
            if (openShelf == null) DeskButton(stringResource(R.string.shelves_new), { creating = true }, icon = DeskIcons.Plus, primary = true)
        }
        val open = openShelf
        if (open != null) {
            val group = groups.firstOrNull { Shelves.key(it.shelf) == Shelves.key(open) } ?: ShelfGroup(open, emptyList())
            Row(Modifier.padding(horizontal = dimens.pagePadding), verticalAlignment = Alignment.CenterVertically) {
                DeskButton(stringResource(R.string.shelves_back), {
                    openShelf = null
                    adding = false
                }, icon = DeskIcons.ChevronLeft)
                Spacer(Modifier.width(dimens.gapM))
                Text(
                    if (group.isUnshelved) stringResource(R.string.shelves_unshelved) else group.shelf,
                    style = DeskTheme.type.headline.copy(fontFamily = if (group.isUnshelved) DeskTheme.type.headline.fontFamily else FontFamily.Monospace),
                    color = colors.accentText,
                    maxLines = 1,
                )
                Spacer(Modifier.width(dimens.gapS))
                Text(shelfCount(group), style = DeskTheme.type.label, color = colors.textSecondary, maxLines = 1)
                if (group.note.isNotEmpty()) {
                    Spacer(Modifier.width(dimens.gapS))
                    Text("· " + group.note, style = DeskTheme.type.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!group.isUnshelved) {
                FlowRow(
                    Modifier.padding(horizontal = dimens.pagePadding).padding(top = dimens.gapS),
                    horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
                    verticalArrangement = Arrangement.spacedBy(dimens.gapS),
                ) {
                    DeskButton(
                        stringResource(if (adding) R.string.shelves_add_close else R.string.shelves_add_books),
                        { adding = !adding },
                        icon = if (adding) DeskIcons.Check else DeskIcons.Plus,
                        primary = !adding,
                    )
                    DeskButton(stringResource(R.string.shelves_rename), { renaming = group })
                    if (group.canDelete) DeskButton(stringResource(R.string.shelves_delete), { deleting = group }, icon = DeskIcons.Delete)
                }
                if (adding) {
                    AddBooksPanel(
                        store, actions, owner, group.shelf, initialAddQuery.orEmpty(),
                        onClose = { adding = false },
                        modifier = Modifier.padding(horizontal = dimens.pagePadding).padding(top = dimens.gapM).widthIn(max = 760.dp).fillMaxWidth(),
                    )
                } else if (group.books.isEmpty()) {
                    Text(
                        stringResource(R.string.shelves_open_empty),
                        style = DeskTheme.type.body,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapM),
                    )
                }
            }
            val rows = remember(group.books, owner) { group.books.map { LibraryRow(owner, it.index, it.value) } }
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(dimens.gapS))
                BookTable(rows, actions, onShelf = null, modifier = Modifier.weight(1f))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (codes.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.shelves_none),
                            style = DeskTheme.type.body,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapM),
                        )
                    }
                }
                items(groups, key = { "shelf:" + it.shelf }) { group ->
                    ShelfRow(group, Modifier.padding(horizontal = dimens.pagePadding).widthIn(max = 760.dp).fillMaxWidth()) {
                        openShelf = group.shelf
                        adding = false
                    }
                }
                item { Spacer(Modifier.height(dimens.gapL)) }
            }
        }
    }

    if (creating) {
        ShelfFormDialog(
            title = stringResource(R.string.shelves_new),
            existing = codes,
            onSave = { code, note ->
                store.shelfCatalog.add(owner, code, note)
                // a new shelf opens ready for its books
                openShelf = code
                adding = true
            },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { group ->
        val text = buildString {
            if (group.books.isNotEmpty()) {
                append(resources.getString(R.string.shelves_rename_text, group.books.size))
                if (me != null && owner != me) append("\n\n").append(resources.getString(R.string.other_list_warning, LibraryTexts.listName(resources, owner)))
            }
        }
        ShelfFormDialog(
            title = stringResource(R.string.shelves_rename_title, group.shelf),
            existing = codes,
            text = text.ifEmpty { null },
            initialCode = group.shelf,
            initialNote = group.note,
            renaming = group.shelf,
            onSave = { code, note ->
                store.renameShelf(owner, group.shelf, code, note, resources.getString(R.string.shelf_renamed, group.shelf, code))
                if (openShelf != null) openShelf = code
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { group ->
        ConfirmDialog(
            title = resources.getString(R.string.shelves_delete_title, group.shelf),
            text = resources.getString(R.string.shelves_delete_text),
            actions = listOf(
                DialogAction(resources.getString(R.string.action_delete), primary = true) {
                    store.shelfCatalog.remove(owner, group.shelf)
                    openShelf = null
                    adding = false
                },
                DialogAction(resources.getString(R.string.action_cancel)) {},
            ),
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ShelfRow(group: ShelfGroup, modifier: Modifier, onClick: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        modifier
            .defaultMinSize(minHeight = dimens.minTarget.coerceAtLeast(44.dp))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .deskClickable(interaction, onClick, role = Role.Button)
            .padding(horizontal = dimens.gapM, vertical = dimens.gapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (group.isUnshelved) {
            Text(stringResource(R.string.shelves_unshelved), style = DeskTheme.type.body.copy(fontStyle = FontStyle.Italic), color = colors.textSecondary, modifier = Modifier.weight(1f))
        } else {
            Text(group.shelf, style = DeskTheme.type.body.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp), color = colors.accentText, maxLines = 1)
            Spacer(Modifier.width(dimens.gapM))
            Text(group.note, style = DeskTheme.type.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.width(dimens.gapS))
        Text(shelfCount(group), style = DeskTheme.type.label, color = colors.textSecondary, maxLines = 1)
    }
}

/**
 * S-34: a search over the list; a click or Enter on the highlighted result puts that book on
 * [shelf], clears the search and keeps the cursor there for the next book. "Put all" asks first.
 * The other person's list and a book on another shelf are asked about by [BookActions.putOnShelf].
 */
@Composable
private fun AddBooksPanel(
    store: LibraryStore,
    actions: BookActions,
    owner: Owner,
    shelf: String,
    initialQuery: String,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val index by store.index.collectAsState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    var query by rememberSaveable(shelf, owner) { mutableStateOf(initialQuery) }
    var active by remember(shelf, owner) { mutableIntStateOf(0) }
    var message by remember(shelf, owner) { mutableStateOf<String?>(null) }
    var placed by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val hits: List<SearchHit> = remember(index, query, owner) {
        val current = index
        if (current == null || query.isBlank()) emptyList() else current.search(query, owner, limit = 50)
    }
    val shown = hits.take(SHOWN_HITS)
    val shelfKey = Shelves.key(shelf)
    val notHere = hits.filter { Shelves.key(it.book.shelf) != shelfKey }

    val place = { books: List<Book>, all: Boolean ->
        actions.putOnShelf(owner, books, shelf, askMove = true, confirm = all) { label ->
            message = label
            query = ""
            active = 0
            placed++
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
        FormField(
            stringResource(R.string.shelves_add_search), query, {
                query = it
                active = 0
            },
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> { if (shown.isNotEmpty()) active = (active + 1).coerceAtMost(shown.size - 1); true }
                    Key.DirectionUp -> { active = (active - 1).coerceAtLeast(0); true }
                    Key.Escape -> { onClose(); true }
                    else -> false
                }
            },
            focusRequester = focus,
            onEnter = { shown.getOrNull(active)?.let { place(listOf(it.book), false) } },
        )
        message?.let { Text(it, style = DeskTheme.type.body, color = colors.success) }
        if (query.isNotBlank() && hits.isEmpty()) {
            Text(stringResource(R.string.shelves_add_none), style = DeskTheme.type.body, color = colors.textSecondary)
        }
        if (shown.isNotEmpty()) {
            Column {
                shown.forEachIndexed { i, hit ->
                    HitRow(hit, highlighted = i == active, here = Shelves.key(hit.book.shelf) == shelfKey) { place(listOf(hit.book), false) }
                }
            }
        }
        if (notHere.size > 1) {
            DeskButton(stringResource(R.string.fill_assign_all, notHere.size, shelf), { place(notHere.map { it.book }, true) })
        }
        if (DeskTheme.inputMode == InputMode.Pointer) {
            Text(stringResource(R.string.shelves_add_hint), style = DeskTheme.type.labelSmall, color = colors.textTertiary)
        }
    }
    // on opening and after every placement the cursor waits in the search for the next book
    LaunchedEffect(placed) { runCatching { focus.requestFocus() } }
}

@Composable
private fun HitRow(hit: SearchHit, highlighted: Boolean, here: Boolean, onClick: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val main = if (highlighted) colors.onSelection else colors.text
    val second = if (highlighted) colors.onSelection else colors.textSecondary
    val book = hit.book
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimens.minTarget.coerceAtLeast(40.dp))
            .focusRing(state, shape)
            .background(stateBackground(state, selected = highlighted), shape)
            .deskClickable(interaction, onClick, role = Role.Button)
            .padding(horizontal = dimens.gapM, vertical = dimens.gapXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                style = DeskTheme.type.body.copy(fontFamily = AppFonts.Literata, fontWeight = FontWeight.Bold),
                color = main,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) Text(book.author, style = DeskTheme.type.label, color = second, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(dimens.gapM))
        Text(
            when {
                here -> stringResource(R.string.shelves_here)
                book.shelf.isBlank() -> stringResource(R.string.search_no_shelf)
                else -> book.shelf
            },
            style = DeskTheme.type.label.copy(fontFamily = if (here || book.shelf.isBlank()) DeskTheme.type.label.fontFamily else FontFamily.Monospace),
            color = if (here && !highlighted) colors.success else second,
            maxLines = 1,
        )
    }
}
