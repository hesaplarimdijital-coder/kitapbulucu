package com.hesaplarimdijital.kitapbulucu.ui.shelves

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
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
import com.hesaplarimdijital.kitapbulucu.text.TurkishText
import com.hesaplarimdijital.kitapbulucu.ui.AnimatedFab
import com.hesaplarimdijital.kitapbulucu.ui.BookActions
import com.hesaplarimdijital.kitapbulucu.ui.ConfirmDialog
import com.hesaplarimdijital.kitapbulucu.ui.DialogAction
import com.hesaplarimdijital.kitapbulucu.ui.FormField
import com.hesaplarimdijital.kitapbulucu.ui.LibraryTexts
import com.hesaplarimdijital.kitapbulucu.ui.TextAction
import com.hesaplarimdijital.kitapbulucu.ui.TouchIcons
import com.hesaplarimdijital.kitapbulucu.ui.WideSegmentedChoice
import com.hesaplarimdijital.kitapbulucu.ui.isCompactLayout
import com.hesaplarimdijital.kitapbulucu.ui.library.BookTable
import com.hesaplarimdijital.kitapbulucu.ui.library.LibraryRow
import com.hesaplarimdijital.kitapbulucu.ui.library.PhoneBookList
import com.hesaplarimdijital.kitapbulucu.ui.library.PhoneListState
import com.hesaplarimdijital.kitapbulucu.ui.library.PhoneListTopBar
import com.hesaplarimdijital.kitapbulucu.ui.theme.AppFonts
import desktopkit.components.DeskButton
import desktopkit.components.DeskIcons
import desktopkit.components.SegmentedChoice
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.stateBackground
import desktopkit.screens.PageHeader
import desktopkit.shell.CompactTopBar
import desktopkit.shell.DetailTransition
import desktopkit.shell.TopBarAction
import desktopkit.shell.TopBarBackIcon
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode

/** Where the Shelves screen should open: a list and, optionally, one shelf. */
data class ShelfTarget(val owner: Owner, val shelf: String?)

/** How many search results the add-books panel lists on DeX; "put all" still takes every result. */
private const val SHOWN_HITS = 6

/** How many results the phone's full-height add-books panel lists. */
private const val PHONE_HITS = 50

/**
 * Shelves of one list (S-21, S-33): the shelves created on this device and the shelves the books
 * name, with their book counts, the unshelved group last. "Yeni raf" creates a shelf; an open
 * shelf shows its books and can be renamed, deleted when empty, and filled with "Kitap ekle"
 * (S-34): search, click or Enter, and the book goes on the shelf as one undoable step.
 * On the phone the shelf's name and its actions go to the top bar, "Yeni raf" and "Kitap ekle"
 * float bottom right and adding books takes the whole screen.
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

    if (isCompactLayout()) {
        PhoneShelves(
            store, actions, title, owner,
            onOwner = {
                owner = it
                openShelf = null
                adding = false
            },
            openShelf = openShelf,
            onOpen = {
                openShelf = it
                adding = false
            },
            adding = adding,
            onAdding = { adding = it },
            groups = groups,
            codes = codes,
            unshelved = Shelves.unshelvedCount(books),
            initialAddQuery = initialAddQuery.orEmpty(),
            onCreate = { creating = true },
            onRename = { renaming = it },
            onDelete = { deleting = it },
        )
    } else {
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
            // the list and an open shelf cross-fade
            AnimatedContent(
                openShelf,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "shelf",
            ) { open ->
                Column(Modifier.fillMaxSize()) {
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
                            AnimatedVisibility(
                                adding,
                                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                                exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                            ) {
                                AddBooksPanel(
                                    store, actions, owner, group.shelf, initialAddQuery.orEmpty(),
                                    onClose = { adding = false },
                                    modifier = Modifier.padding(horizontal = dimens.pagePadding).padding(top = dimens.gapM).widthIn(max = 760.dp).fillMaxWidth(),
                                )
                            }
                            if (!adding && group.books.isEmpty()) {
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
                                ShelfRow(group, Modifier.animateItem().padding(horizontal = dimens.pagePadding).widthIn(max = 760.dp).fillMaxWidth()) {
                                    openShelf = group.shelf
                                    adding = false
                                }
                            }
                            item { Spacer(Modifier.height(dimens.gapL)) }
                        }
                    }
                }
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

// ------------------------------------------------------------------------------------ phone

/**
 * The phone layout: the list of shelves (full-width rows, "Yeni raf" floating), or one shelf
 * with its name in the top bar, its books as the phone list and "Kitap ekle" floating. Adding
 * books covers the shelf with a full-height search. The list and a shelf slide into each other.
 */
@Composable
private fun PhoneShelves(
    store: LibraryStore,
    actions: BookActions,
    title: String,
    owner: Owner,
    onOwner: (Owner) -> Unit,
    openShelf: String?,
    onOpen: (String?) -> Unit,
    adding: Boolean,
    onAdding: (Boolean) -> Unit,
    groups: List<ShelfGroup>,
    codes: List<String>,
    unshelved: Int,
    initialAddQuery: String,
    onCreate: () -> Unit,
    onRename: (ShelfGroup) -> Unit,
    onDelete: (ShelfGroup) -> Unit,
) {
    val resources = LocalContext.current.resources
    val dimens = DeskTheme.dimens
    val open = openShelf
    val group = open?.let { code -> groups.firstOrNull { Shelves.key(it.shelf) == Shelves.key(code) } ?: ShelfGroup(code, emptyList()) }
    val rows = remember(group?.books, owner) { group?.books.orEmpty().map { LibraryRow(owner, it.index, it.value) } }
    val list = remember(owner, open) { PhoneListState() }

    // the top bar: adding books, selecting rows, an open shelf, or the screen's own
    when {
        group == null -> Unit
        adding && !group.isUnshelved -> CompactTopBar(
            title = stringResource(R.string.touchlist_shelf_add_title, group.shelf),
            onBack = { onAdding(false) },
            backIcon = TopBarBackIcon.Close,
            showSearch = false,
        )
        else -> PhoneListTopBar(
            list, rows, actions,
            normalActions = if (group.isUnshelved) {
                emptyList()
            } else {
                listOfNotNull(
                    TopBarAction(TouchIcons.Pencil, stringResource(R.string.shelves_rename), { onRename(group) }),
                    if (group.canDelete) TopBarAction(DeskIcons.Delete, stringResource(R.string.shelves_delete), { onDelete(group) }) else null,
                )
            },
            title = if (group.isUnshelved) stringResource(R.string.shelves_unshelved) else group.shelf,
            onBack = { onOpen(null) },
        )
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(title, subtitle = stringResource(R.string.fill_left, unshelved))
        // list → shelf with Android's shared-axis transition; the predictive Back gesture shrinks the
        // open shelf with the finger. While books are being added or rows ticked, Back ends that first
        // (the top bar's handler), so the shelf only takes Back when neither is going on.
        DetailTransition(
            detail = open,
            onBack = { onOpen(null) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            handleBack = !adding && !list.selection.active,
            detailKey = { Shelves.key(it) },
            list = { PhoneShelfList(groups, codes, owner, onOwner, onOpen = { onOpen(it) }, onCreate = onCreate) },
        ) { shown ->
            val shownGroup = groups.firstOrNull { Shelves.key(it.shelf) == Shelves.key(shown) } ?: ShelfGroup(shown, emptyList())
            PhoneShelfDetail(
                store, actions, owner, shownGroup,
                rows = remember(shownGroup.books, owner) { shownGroup.books.map { LibraryRow(owner, it.index, it.value) } },
                list = list,
                adding = adding && shown == open,
                onAdding = onAdding,
                initialAddQuery = initialAddQuery,
                listLabel = LibraryTexts.ownerLabel(resources, owner),
            )
        }
    }
}

@Composable
private fun PhoneShelfList(
    groups: List<ShelfGroup>,
    codes: List<String>,
    owner: Owner,
    onOwner: (Owner) -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val listState = rememberSaveable(owner, saver = LazyListState.Saver) { LazyListState() }
    val expanded by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    Column(Modifier.fillMaxSize()) {
        WideSegmentedChoice(
            options = Owner.entries.map { it to LibraryTexts.ownerLabel(resources, it) },
            selected = owner,
            onSelect = onOwner,
            modifier = Modifier.padding(horizontal = dimens.pagePadding).padding(bottom = dimens.gapS),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                if (codes.isEmpty()) {
                    item(key = "none") {
                        Text(
                            stringResource(R.string.shelves_none),
                            style = DeskTheme.type.body,
                            color = colors.textSecondary,
                            modifier = Modifier.animateItem().padding(horizontal = dimens.pagePadding, vertical = dimens.gapM),
                        )
                    }
                }
                items(groups, key = { "shelf:" + it.shelf }) { group ->
                    PhoneShelfRow(group, Modifier.animateItem()) { onOpen(group.shelf) }
                }
            }
            AnimatedFab(
                visible = true,
                label = stringResource(R.string.shelves_new),
                icon = DeskIcons.Plus,
                onClick = onCreate,
                expanded = expanded,
                modifier = Modifier.align(Alignment.BottomEnd).padding(dimens.gapL),
            )
        }
    }
}

/** A shelf on the phone: the code large in the accent colour, its description, the book count. */
@Composable
private fun PhoneShelfRow(group: ShelfGroup, modifier: Modifier, onClick: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val background by animateColorAsState(stateBackground(state), DeskMotion.color(), label = "shelfRow")
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(background)
                .focusRing(state, RectangleShape)
                .deskClickable(interaction, onClick, role = Role.Button)
                .defaultMinSize(minHeight = 64.dp)
                .padding(start = dimens.pagePadding, end = dimens.gapS, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (group.isUnshelved) {
                Text(
                    stringResource(R.string.shelves_unshelved),
                    style = DeskTheme.type.body.copy(fontStyle = FontStyle.Italic),
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    group.shelf,
                    style = PhoneShelfStyle,
                    color = colors.accentText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(min = 56.dp, max = 152.dp),
                )
                Spacer(Modifier.width(dimens.gapL))
                Text(
                    group.note,
                    style = DeskTheme.type.body,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.width(dimens.gapS))
            Text(shelfCount(group), style = DeskTheme.type.label, color = if (group.books.isEmpty()) colors.textTertiary else colors.textSecondary, maxLines = 1)
            Icon(DeskIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.padding(start = dimens.gapXs).size(20.dp))
        }
        Box(Modifier.fillMaxWidth().padding(start = dimens.pagePadding).height(1.dp).background(colors.border))
    }
}

private val PhoneShelfStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)

/** One shelf on the phone: description and count on top, then its books; "Kitap ekle" floats. */
@Composable
private fun PhoneShelfDetail(
    store: LibraryStore,
    actions: BookActions,
    owner: Owner,
    group: ShelfGroup,
    rows: List<LibraryRow>,
    list: PhoneListState,
    adding: Boolean,
    onAdding: (Boolean) -> Unit,
    initialAddQuery: String,
    listLabel: String,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val listState = remember(group.shelf, owner) { LazyListState() }
    val expanded by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    Box(Modifier.fillMaxSize()) {
        PhoneBookList(
            rows, list, actions, onShelf = null, listState = listState, modifier = Modifier.fillMaxSize(), showShelf = false,
            header = {
                item(key = "header") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding).padding(top = dimens.gapXs, bottom = dimens.gapM),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (group.note.isNotEmpty()) Text(group.note, style = DeskTheme.type.bodyStrong, color = colors.text)
                        Text(listLabel + " · " + shelfCount(group), style = DeskTheme.type.label, color = colors.textSecondary)
                    }
                }
                if (rows.isEmpty() && !group.isUnshelved) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.shelves_open_empty),
                            style = DeskTheme.type.body,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapM),
                        )
                    }
                }
            },
        )
        AnimatedFab(
            visible = !group.isUnshelved && !adding && !list.selection.active,
            label = stringResource(R.string.shelves_add_books),
            icon = DeskIcons.Plus,
            onClick = { onAdding(true) },
            expanded = expanded,
            modifier = Modifier.align(Alignment.BottomEnd).padding(dimens.gapL),
        )
        AnimatedVisibility(
            adding && !group.isUnshelved,
            enter = slideInVertically(tween(DeskMotion.LONG_MS, easing = DeskMotion.EmphasizedDecelerate)) { it / 10 } +
                fadeIn(tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.StandardDecelerate)),
            exit = slideOutVertically(tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.EmphasizedAccelerate)) { it / 10 } +
                fadeOut(tween(DeskMotion.SHORT_MS + 50, easing = DeskMotion.StandardAccelerate)),
        ) {
            AddBooksPanel(
                store, actions, owner, group.shelf, initialAddQuery,
                onClose = { onAdding(false) },
                modifier = Modifier.fillMaxSize().background(colors.background),
                compact = true,
            )
        }
    }
}

// ------------------------------------------------------------------------------------ DeX rows

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

// ------------------------------------------------------------------------------------ adding books

/**
 * S-34: a search over the list; a click or Enter on the highlighted result puts that book on
 * [shelf], clears the search and keeps the cursor there for the next book. "Put all" asks first.
 * The other person's list and a book on another shelf are asked about by [BookActions.putOnShelf].
 * [compact]: the phone's full-height version, the field with "Bitti" on top and the results
 * below as two-line rows.
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
    compact: Boolean = false,
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
    val shown = hits.take(if (compact) PHONE_HITS else SHOWN_HITS)
    val shelfKey = Shelves.key(shelf)
    val notHere = hits.filter { Shelves.key(it.book.shelf) != shelfKey }
    // on touch nothing is highlighted: Enter comes from a keyboard
    val highlight = !compact || DeskTheme.inputMode == InputMode.Pointer

    val place = { books: List<Book>, all: Boolean ->
        actions.putOnShelf(owner, books, shelf, askMove = true, confirm = all) { label ->
            message = label
            query = ""
            active = 0
            placed++
        }
    }

    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        FormField(
            stringResource(R.string.shelves_add_search), query, {
                query = it
                active = 0
            },
            modifier = fieldModifier.onPreviewKeyEvent { event ->
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
    }

    if (compact) {
        Column(modifier) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding).padding(top = dimens.gapS),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
            ) {
                field(Modifier.weight(1f))
                DeskButton(stringResource(R.string.touchlist_done), onClose, icon = DeskIcons.Check, primary = true)
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding).padding(top = dimens.gapS),
                verticalArrangement = Arrangement.spacedBy(dimens.gapXs),
            ) {
                AnimatedVisibility(message != null, enter = fadeIn(tween(200)) + expandVertically(tween(200)), exit = fadeOut(tween(120)) + shrinkVertically(tween(120))) {
                    Text(message.orEmpty(), style = DeskTheme.type.body, color = colors.success)
                }
                if (notHere.size > 1) {
                    TextAction(stringResource(R.string.fill_assign_all, notHere.size, shelf), { place(notHere.map { it.book }, true) })
                }
                if (query.isNotBlank() && hits.isEmpty()) {
                    Text(stringResource(R.string.shelves_add_none), style = DeskTheme.type.body, color = colors.textSecondary)
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = dimens.gapXs, bottom = dimens.gapL)) {
                itemsIndexed(shown) { i, hit ->
                    PhoneHitRow(hit, highlighted = highlight && i == active, here = Shelves.key(hit.book.shelf) == shelfKey, modifier = Modifier.animateItem()) {
                        place(listOf(hit.book), false)
                    }
                }
            }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            field(Modifier.fillMaxWidth())
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

/** A result of the phone's add-books search: two lines like the library list, where it stands now on the right. */
@Composable
private fun PhoneHitRow(hit: SearchHit, highlighted: Boolean, here: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val main = if (highlighted) colors.onSelection else colors.text
    val second = if (highlighted) colors.onSelection else colors.textSecondary
    val book = hit.book
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(stateBackground(state, selected = highlighted))
                .focusRing(state, RectangleShape)
                .deskClickable(interaction, onClick, role = Role.Button)
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = dimens.pagePadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    book.title,
                    style = TextStyle(fontFamily = AppFonts.Literata, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
                    color = main,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Text(TurkishText.upper(book.author), style = DeskTheme.type.labelSmall.copy(letterSpacing = 0.4.sp), color = second, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(dimens.gapM))
            when {
                here -> Text(stringResource(R.string.shelves_here), style = DeskTheme.type.label, color = if (highlighted) second else colors.success, maxLines = 1)
                book.shelf.isBlank() -> Text(stringResource(R.string.touchlist_no_shelf), style = DeskTheme.type.labelSmall.copy(fontStyle = FontStyle.Italic), color = if (highlighted) second else colors.textTertiary, maxLines = 1)
                else -> Text(
                    book.shelf,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    color = if (highlighted) second else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = dimens.pagePadding).height(1.dp).background(colors.border))
    }
}
