package com.hesaplarimdijital.kitapbulucu.ui.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.model.ShelfGroup
import com.hesaplarimdijital.kitapbulucu.model.Shelves
import com.hesaplarimdijital.kitapbulucu.search.SearchHit
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.Change
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
import desktopkit.settings.SettingsSection
import desktopkit.theme.DeskTheme

/** Where the Shelves screen should open: a list and, optionally, one shelf. */
data class ShelfTarget(val owner: Owner, val shelf: String?)

/**
 * Shelves of one list with their book counts, the unshelved group last (S-21); a shelf opens
 * its books. Above them, shelf filling (S-22): an active shelf, a search, Enter puts the first
 * result on that shelf; every step can be undone.
 */
@Composable
fun ShelvesScreen(store: LibraryStore, settings: AppSettings?, actions: BookActions, title: String, target: ShelfTarget? = null) {
    val states by store.states.collectAsState()
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    var owner by rememberSaveable(target) { mutableStateOf(target?.owner ?: settings?.me ?: Owner.DAD) }
    var openShelf by rememberSaveable(target) { mutableStateOf(target?.shelf) }
    val books = states.getValue(owner).table?.books.orEmpty()
    val groups = remember(books) { Shelves.group(books) }

    Column(Modifier.fillMaxSize()) {
        PageHeader(title, subtitle = stringResource(R.string.fill_left, Shelves.unshelvedCount(books)))
        Row(Modifier.padding(horizontal = dimens.pagePadding).padding(bottom = dimens.gapS), verticalAlignment = Alignment.CenterVertically) {
            SegmentedChoice(Owner.entries.map { it to LibraryTexts.ownerLabel(resources, it) }, owner, { owner = it; openShelf = null })
        }
        val open = openShelf
        if (open != null) {
            val group = groups.firstOrNull { Shelves.key(it.shelf) == Shelves.key(open) }
            Row(Modifier.padding(horizontal = dimens.pagePadding), verticalAlignment = Alignment.CenterVertically) {
                DeskButton(stringResource(R.string.shelves_back), { openShelf = null }, icon = DeskIcons.ChevronLeft)
                Spacer(Modifier.width(dimens.gapM))
                Text(
                    if (open.isEmpty()) stringResource(R.string.shelves_unshelved) else open,
                    style = DeskTheme.type.headline.copy(fontFamily = if (open.isEmpty()) DeskTheme.type.headline.fontFamily else FontFamily.Monospace),
                    color = colors.accentText,
                )
                Spacer(Modifier.width(dimens.gapS))
                Text(stringResource(R.string.shelves_count, group?.books?.size ?: 0), style = DeskTheme.type.label, color = colors.textSecondary)
            }
            val rows = remember(group, owner) { group?.books?.map { LibraryRow(owner, it.index, it.value) }.orEmpty() }
            BookTable(rows, actions, onShelf = null, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item { FillSection(store, settings, owner, Modifier.fillMaxWidth()) }
                if (groups.none { !it.isUnshelved }) {
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
                    ShelfRow(group, Modifier.padding(horizontal = dimens.pagePadding).widthIn(max = 760.dp).fillMaxWidth()) { openShelf = group.shelf }
                }
                item { Spacer(Modifier.height(dimens.gapL)) }
            }
        }
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
            Text(group.shelf, style = DeskTheme.type.body.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp), color = colors.accentText, modifier = Modifier.weight(1f))
        }
        Text(stringResource(R.string.shelves_count, group.books.size), style = DeskTheme.type.label, color = colors.textSecondary)
    }
}

/** S-22: active shelf + search; Enter shelves the first result and clears the search. */
@Composable
private fun FillSection(store: LibraryStore, settings: AppSettings?, owner: Owner, modifier: Modifier) {
    val index by store.index.collectAsState()
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    var shelf by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmOtherFor by remember { mutableStateOf<Owner?>(null) }
    var askAll by remember { mutableStateOf(false) }
    // the one result waiting for the "other person's list" answer
    var pendingSingle by remember { mutableStateOf<SearchHit?>(null) }
    val searchFocus = remember { FocusRequester() }
    val hits: List<SearchHit> = remember(index, query, owner) {
        val current = index
        if (current == null || query.isBlank()) emptyList() else current.search(query, owner, limit = 50)
    }
    val me = settings?.me
    val needsConfirm = me != null && owner != me && confirmOtherFor != owner

    fun assign(targets: List<SearchHit>) {
        val active = shelf.trim()
        if (active.isEmpty()) {
            message = resources.getString(R.string.fill_need_shelf)
            return
        }
        val changes = targets.map { Change.Edit(it.owner, it.book, it.book.copy(shelf = active)) }
        val label = if (targets.size == 1) resources.getString(R.string.fill_done, targets[0].book.title, active)
        else resources.getString(R.string.undo_shelf, targets.size, active)
        store.submitAll(changes, label)
        message = label
        query = ""
        runCatching { searchFocus.requestFocus() }
    }

    SettingsSection(stringResource(R.string.fill_title), modifier) {
        Text(stringResource(R.string.fill_text), style = DeskTheme.type.body, color = colors.textSecondary)
        Spacer(Modifier.height(dimens.gapM))
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapM), verticalAlignment = Alignment.Bottom) {
            FormField(
                stringResource(R.string.fill_shelf), shelf, { shelf = it },
                modifier = Modifier.width(140.dp), capitalization = KeyboardCapitalization.Characters, monospace = true,
                onEnter = { runCatching { searchFocus.requestFocus() } },
            )
            FormField(
                stringResource(R.string.fill_search), query, { query = it },
                modifier = Modifier.weight(1f), focusRequester = searchFocus,
                onEnter = {
                    val first = hits.firstOrNull()
                    if (first != null) {
                        if (needsConfirm) pendingSingle = first else assign(listOf(first))
                    }
                },
            )
        }
        message?.let {
            Spacer(Modifier.height(dimens.gapS))
            Text(it, style = DeskTheme.type.body, color = colors.success)
        }
        if (hits.isNotEmpty()) {
            Spacer(Modifier.height(dimens.gapS))
            for (hit in hits.take(5)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(hit.book.title, style = DeskTheme.type.body.copy(fontFamily = AppFonts.Literata, fontWeight = FontWeight.Bold), color = colors.text, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(
                        hit.book.shelf.ifBlank { resources.getString(R.string.search_no_shelf) },
                        style = DeskTheme.type.label.copy(fontFamily = if (hit.book.shelf.isBlank()) DeskTheme.type.label.fontFamily else FontFamily.Monospace),
                        color = colors.textSecondary,
                    )
                }
            }
            if (hits.size > 1) {
                Spacer(Modifier.height(dimens.gapS))
                DeskButton(stringResource(R.string.fill_assign_all, hits.size, shelf.trim().ifEmpty { "…" }), { askAll = true })
            }
        }
    }

    val single = pendingSingle
    if (single != null || askAll) {
        val count = if (askAll) hits.size else 1
        val text = buildString {
            if (askAll) append(resources.getString(R.string.fill_assign_all_title, count, shelf.trim()))
            if (needsConfirm) {
                if (isNotEmpty()) append("\n\n")
                append(resources.getString(R.string.other_list_warning, LibraryTexts.listName(resources, owner)))
            }
        }
        ConfirmDialog(
            title = resources.getString(R.string.fill_title),
            text = text,
            actions = listOf(
                DialogAction(resources.getString(R.string.add_confirm), primary = true) {
                    confirmOtherFor = owner
                    assign(if (askAll) hits else listOfNotNull(single))
                },
                DialogAction(resources.getString(R.string.action_cancel)) {},
            ),
            onDismiss = {
                askAll = false
                pendingSingle = null
            },
        )
    }
    LaunchedEffect(owner) { message = null }
}
