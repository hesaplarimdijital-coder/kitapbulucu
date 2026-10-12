package com.hesaplarimdijital.kitapbulucu.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hesaplarimdijital.kitapbulucu.R
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
import desktopkit.components.focusRing
import desktopkit.components.stateBackground
import desktopkit.shell.KeyCap
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode

/** Serif for book titles and the shelf line (Draft A); the kit's own serif slot arrives with K-01. */
private val Serif = FontFamily.Serif

/** The shelf line on the result card: at least 32 sp by the project's rule. */
private val ShelfSize = 36.sp

/**
 * Type, see whose shelf the book is on (Draft A "Fiş"): a search field, the All / Babam / Benim
 * filter, the best match as a catalogue card with "Babam · Raf C2" in large type, then the other
 * matches. Nothing found: the closest three books and the way to add it.
 */
@Composable
fun SearchScreen(store: LibraryStore, initialQuery: String = "", onAdd: ((String) -> Unit)? = null) {
    val index by store.index.collectAsState()
    val states by store.states.collectAsState()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var filter by rememberSaveable { mutableStateOf<Owner?>(null) }
    val dimens = DeskTheme.dimens
    val colors = DeskTheme.colors
    val type = DeskTheme.type
    val resources = LocalContext.current.resources

    val currentIndex = index
    val groups = remember(currentIndex, query, filter) {
        if (currentIndex == null || query.isBlank()) emptyList() else BookGroups.group(currentIndex.search(query, filter))
    }
    val suggestions = remember(currentIndex, query, filter, groups) {
        if (currentIndex == null || query.isBlank() || groups.isNotEmpty()) emptyList() else BookGroups.group(currentIndex.suggest(query, filter))
    }
    var selected by remember(groups) { mutableIntStateOf(0) }
    val hero = groups.getOrNull(selected)
    val pointer = DeskTheme.inputMode == InputMode.Pointer

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding).padding(top = dimens.pagePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onMove = { step -> if (groups.isNotEmpty()) selected = (selected + step).coerceIn(0, groups.lastIndex) },
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
        LazyColumn(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val rowModifier = Modifier.padding(horizontal = dimens.pagePadding).widthIn(max = 760.dp).fillMaxWidth()
            when {
                currentIndex == null -> item { Message(stringResource(R.string.search_loading), null, rowModifier) }
                query.isBlank() -> item {
                    val now = System.currentTimeMillis()
                    val updates = Owner.entries.filter { states.getValue(it).isLinked }
                        .joinToString("\n") { LibraryTexts.updated(resources, it, states.getValue(it), now) }
                    Message(stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_text) + if (updates.isEmpty()) "" else "\n\n$updates", rowModifier)
                }
                groups.isEmpty() -> {
                    item {
                        val title = when (filter) {
                            null -> stringResource(R.string.search_none_both)
                            Owner.DAD -> stringResource(R.string.search_none_dad)
                            Owner.ME -> stringResource(R.string.search_none_me)
                        }
                        Column(rowModifier.padding(top = dimens.gapL), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                            Text(title, style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp), color = colors.text)
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
                    itemsIndexed(suggestions.take(3)) { _, group ->
                        ResultRow(group, rowModifier, onClick = { query = group.title })
                    }
                }
                else -> {
                    if (hero != null) item { HeroCard(hero, rowModifier.padding(vertical = dimens.gapS)) }
                    itemsIndexed(groups) { position, group ->
                        if (position != selected) ResultRow(group, rowModifier, onClick = { selected = position })
                    }
                    if (pointer) item { KeyHints(rowModifier.padding(vertical = dimens.gapM)) }
                }
            }
            item { Spacer(Modifier.height(dimens.gapL)) }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onMove: (Int) -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = DeskTheme.inputMode == InputMode.Touch
    val shape = RoundedCornerShape(dimens.radiusCard)
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (touch) 60.dp else 52.dp)
            .background(colors.surfaceRaised, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.borderStrong, shape)
            .padding(horizontal = dimens.gapM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DeskIcons.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(if (touch) 24.dp else 20.dp))
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
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Escape -> if (query.isNotEmpty()) { onQueryChange(""); true } else false
                            Key.DirectionDown -> { onMove(1); true }
                            Key.DirectionUp -> { onMove(-1); true }
                            else -> false
                        }
                    },
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
private fun fieldStyle(touch: Boolean): TextStyle = DeskTheme.type.body.copy(fontSize = if (touch) 20.sp else 18.sp)

/** The best match: title, author, and one large line per library row that holds it. */
@Composable
private fun HeroCard(group: BookGroup, modifier: Modifier) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    val touch = DeskTheme.inputMode == InputMode.Touch
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape),
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(colors.accent))
        Column(Modifier.padding(horizontal = if (touch) 20.dp else 28.dp, vertical = 20.dp).weight(1f)) {
            Text(
                group.title,
                style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = if (touch) 28.sp else 32.sp, lineHeight = if (touch) 34.sp else 38.sp),
                color = colors.text,
            )
            if (group.author.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(TurkishText.upper(group.author), style = DeskTheme.type.label.copy(letterSpacing = 0.6.sp), color = colors.textSecondary)
            }
            for (hit in group.holdings) {
                Spacer(Modifier.height(dimens.gapM))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                Spacer(Modifier.height(dimens.gapM))
                HoldingLine(hit)
            }
        }
    }
}

/** "Babam · Raf C2" at shelf size, with copies and signed marks beside it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HoldingLine(hit: SearchHit) {
    val colors = DeskTheme.colors
    val resources = LocalContext.current.resources
    val shelf = hit.book.shelf
    val noShelf = stringResource(R.string.search_no_shelf)
    val shelfWord = stringResource(R.string.search_shelf)
    val line = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, color = colors.text)) { append(LibraryTexts.ownerLabel(resources, hit.owner)) }
        withStyle(SpanStyle(fontFamily = Serif, color = colors.textSecondary)) { append(" · ") }
        if (shelf.isBlank()) {
            withStyle(SpanStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, color = colors.textSecondary)) { append(noShelf) }
        } else {
            withStyle(SpanStyle(fontFamily = Serif, color = colors.textSecondary)) { append("$shelfWord ") }
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colors.accentText)) { append(shelf) }
        }
    }
    FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(line, style = TextStyle(fontSize = ShelfSize, lineHeight = 44.sp), modifier = Modifier.align(Alignment.CenterVertically))
        Marks(hit, Modifier.align(Alignment.CenterVertically))
    }
}

/** "2 adet" and "İmzalı" pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Marks(hit: SearchHit, modifier: Modifier = Modifier) {
    val copies = hit.book.copies
    if (copies <= 1 && !hit.book.signed) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (copies > 1) Pill(stringResource(R.string.search_copies, copies))
        if (hit.book.signed) Pill(stringResource(R.string.search_signed))
    }
}

@Composable
private fun Pill(text: String) {
    val colors = DeskTheme.colors
    Text(
        text,
        style = DeskTheme.type.labelSmall,
        color = colors.textSecondary,
        modifier = Modifier
            .border(1.dp, colors.borderStrong, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Another match: title and author on the left, where it is on the right. Clicking makes it the card. */
@Composable
private fun ResultRow(group: BookGroup, modifier: Modifier, onClick: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    BoxWithConstraints(
        modifier
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .deskClickable(interaction, onClick, role = Role.Button),
    ) {
        val narrow = maxWidth < 520.dp
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = dimens.gapM, vertical = dimens.gapM), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        group.title,
                        style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = if (narrow) 17.sp else 16.sp),
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (group.author.isNotBlank()) {
                        Text(TurkishText.upper(group.author), style = DeskTheme.type.labelSmall.copy(letterSpacing = 0.4.sp), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(dimens.gapM))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (hit in group.holdings) HoldingSummary(hit)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        }
    }
}

/** "İmzalı  Babam  Raf D4" in a result row. */
@Composable
private fun HoldingSummary(hit: SearchHit) {
    val colors = DeskTheme.colors
    val resources = LocalContext.current.resources
    val shelf = hit.book.shelf
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Marks(hit)
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.textSecondary)) { append(LibraryTexts.ownerLabel(resources, hit.owner)) }
            append("  ")
            if (shelf.isBlank()) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = colors.textSecondary)) { append(resources.getString(R.string.search_no_shelf)) }
            } else {
                withStyle(SpanStyle(color = colors.textSecondary)) { append(resources.getString(R.string.search_shelf) + " ") }
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colors.accentText)) { append(shelf) }
            }
        }
        Text(text, style = DeskTheme.type.body.copy(fontFamily = Serif, fontSize = 15.sp))
    }
}

@Composable
private fun KeyHints(modifier: Modifier) {
    val colors = DeskTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyCap("↑")
        KeyCap("↓")
        Text(stringResource(R.string.search_keys_move), style = DeskTheme.type.labelSmall, color = colors.textTertiary)
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
        Text(title, style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp), color = colors.text)
        if (text != null) Text(text, style = DeskTheme.type.body, color = colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
