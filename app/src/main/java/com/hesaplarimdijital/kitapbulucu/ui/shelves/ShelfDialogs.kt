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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.hesaplarimdijital.kitapbulucu.ui.AppSheet
import com.hesaplarimdijital.kitapbulucu.ui.isCompactLayout
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
    if (isCompactLayout()) {
        // phone: a bottom sheet, the fields over the full width and a wide "Kaydet"
        AppSheet(onDismiss = onDismiss, title = title) { close ->
            val saveAndClose = {
                val found = Shelves.checkName(code, existing, renaming)
                problem = found
                if (found == null) {
                    val cleanCode = Shelves.clean(code)
                    val cleanNote = Shelves.clean(note)
                    close { onSave(cleanCode, cleanNote) }
                }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = dimens.pagePadding).padding(top = dimens.gapS, bottom = dimens.gapS),
                verticalArrangement = Arrangement.spacedBy(dimens.gapM),
            ) {
                if (text != null) Text(text, style = DeskTheme.type.body, color = colors.textSecondary)
                FormField(
                    stringResource(R.string.shelves_code), code, { code = it.take(MAX_CODE); problem = null },
                    modifier = Modifier.fillMaxWidth(), focusRequester = focus,
                    capitalization = KeyboardCapitalization.Characters, monospace = true, error = problem != null, onEnter = saveAndClose,
                )
                NameProblemText(problem, code)
                FormField(stringResource(R.string.shelves_note), note, { note = it.take(MAX_NOTE) }, modifier = Modifier.fillMaxWidth(), onEnter = saveAndClose)
                DeskButton(stringResource(R.string.shelves_save), saveAndClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), icon = DeskIcons.Check, primary = true)
            }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        return
    }
    ShelfDialogFrame(onDismiss) {
        Text(title, style = DeskTheme.type.headline, color = colors.text)
        if (text != null) Text(text, style = DeskTheme.type.body, color = colors.textSecondary)
        FormField(
            stringResource(R.string.shelves_code), code, { code = it.take(MAX_CODE); problem = null },
            modifier = Modifier.fillMaxWidth(), focusRequester = focus,
            capitalization = KeyboardCapitalization.Characters, monospace = true, error = problem != null, onEnter = save,
        )
        NameProblemText(problem, code)
        FormField(stringResource(R.string.shelves_note), note, { note = it.take(MAX_NOTE) }, modifier = Modifier.fillMaxWidth(), onEnter = save)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            DeskButton(stringResource(R.string.shelves_save), save, icon = DeskIcons.Check, primary = true)
            DeskButton(stringResource(R.string.action_cancel), onDismiss)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun NameProblemText(problem: Shelves.NameProblem?, code: String) {
    val colors = DeskTheme.colors
    when (problem) {
        Shelves.NameProblem.EMPTY -> Text(stringResource(R.string.shelves_code_empty), style = DeskTheme.type.body, color = colors.danger)
        Shelves.NameProblem.TAKEN -> Text(stringResource(R.string.shelves_code_taken, Shelves.clean(code)), style = DeskTheme.type.body, color = colors.danger)
        null -> Unit
    }
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
    if (isCompactLayout()) {
        PhonePicker(title, subtitle, shelves, filter, { filter = it.take(MAX_CODE) }, shown, currentKey, onPick, onNew, onDismiss)
        return
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

/**
 * The phone's "Rafa koy…": a bottom sheet with the filter on top, "Yeni raf" as the first row and
 * the shelves below as large rows; the list scrolls. The filter does not take the cursor on its own:
 * the keyboard would cover half of the shelves. The sheet's window does not use the platform's
 * dialog width: a dialog sized by the platform whose content fills the width (the old frame on a
 * phone) never settled under Robolectric ("Compose did not get idle"), with or without focus.
 */
@Composable
private fun PhonePicker(
    title: String,
    subtitle: String,
    shelves: List<ShelfGroup>,
    filter: String,
    onFilter: (String) -> Unit,
    shown: List<ShelfGroup>,
    currentKey: String?,
    onPick: (String) -> Unit,
    onNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val typed = Shelves.clean(filter)
    val exact = typed.isNotEmpty() && shelves.any { Shelves.key(it.shelf) == Shelves.key(typed) }
    AppSheet(onDismiss = onDismiss, title = title) { close ->
        Text(
            subtitle,
            style = DeskTheme.type.body,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = dimens.pagePadding),
        )
        FormField(
            stringResource(R.string.picker_filter), filter, onFilter,
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapS),
            capitalization = KeyboardCapitalization.Characters, monospace = true,
            onEnter = {
                val chosen = shown.firstOrNull()
                when {
                    chosen != null -> close { onPick(chosen.shelf) }
                    filter.isNotBlank() -> close { onNew(filter) }
                    else -> Unit
                }
            },
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (!exact) {
                PhonePickerRow(
                    code = null,
                    label = if (typed.isEmpty()) stringResource(R.string.touchlist_picker_new) else stringResource(R.string.touchlist_picker_new_named, typed),
                    count = null,
                ) { close { onNew(filter) } }
            }
            when {
                shelves.isEmpty() -> PickerNote(stringResource(R.string.picker_none))
                shown.isEmpty() -> PickerNote(stringResource(R.string.touchlist_picker_no_match))
                else -> shown.forEach { group ->
                    PhonePickerRow(
                        code = group.shelf,
                        label = group.note,
                        count = if (Shelves.key(group.shelf) == currentKey) stringResource(R.string.picker_current) else shelfCount(group),
                        current = Shelves.key(group.shelf) == currentKey,
                    ) { close { onPick(group.shelf) } }
                }
            }
        }
    }
}

@Composable
private fun PickerNote(text: String) {
    Text(
        text,
        style = DeskTheme.type.body,
        color = DeskTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = DeskTheme.dimens.pagePadding, vertical = DeskTheme.dimens.gapM),
    )
}

/** A shelf in the phone picker (code, description, count), or with no [code] the "Yeni raf" row. */
@Composable
private fun PhonePickerRow(code: String?, label: String, count: String?, current: Boolean = false, onClick: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(stateBackground(state, selected = current))
            .focusRing(state, RoundedCornerShape(0.dp))
            .deskClickable(interaction, onClick, role = Role.Button)
            .padding(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (code == null) {
            Icon(DeskIcons.Plus, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(dimens.gapL))
            Text(label, style = DeskTheme.type.bodyStrong, color = colors.accentText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        } else {
            val main = if (current) colors.onSelection else colors.accentText
            val second = if (current) colors.onSelection else colors.textSecondary
            Text(
                code,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
                color = main,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(min = 48.dp, max = 140.dp),
            )
            Spacer(Modifier.width(dimens.gapL))
            Text(label, style = DeskTheme.type.body, color = second, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (count != null) {
                Spacer(Modifier.width(dimens.gapS))
                Text(count, style = DeskTheme.type.label, color = second, maxLines = 1)
            }
        }
    }
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
