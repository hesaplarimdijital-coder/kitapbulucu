package com.hesaplarimdijital.kitapbulucu.ui.shelves

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.ShelfGroup
import com.hesaplarimdijital.kitapbulucu.model.Shelves
import com.hesaplarimdijital.kitapbulucu.text.TurkishText
import com.hesaplarimdijital.kitapbulucu.ui.FormField
import desktopkit.components.DeskButton
import desktopkit.components.DeskIcons
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.stateBackground
import desktopkit.theme.DeskTheme

private const val MAX_CODE = 16
private const val MAX_NOTE = 60

/** The frame of the shelf dialogs, in the look of [com.hesaplarimdijital.kitapbulucu.ui.ConfirmDialog]; Esc cancels. */
@Composable
private fun ShelfDialogFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .padding(dimens.gapL),
            verticalArrangement = Arrangement.spacedBy(dimens.gapM),
            content = content,
        )
    }
}

/**
 * A new shelf or a new name for one (S-33): code and an optional description. Enter saves, Esc
 * cancels. [existing] are the codes the list has now; [renaming] is the shelf being renamed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShelfFormDialog(
    title: String,
    existing: List<String>,
    onSave: (code: String, note: String) -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    initialCode: String = "",
    initialNote: String = "",
    renaming: String? = null,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    var code by remember { mutableStateOf(initialCode.take(MAX_CODE)) }
    var note by remember { mutableStateOf(initialNote.take(MAX_NOTE)) }
    var problem by remember { mutableStateOf<Shelves.NameProblem?>(null) }
    val focus = remember { FocusRequester() }
    val save = {
        val found = Shelves.checkName(code, existing, renaming)
        problem = found
        if (found == null) {
            onDismiss()
            onSave(Shelves.clean(code), Shelves.clean(note))
        }
    }
    ShelfDialogFrame(onDismiss) {
        Text(title, style = DeskTheme.type.headline, color = colors.text)
        if (text != null) Text(text, style = DeskTheme.type.body, color = colors.textSecondary)
        FormField(
            stringResource(R.string.shelves_code), code, { code = it.take(MAX_CODE); problem = null },
            modifier = Modifier.fillMaxWidth(), focusRequester = focus,
            capitalization = KeyboardCapitalization.Characters, monospace = true, error = problem != null, onEnter = save,
        )
        when (problem) {
            Shelves.NameProblem.EMPTY -> Text(stringResource(R.string.shelves_code_empty), style = DeskTheme.type.body, color = colors.danger)
            Shelves.NameProblem.TAKEN -> Text(stringResource(R.string.shelves_code_taken, Shelves.clean(code)), style = DeskTheme.type.body, color = colors.danger)
            null -> Unit
        }
        FormField(stringResource(R.string.shelves_note), note, { note = it.take(MAX_NOTE) }, modifier = Modifier.fillMaxWidth(), onEnter = save)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            DeskButton(stringResource(R.string.shelves_save), save, icon = DeskIcons.Check, primary = true)
            DeskButton(stringResource(R.string.action_cancel), onDismiss)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/**
 * "Rafa koy…" (S-35): the list's shelves with a filter. ↑↓ choose, Enter puts the book(s) on the
 * chosen shelf; with no match, Enter offers the typed code as a new shelf. [current] is the
 * shelf the book is on now (one book only).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShelfPickerDialog(
    title: String,
    subtitle: String,
    shelves: List<ShelfGroup>,
    current: String?,
    onPick: (String) -> Unit,
    onNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    var filter by remember { mutableStateOf("") }
    var active by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val shown = remember(shelves, filter) { filterShelves(shelves, filter) }
    val currentKey = current?.let(Shelves::key)?.takeIf { it.isNotEmpty() }
    val pick = { shelf: String ->
        onDismiss()
        onPick(shelf)
    }
    ShelfDialogFrame(onDismiss) {
        Text(title, style = DeskTheme.type.headline, color = colors.text)
        Text(subtitle, style = DeskTheme.type.body, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        FormField(
            stringResource(R.string.picker_filter), filter, { filter = it.take(MAX_CODE); active = 0 },
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> { if (shown.isNotEmpty()) active = (active + 1).coerceAtMost(shown.size - 1); true }
                    Key.DirectionUp -> { active = (active - 1).coerceAtLeast(0); true }
                    else -> false
                }
            },
            focusRequester = focus, capitalization = KeyboardCapitalization.Characters, monospace = true,
            onEnter = {
                val chosen = shown.getOrNull(active)
                when {
                    chosen != null -> pick(chosen.shelf)
                    filter.isNotBlank() -> onNew(filter)
                    else -> Unit
                }
            },
        )
        when {
            shelves.isEmpty() -> Text(stringResource(R.string.picker_none), style = DeskTheme.type.body, color = colors.textSecondary)
            shown.isEmpty() -> Text(stringResource(R.string.picker_no_match, Shelves.clean(filter)), style = DeskTheme.type.body, color = colors.textSecondary)
            // a plain scrolling column: a list has tens of shelves, not thousands
            else -> Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(scroll)) {
                shown.forEachIndexed { index, group ->
                    PickerRow(group, highlighted = index == active, isCurrent = Shelves.key(group.shelf) == currentKey) { pick(group.shelf) }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            DeskButton(stringResource(R.string.shelves_new), { onNew(filter) }, icon = DeskIcons.Plus)
            DeskButton(stringResource(R.string.action_cancel), onDismiss)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Shelves whose code contains the typed text (b3 finds B3, B30), or whose description does. */
internal fun filterShelves(shelves: List<ShelfGroup>, filter: String): List<ShelfGroup> {
    val key = Shelves.key(filter)
    if (key.isEmpty()) return shelves
    val words = TurkishText.upper(TurkishText.squeeze(filter))
    return shelves.filter { Shelves.key(it.shelf).contains(key) || TurkishText.upper(it.note).contains(words) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickerRow(group: ShelfGroup, highlighted: Boolean, isCurrent: Boolean, onClick: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val relocation = remember { BringIntoViewRequester() }
    // the row chosen with ↑↓ scrolls into view
    LaunchedEffect(highlighted) { if (highlighted) runCatching { relocation.bringIntoView() } }
    val main = if (highlighted) colors.onSelection else colors.accentText
    val second = if (highlighted) colors.onSelection else colors.textSecondary
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(relocation)
            .defaultMinSize(minHeight = dimens.minTarget.coerceAtLeast(40.dp))
            .focusRing(state, shape)
            .background(stateBackground(state, selected = highlighted), shape)
            .deskClickable(interaction, onClick, role = Role.Button)
            .padding(horizontal = dimens.gapM, vertical = dimens.gapXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(group.shelf, style = DeskTheme.type.body.copy(fontFamily = DeskTheme.type.mono.fontFamily, fontWeight = FontWeight.Bold), color = main, maxLines = 1)
        Spacer(Modifier.width(dimens.gapM))
        Box(Modifier.weight(1f)) {
            if (group.note.isNotEmpty()) Text(group.note, style = DeskTheme.type.label, color = second, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(dimens.gapS))
        Text(
            if (isCurrent) stringResource(R.string.picker_current) else shelfCount(group),
            style = DeskTheme.type.label,
            color = second,
            maxLines = 1,
        )
    }
}

/** "3 kitap", or "Boş" for a created shelf without books. */
@Composable
internal fun shelfCount(group: ShelfGroup): String =
    if (group.books.isEmpty()) stringResource(R.string.shelves_empty) else stringResource(R.string.shelves_count, group.books.size)
