package com.hesaplarimdijital.kitapbulucu.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.BookColumn
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.search.BookGroup
import com.hesaplarimdijital.kitapbulucu.search.BookGroups
import com.hesaplarimdijital.kitapbulucu.search.SearchHit
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.text.TurkishText
import com.hesaplarimdijital.kitapbulucu.ui.LibraryTexts
import desktopkit.components.DeskButton
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.SegmentedChoice
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.stateBackground
import desktopkit.shell.KeyCap
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode

/** Serif for book titles (Draft A); the kit's own serif slot arrives with K-01. */
private val Serif = FontFamily.Serif

/** The shelf code in a result: bold, accent, large enough to read at a glance (S-30: 20 sp). */
private val ShelfSize = 20.sp

/**
 * Type, see whose shelf the book is on: a search field, the All / Babam / Benim filter and one
 * row per book, with the library above the shelf on the right. A row opens in place to show the
 * details (copies, signed, publisher, year, ISBN, note). The cursor stays in the field: arrow
 * keys move the selection, Enter opens and closes it, Esc closes it or clears the search.
 */
@Composable
fun SearchScreen(store: LibraryStore, initialQuery: String = "", focusKey: Int = 0, openFirst: Boolean = false, onAdd: ((String) -> Unit)? = null) {
    val index by store.index.collectAsState()
    val states by store.states.collectAsState()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var filter by rememberSaveable { mutableStateOf<Owner?>(null) }
    val dimens = DeskTheme.dimens
    val colors = DeskTheme.colors
    val type = DeskTheme.type
    val resources = LocalContext.current.resources
    val pointer = DeskTheme.inputMode == InputMode.Pointer

    val currentIndex = index
    val groups = remember(currentIndex, query, filter) {
        if (currentIndex == null || query.isBlank()) emptyList() else BookGroups.group(currentIndex.search(query, filter))
    }
    val suggestions = remember(currentIndex, query, filter, groups) {
        if (currentIndex == null || query.isBlank() || groups.isNotEmpty()) emptyList() else BookGroups.group(currentIndex.suggest(query, filter)).take(3)
    }
    val shown = if (groups.isNotEmpty()) groups else suggestions
    var selected by remember(shown) { mutableIntStateOf(if (pointer && shown.isNotEmpty()) 0 else -1) }
    var opened by remember(shown) { mutableIntStateOf(if (openFirst && shown.isNotEmpty()) 0 else -1) }
    val list = rememberLazyListState()
    // the header items before the first result row
    val firstRowItem = if (groups.isEmpty() && suggestions.isNotEmpty()) 1 else 0
    LaunchedEffect(selected) { if (selected >= 0) list.animateScrollToItem((selected + firstRowItem).coerceAtLeast(0)) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding).padding(top = dimens.pagePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                SearchField(
                    query = query,
                    focusKey = focusKey,
                    onQueryChange = { query = it },
                    onKey = { key ->
                        when (key) {
                            Key.DirectionDown -> if (shown.isNotEmpty()) { selected = (selected + 1).coerceIn(0, shown.lastIndex); true } else false
                            Key.DirectionUp -> if (shown.isNotEmpty()) { selected = (selected - 1).coerceIn(0, shown.lastIndex); true } else false
                            Key.Enter, Key.NumPadEnter -> if (selected in shown.indices) { opened = if (opened == selected) -1 else selected; true } else false
                            Key.Escape -> when {
                                opened >= 0 -> { opened = -1; true }
                                query.isNotEmpty() -> { query = ""; true }
                                else -> false
                            }
                            else -> false
                        }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SegmentedChoice(
                        options = listOf<Pair<Owner?, String>>(
                            null to stringResource(R.string.search_filter_all),
                            Owner.DAD to LibraryTexts.ownerLabel(resources, Owner.DAD),
                            Owner.ME to LibraryTexts.ownerLabel(resources, Owner.ME),
                        ),
                        selected = filter,
                        onSelect = { filter = it },
                        // the filter never takes the cursor away from the search field
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                    Spacer(Modifier.weight(1f))
                    if (groups.isNotEmpty()) {
                        Text(
                            if (groups.size == 1) stringResource(R.string.search_found_one) else stringResource(R.string.search_found, groups.size),
                            style = type.label,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state = list, horizontalAlignment = Alignment.CenterHorizontally) {
            val rowModifier = Modifier.padding(horizontal = dimens.pagePadding).widthIn(max = 760.dp).fillMaxWidth()
            when {
                currentIndex == null -> item { Message(stringResource(R.string.search_loading), null, rowModifier) }
                query.isBlank() -> item {
                    val now = System.currentTimeMillis()
                    val updates = Owner.entries.filter { states.getValue(it).isLinked }
                        .joinToString("\n") { LibraryTexts.updated(resources, it, states.getValue(it), now) }
                    Message(stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_text) + if (updates.isEmpty()) "" else "\n\n$updates", rowModifier)
                }
                else -> {
                    if (groups.isEmpty()) {
                        item {
                            val title = when (filter) {
                                null -> stringResource(R.string.search_none_both)
                                Owner.DAD -> stringResource(R.string.search_none_dad)
                                Owner.ME -> stringResource(R.string.search_none_me)
                            }
                            Column(rowModifier.padding(top = dimens.gapL), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                                Text(title, style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp), color = colors.text)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                                    DeskButton(stringResource(R.string.search_add), { onAdd?.invoke(query) }, icon = DeskIcons.Plus, primary = true, enabled = onAdd != null)
                                    if (onAdd == null) Text(stringResource(R.string.search_add_later), style = type.labelSmall, color = colors.textTertiary)
                                }
                                if (suggestions.isNotEmpty()) {
                                    Spacer(Modifier.height(dimens.gapS))
                                    Text(stringResource(R.string.search_suggestions), style = type.label, color = colors.textSecondary)
                                }
                            }
                        }
                    }
                    itemsIndexed(shown, key = { position, group -> "$position:" + BookGroups.key(group.title, group.author) }) { position, group ->
                        ResultRow(
                            group = group,
                            selected = position == selected,
                            opened = position == opened,
                            modifier = rowModifier,
                            onClick = {
                                selected = position
                                opened = if (opened == position) -1 else position
                            },
                        )
                    }
                    if (pointer && shown.isNotEmpty()) item { KeyHints(rowModifier.padding(vertical = dimens.gapM)) }
                }
            }
            item { Spacer(Modifier.height(dimens.gapL)) }
        }
    }
}

@Composable
private fun SearchField(query: String, focusKey: Int, onQueryChange: (String) -> Unit, onKey: (Key) -> Boolean) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = DeskTheme.inputMode == InputMode.Touch
    val shape = RoundedCornerShape(dimens.radiusCard)
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focusKey) { runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (touch) 56.dp else 48.dp)
            .background(colors.surfaceRaised, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.borderStrong, shape)
            .padding(horizontal = dimens.gapM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DeskIcons.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(if (touch) 22.dp else 18.dp))
        Spacer(Modifier.width(dimens.gapM))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.search_hint), style = fieldStyle(touch), color = colors.textTertiary, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = fieldStyle(touch).copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && onKey(event.key) },
            )
        }
        if (query.isNotEmpty()) {
            if (touch) {
                DeskIconButton(DeskIcons.Close, stringResource(R.string.search_clear), { onQueryChange("") })
            } else {
                KeyCap("Esc")
                Spacer(Modifier.width(dimens.gapS))
                Text(stringResource(R.string.search_keys_clear), style = DeskTheme.type.labelSmall, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun fieldStyle(touch: Boolean): TextStyle = DeskTheme.type.body.copy(fontSize = if (touch) 18.sp else 16.sp)

/**
 * One book: title and author on the left, each library holding it on the right with the shelf
 * under the library's name. Opens in place to show the details.
 */
@Composable
private fun ResultRow(group: BookGroup, selected: Boolean, opened: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = DeskTheme.inputMode == InputMode.Touch
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val background = when {
        opened -> colors.surface
        selected -> colors.selection
        else -> stateBackground(state)
    }
    Column(
        modifier
            .background(background, shape)
            .then(if (opened) Modifier.border(1.dp, colors.border, shape) else Modifier)
            // the cursor stays in the search field; the row is reached with the arrow keys
            .focusProperties { canFocus = false }
            .deskClickable(interaction, onClick, role = Role.Button),
    ) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = if (touch) 64.dp else 52.dp).padding(horizontal = dimens.gapM, vertical = dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.title,
                    style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
                    color = if (selected && !opened) colors.onSelection else colors.text,
                    maxLines = if (opened) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group.author.isNotBlank()) {
                    Text(
                        TurkishText.upper(group.author),
                        style = DeskTheme.type.labelSmall.copy(letterSpacing = 0.4.sp),
                        color = colors.textSecondary,
                        maxLines = if (opened) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(dimens.gapM))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (hit in group.holdings) HoldingSummary(hit)
            }
        }
        if (opened) Details(group, Modifier.padding(start = dimens.gapM, end = dimens.gapM, bottom = dimens.gapM))
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (opened) colors.surface else colors.border))
    }
}

/** "Babam" (with "İmzalı" beside it) over "Raf C2" or "Raf yazılmamış". */
@Composable
private fun HoldingSummary(hit: SearchHit) {
    val colors = DeskTheme.colors
    val resources = LocalContext.current.resources
    val shelf = hit.book.shelf
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (hit.book.signed) Pill(stringResource(R.string.search_signed))
            Text(LibraryTexts.ownerLabel(resources, hit.owner), style = DeskTheme.type.label, color = colors.textSecondary)
        }
        if (shelf.isBlank()) {
            Text(
                stringResource(R.string.search_no_shelf),
                style = DeskTheme.type.labelSmall.copy(fontStyle = FontStyle.Italic),
                color = colors.textTertiary,
            )
        } else {
            val text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 14.sp, color = colors.textSecondary)) { append(resources.getString(R.string.search_shelf) + " ") }
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = ShelfSize, color = colors.accentText)) { append(shelf) }
            }
            Text(text, style = TextStyle(lineHeight = 24.sp), textAlign = TextAlign.End)
        }
    }
}

/** Everything the row leaves out, one holding after another. */
@Composable
private fun Details(group: BookGroup, modifier: Modifier) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val resources = LocalContext.current.resources
    val yes = stringResource(R.string.search_detail_yes)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
        for (hit in group.holdings) {
            val book = hit.book
            val shelf = book.shelf.ifBlank { resources.getString(R.string.search_no_shelf) }
            val rows = listOfNotNull(
                BookColumn.SHELF.header to shelf,
                BookColumn.COPIES.header to book.copies.toString(),
                (BookColumn.SIGNED.header to yes).takeIf { book.signed },
                (BookColumn.PUBLISHER.header to book.publisher).takeIf { book.publisher.isNotBlank() },
                (BookColumn.YEAR.header to book.year).takeIf { book.year.isNotBlank() },
                (BookColumn.ISBN.header to book.isbn).takeIf { book.isbn.isNotBlank() },
                (BookColumn.NOTE.header to book.note).takeIf { book.note.isNotBlank() },
            )
            Column(Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(dimens.radiusControl)).padding(dimens.gapM)) {
                Text(LibraryTexts.listName(resources, hit.owner), style = DeskTheme.type.title, color = colors.text)
                Spacer(Modifier.height(dimens.gapS))
                for ((label, value) in rows) {
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(label, style = DeskTheme.type.label, color = colors.textTertiary, modifier = Modifier.width(96.dp))
                        Text(
                            value,
                            style = if (label == BookColumn.SHELF.header && book.shelf.isNotBlank()) {
                                DeskTheme.type.body.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            } else {
                                DeskTheme.type.body
                            },
                            color = if (label == BookColumn.SHELF.header && book.shelf.isNotBlank()) colors.accentText else colors.text,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    val colors = DeskTheme.colors
    Text(
        text,
        style = DeskTheme.type.caption,
        color = colors.textSecondary,
        modifier = Modifier
            .border(1.dp, colors.borderStrong, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
private fun KeyHints(modifier: Modifier) {
    val colors = DeskTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyCap("↑")
        KeyCap("↓")
        Text(stringResource(R.string.search_keys_move), style = DeskTheme.type.labelSmall, color = colors.textTertiary)
        Spacer(Modifier.width(12.dp))
        KeyCap("Enter")
        Text(stringResource(R.string.search_keys_open), style = DeskTheme.type.labelSmall, color = colors.textTertiary)
        Spacer(Modifier.width(12.dp))
        KeyCap("Esc")
        Text(stringResource(R.string.search_keys_clear), style = DeskTheme.type.labelSmall, color = colors.textTertiary)
    }
}

@Composable
private fun Message(title: String, text: String?, modifier: Modifier) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    Column(modifier.padding(top = dimens.gapL * 2), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
        Text(title, style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp), color = colors.text)
        if (text != null) Text(text, style = DeskTheme.type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
    }
}
