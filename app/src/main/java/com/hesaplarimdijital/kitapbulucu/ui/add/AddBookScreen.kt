package com.hesaplarimdijital.kitapbulucu.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.model.Shelves
import com.hesaplarimdijital.kitapbulucu.model.Suggestions
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.Change
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.ui.AdaptiveButton
import com.hesaplarimdijital.kitapbulucu.ui.PhoneForm
import com.hesaplarimdijital.kitapbulucu.ui.BottomPrimaryButton
import com.hesaplarimdijital.kitapbulucu.ui.BottomActions
import com.hesaplarimdijital.kitapbulucu.ui.FormField
import com.hesaplarimdijital.kitapbulucu.ui.PhoneTextButton
import com.hesaplarimdijital.kitapbulucu.ui.SuggestField
import com.hesaplarimdijital.kitapbulucu.ui.isPhone
import com.hesaplarimdijital.kitapbulucu.ui.LibraryTexts
import com.hesaplarimdijital.kitapbulucu.ui.NoticeCard
import com.hesaplarimdijital.kitapbulucu.ui.shelves.ShelfField
import desktopkit.components.DeskButton
import desktopkit.components.DeskIcons
import desktopkit.components.SegmentedChoice
import desktopkit.screens.PageHeader
import desktopkit.shell.KeyCap
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The add form (S-17): title, author, whose list, shelf, then the optional details. Enter saves;
 * the form stays open with the same list and shelf and the cursor back in the title, so a whole
 * shelf can be typed in one go. A book already in that list and a book for the other person's
 * list are confirmed first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBookScreen(
    store: LibraryStore,
    settings: AppSettings?,
    initialTitle: String = "",
    focusKey: Int = 0,
    /** When set, the form edits this book instead of adding one; [actions] saves it. */
    editing: EditTarget? = null,
    actions: com.hesaplarimdijital.kitapbulucu.ui.BookActions? = null,
    onEdited: () -> Unit = {},
) {
    val states by store.states.collectAsState()
    val catalog by store.shelfCatalog.entries.collectAsState()
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val type = DeskTheme.type
    val dimens = DeskTheme.dimens
    val me = settings?.me
    var fields by remember(initialTitle, editing) { mutableStateOf(if (editing != null) AddBookFields.of(editing.owner, editing.book) else AddBookFields(title = initialTitle, owner = me ?: Owner.DAD)) }
    var check by remember { mutableStateOf<AddBookForm.Check?>(null) }
    var done by rememberSaveable { mutableStateOf<String?>(null) }
    var showMore by rememberSaveable(editing != null) { mutableStateOf(editing != null) }
    val titleFocus = remember { FocusRequester() }
    // authors and publishers of both lists, collected again only when a list changes, and not on the main thread
    val tables = states.values.mapNotNull { it.table }
    val authorSuggestions by produceState(Suggestions.EMPTY, tables) { value = withContext(Dispatchers.Default) { Suggestions.authors(tables) } }
    val publisherSuggestions by produceState(Suggestions.EMPTY, tables) { value = withContext(Dispatchers.Default) { Suggestions.publishers(tables) } }
    val ownerBooks = states.getValue(fields.owner).table?.books.orEmpty()
    val ownerSaved = catalog[fields.owner].orEmpty()
    val shelves = remember(ownerBooks, ownerSaved) { Shelves.overview(ownerBooks, ownerSaved).filterNot { it.isUnshelved } }
    LaunchedEffect(focusKey, initialTitle) { runCatching { titleFocus.requestFocus() } }

    fun submit(confirmedOther: Boolean = false, confirmedDuplicate: Boolean = false) {
        if (editing != null) {
            val result = AddBookForm.edited(fields, editing.book)
            check = result
            if (result is AddBookForm.Check.Ready && actions != null) {
                actions.saveEdit(editing.owner, editing.book, fields.owner, result.book) {
                    done = resources.getString(R.string.edit_done, result.book.title)
                    check = null
                    onEdited()
                }
            }
            return
        }
        val result = AddBookForm.check(fields, me, states.getValue(fields.owner).table, confirmedOther, confirmedDuplicate)
        check = result
        if (result is AddBookForm.Check.Ready) {
            store.submit(Change.Add(fields.owner, result.book), resources.getString(R.string.undo_added, result.book.title))
            done = resources.getString(R.string.add_done, result.book.title, toList(resources, fields.owner))
            fields = fields.next()
            check = null
            runCatching { titleFocus.requestFocus() }
        }
    }

    val phone = isPhone()

    /** The checks' answers: a missing title, a bad count, a book that is already there, the other person's list. */
    val checkContent: @Composable () -> Unit = {
        when (val current = check) {
            AddBookForm.Check.NeedTitle -> Text(stringResource(R.string.add_need_title), style = type.body, color = colors.danger)
            AddBookForm.Check.BadCopies -> Text(stringResource(R.string.add_bad_copies), style = type.body, color = colors.danger)
            is AddBookForm.Check.Exists -> NoticeCard(
                title = current.existing.title,
                text = stringResource(
                    R.string.add_exists,
                    inList(resources, fields.owner),
                    if (current.existing.shelf.isBlank()) stringResource(R.string.add_exists_no_shelf) else stringResource(R.string.add_exists_shelf, current.existing.shelf),
                ),
                tone = colors.warning,
            ) {
                AdaptiveButton(stringResource(R.string.add_one_more), { submit(confirmedDuplicate = true) }, primary = true)
                AdaptiveButton(stringResource(R.string.add_cancel), { check = null })
            }
            is AddBookForm.Check.OtherList -> NoticeCard(
                title = current.book.title,
                text = stringResource(R.string.add_other_confirm, toList(resources, fields.owner)),
                tone = colors.warning,
            ) {
                AdaptiveButton(stringResource(R.string.add_confirm), { submit(confirmedOther = true, confirmedDuplicate = true) }, primary = true)
                AdaptiveButton(stringResource(R.string.add_cancel), { check = null })
            }
            else -> Unit
        }
    }
    val clear = {
        fields = AddBookFields(owner = fields.owner)
        check = null
        done = null
        runCatching { titleFocus.requestFocus() }
        Unit
    }

    if (phone) {
        // phone: fields one under the other; Kaydet is pinned at the bottom, and while the keyboard
        // is open it ends the scrolling form instead of riding on the keyboard
        PhoneForm(
            bar = {
                BottomActions(
                    primary = { modifier -> BottomPrimaryButton(stringResource(R.string.add_save), { submit() }, modifier, icon = DeskIcons.Check) },
                    secondary = {
                        if (editing != null) {
                            PhoneTextButton(stringResource(R.string.action_cancel), onEdited)
                        } else {
                            PhoneTextButton(stringResource(R.string.add_clear), clear)
                        }
                    },
                )
            },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapM),
                verticalArrangement = Arrangement.spacedBy(dimens.gapM),
            ) {
                done?.let { Text(it, style = type.body, color = colors.success) }
                FormField(
                    stringResource(R.string.add_field_title), fields.title, { fields = fields.copy(title = it); done = null },
                    focusRequester = titleFocus, error = check == AddBookForm.Check.NeedTitle, onEnter = { submit() },
                )
                SuggestField(stringResource(R.string.add_field_author), fields.author, { fields = fields.copy(author = it) }, authorSuggestions, modifier = Modifier.testTag("field-author"), onEnter = { submit() })
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.add_field_owner), style = type.label, color = colors.textSecondary)
                    SegmentedChoice(
                        options = listOf(Owner.DAD to LibraryTexts.ownerLabel(resources, Owner.DAD), Owner.ME to LibraryTexts.ownerLabel(resources, Owner.ME)),
                        selected = fields.owner,
                        onSelect = { fields = fields.copy(owner = it); check = null },
                    )
                }
                ShelfField(
                    stringResource(R.string.add_field_shelf), fields.shelf, { fields = fields.copy(shelf = it) },
                    shelves = shelves,
                    modifier = Modifier.fillMaxWidth(), onEnter = { submit() },
                )
                PhoneTextButton(
                    stringResource(R.string.add_more),
                    { showMore = !showMore },
                    icon = if (showMore) DeskIcons.ChevronUp else DeskIcons.ChevronDown,
                    primary = true,
                )
                AnimatedVisibility(showMore, enter = expandVertically(tween(200)) + fadeIn(tween(200)), exit = shrinkVertically(tween(150)) + fadeOut(tween(150))) {
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                        FormField(
                            stringResource(R.string.add_field_copies), fields.copies, { fields = fields.copy(copies = it.filter(Char::isDigit).take(4)) },
                            keyboardType = KeyboardType.Number, error = check == AddBookForm.Check.BadCopies, onEnter = { submit() },
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.add_field_signed), style = type.label, color = colors.textSecondary)
                            SegmentedChoice(
                                options = listOf(false to stringResource(R.string.add_no), true to stringResource(R.string.add_yes)),
                                selected = fields.signed,
                                onSelect = { fields = fields.copy(signed = it) },
                            )
                        }
                        SuggestField(stringResource(R.string.add_field_publisher), fields.publisher, { fields = fields.copy(publisher = it) }, publisherSuggestions, modifier = Modifier.testTag("field-publisher"), onEnter = { submit() })
                        FormField(stringResource(R.string.add_field_year), fields.year, { fields = fields.copy(year = it) }, keyboardType = KeyboardType.Number, onEnter = { submit() })
                        FormField(stringResource(R.string.add_field_isbn), fields.isbn, { fields = fields.copy(isbn = it) }, keyboardType = KeyboardType.Number, monospace = true, onEnter = { submit() })
                        FormField(stringResource(R.string.add_field_note), fields.note, { fields = fields.copy(note = it) }, onEnter = { submit() })
                    }
                }
                checkContent()
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(stringResource(if (editing != null) R.string.edit_title else R.string.add_title))
        Column(
            Modifier.padding(horizontal = dimens.pagePadding).widthIn(max = 760.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimens.gapM),
        ) {
            done?.let { Text(it, style = type.body, color = colors.success) }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 560.dp
                Column(verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                    FormField(
                        stringResource(R.string.add_field_title), fields.title, { fields = fields.copy(title = it); done = null },
                        focusRequester = titleFocus, error = check == AddBookForm.Check.NeedTitle, onEnter = { submit() },
                    )
                    SuggestField(stringResource(R.string.add_field_author), fields.author, { fields = fields.copy(author = it) }, authorSuggestions, modifier = Modifier.testTag("field-author"), onEnter = { submit() })
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapM), verticalAlignment = Alignment.Bottom) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.add_field_owner), style = type.label, color = colors.textSecondary)
                            SegmentedChoice(
                                options = listOf(Owner.DAD to LibraryTexts.ownerLabel(resources, Owner.DAD), Owner.ME to LibraryTexts.ownerLabel(resources, Owner.ME)),
                                selected = fields.owner,
                                onSelect = { fields = fields.copy(owner = it); check = null },
                            )
                        }
                        ShelfField(
                            stringResource(R.string.add_field_shelf), fields.shelf, { fields = fields.copy(shelf = it) },
                            shelves = shelves,
                            modifier = Modifier.width(if (wide) 220.dp else 170.dp), onEnter = { submit() },
                        )
                    }
                    DeskButton(
                        stringResource(R.string.add_more),
                        { showMore = !showMore },
                        icon = if (showMore) DeskIcons.ChevronUp else DeskIcons.ChevronDown,
                    )
                    if (showMore) {
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapM), verticalAlignment = Alignment.Bottom) {
                            FormField(
                                stringResource(R.string.add_field_copies), fields.copies, { fields = fields.copy(copies = it.filter(Char::isDigit).take(4)) },
                                modifier = Modifier.width(96.dp), keyboardType = KeyboardType.Number, error = check == AddBookForm.Check.BadCopies, onEnter = { submit() },
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.add_field_signed), style = type.label, color = colors.textSecondary)
                                SegmentedChoice(
                                    options = listOf(false to stringResource(R.string.add_no), true to stringResource(R.string.add_yes)),
                                    selected = fields.signed,
                                    onSelect = { fields = fields.copy(signed = it) },
                                )
                            }
                        }
                        SuggestField(stringResource(R.string.add_field_publisher), fields.publisher, { fields = fields.copy(publisher = it) }, publisherSuggestions, modifier = Modifier.testTag("field-publisher"), onEnter = { submit() })
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                            FormField(stringResource(R.string.add_field_year), fields.year, { fields = fields.copy(year = it) }, modifier = Modifier.width(120.dp), keyboardType = KeyboardType.Number, onEnter = { submit() })
                            FormField(stringResource(R.string.add_field_isbn), fields.isbn, { fields = fields.copy(isbn = it) }, modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number, monospace = true, onEnter = { submit() })
                        }
                        FormField(stringResource(R.string.add_field_note), fields.note, { fields = fields.copy(note = it) }, onEnter = { submit() })
                    }
                }
            }

            checkContent()

            FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                DeskButton(stringResource(R.string.add_save), { submit() }, icon = DeskIcons.Check, primary = true)
                if (editing != null) {
                    DeskButton(stringResource(R.string.action_cancel), onEdited)
                } else {
                    DeskButton(stringResource(R.string.add_clear), clear)
                }
            }
            if (DeskTheme.inputMode == InputMode.Pointer) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyCap("Enter")
                    Text(stringResource(R.string.add_keys_save), style = type.labelSmall, color = colors.textTertiary)
                    Spacer(Modifier.width(12.dp))
                    KeyCap("Tab")
                    Text(stringResource(R.string.add_keys_next), style = type.labelSmall, color = colors.textTertiary)
                }
            }
            Spacer(Modifier.height(dimens.gapL))
        }
    }
}

/** The book the form edits and the list it is in now. */
class EditTarget(val owner: Owner, val book: com.hesaplarimdijital.kitapbulucu.model.Book)

private fun inList(resources: android.content.res.Resources, owner: Owner): String =
    resources.getString(if (owner == Owner.DAD) R.string.list_in_dad else R.string.list_in_me)

private fun toList(resources: android.content.res.Resources, owner: Owner): String =
    resources.getString(if (owner == Owner.DAD) R.string.list_to_dad else R.string.list_to_me)
