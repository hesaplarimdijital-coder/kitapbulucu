package com.hesaplarimdijital.kitapbulucu.ui.importing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.importing.MergePlan
import com.hesaplarimdijital.kitapbulucu.model.Book
import com.hesaplarimdijital.kitapbulucu.model.BookTable
import com.hesaplarimdijital.kitapbulucu.model.ColumnMapping
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.DocumentException
import com.hesaplarimdijital.kitapbulucu.store.DocumentFailure
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.store.LoadFailure
import com.hesaplarimdijital.kitapbulucu.ui.CheckRow
import com.hesaplarimdijital.kitapbulucu.ui.ConfirmDialog
import com.hesaplarimdijital.kitapbulucu.ui.DialogAction
import com.hesaplarimdijital.kitapbulucu.ui.DropdownChoice
import com.hesaplarimdijital.kitapbulucu.ui.FilePicker
import com.hesaplarimdijital.kitapbulucu.ui.LibraryTexts
import com.hesaplarimdijital.kitapbulucu.ui.NoticeCard
import com.hesaplarimdijital.kitapbulucu.ui.setup.MappingEditor
import com.hesaplarimdijital.kitapbulucu.xlsx.SpreadsheetException
import com.hesaplarimdijital.kitapbulucu.xlsx.SpreadsheetReader
import com.hesaplarimdijital.kitapbulucu.xlsx.Workbook
import desktopkit.components.DeskButton
import desktopkit.components.SegmentedChoice
import desktopkit.components.SettingRow
import desktopkit.screens.PageHeader
import desktopkit.settings.SettingsSection
import desktopkit.theme.DeskTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A picked file as read: its name and sheets. */
class PickedFile(val name: String, val workbook: Workbook)

/**
 * "Excel birleştir" (S-23): another Excel or CSV file is read, cleaned with the first cleaning's
 * rules and compared with one list; the user sees new, changed and missing books and the pairs
 * only they can judge, ticks what to take, and nothing is written before "Uygula".
 * [initialFile] and [initialTarget] are for screenshots.
 */
@Composable
fun ImportScreen(
    store: LibraryStore,
    settings: AppSettings?,
    onClose: () -> Unit,
    initialFile: PickedFile? = null,
    initialTarget: Owner? = null,
) {
    val scope = rememberCoroutineScope()
    val states by store.states.collectAsState()
    val me = settings?.me
    var target by remember { mutableStateOf(initialTarget ?: me ?: Owner.DAD) }
    var file by remember { mutableStateOf(initialFile) }
    var reading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<LoadFailure?>(null) }
    var sheetName by remember { mutableStateOf<String?>(null) }
    var mapping by remember { mutableStateOf<ColumnMapping?>(null) }
    var editingMapping by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MergePlan.Result?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            reading = true
            failure = null
            try {
                val bytes = store.readDocument(uri)
                val name = store.documentName(uri) ?: uri.lastPathSegment.orEmpty()
                val workbook = withContext(Dispatchers.Default) { SpreadsheetReader.read(bytes, name) }
                if (workbook.filledSheets.isEmpty()) {
                    failure = LoadFailure.NOT_A_SPREADSHEET
                } else {
                    file = PickedFile(name, workbook)
                    sheetName = null
                    mapping = null
                    editingMapping = false
                }
            } catch (e: DocumentException) {
                failure = when (e.failure) {
                    DocumentFailure.NOT_FOUND -> LoadFailure.NOT_FOUND
                    DocumentFailure.NO_PERMISSION -> LoadFailure.NO_PERMISSION
                    DocumentFailure.TOO_LARGE -> LoadFailure.TOO_LARGE
                    DocumentFailure.IO -> LoadFailure.IO
                }
            } catch (e: SpreadsheetException) {
                failure = when (e.reason) {
                    SpreadsheetException.Reason.OLD_XLS -> LoadFailure.OLD_XLS
                    SpreadsheetException.Reason.NOT_A_SPREADSHEET -> LoadFailure.NOT_A_SPREADSHEET
                    SpreadsheetException.Reason.DAMAGED -> LoadFailure.DAMAGED
                    SpreadsheetException.Reason.TOO_LARGE -> LoadFailure.TOO_LARGE
                }
            } finally {
                reading = false
            }
        }
    }
    val pick = { launcher.launch(FilePicker.OPEN_TYPES) }

    val title = stringResource(R.string.import_title)
    val picked = file
    val done = result
    when {
        done != null -> Done(title, done, onClose)
        picked == null -> Start(title, target, { target = it }, reading, failure, pick, onClose)
        else -> {
            val sheet = picked.workbook.filledSheets.firstOrNull { it.name == sheetName } ?: picked.workbook.filledSheets.first()
            val sheetNames = picked.workbook.filledSheets.map { it.name }.takeIf { it.size > 1 }.orEmpty()
            val parsed = remember(sheet, mapping) { BookTable.parse(sheet, mapping) }
            if (parsed == null || editingMapping) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PageHeader(title, subtitle = picked.name)
                    Text(
                        stringResource(R.string.import_mapping_intro),
                        style = DeskTheme.type.body,
                        color = DeskTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = DeskTheme.dimens.pagePadding).widthIn(max = 760.dp),
                    )
                    MappingEditor(
                        sheet = sheet,
                        sheetNames = sheetNames,
                        initial = mapping?.takeIf { it.fits(sheet, BookTable.detectHeaderRow(sheet)) } ?: ColumnMapping.guess(sheet),
                        onChooseSheet = {
                            sheetName = it
                            mapping = null
                        },
                        onSave = {
                            mapping = it
                            editingMapping = false
                        },
                        onCancel = { if (parsed == null) onClose() else editingMapping = false },
                    )
                }
            } else {
                Review(
                    store = store,
                    title = title,
                    fileName = picked.name,
                    incoming = parsed.table,
                    target = target,
                    onTarget = { target = it },
                    targetTable = states.getValue(target).table,
                    me = me,
                    sheetNames = sheetNames,
                    sheetName = sheet.name,
                    onSheet = {
                        sheetName = it
                        mapping = null
                    },
                    onMapping = { editingMapping = true },
                    onOtherFile = pick,
                    onApplied = { result = it },
                    onClose = onClose,
                )
            }
        }
    }
}

@Composable
private fun Start(
    title: String,
    target: Owner,
    onTarget: (Owner) -> Unit,
    reading: Boolean,
    failure: LoadFailure?,
    onPick: () -> Unit,
    onClose: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(title)
        Text(
            stringResource(R.string.import_intro),
            style = DeskTheme.type.body,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = dimens.pagePadding).widthIn(max = 760.dp),
        )
        Spacer(Modifier.height(dimens.gapS))
        SettingsSection(stringResource(R.string.import_target_section)) {
            SettingRow(stringResource(R.string.import_target), subtitle = stringResource(R.string.import_target_text)) {
                SegmentedChoice(Owner.entries.map { it to LibraryTexts.ownerLabel(resources, it) }, target, onTarget)
            }
        }
        Column(
            Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapM).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(dimens.gapS),
        ) {
            if (failure != null) {
                NoticeCard(title = stringResource(R.string.import_failed), text = LibraryTexts.failure(resources, failure), tone = colors.danger) {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                DeskButton(
                    stringResource(if (reading) R.string.import_reading else R.string.import_pick),
                    onPick,
                    modifier = Modifier.focusRequester(focus),
                    icon = desktopkit.components.DeskIcons.Folder,
                    primary = true,
                    enabled = !reading,
                )
                DeskButton(stringResource(R.string.mapping_cancel), onClose)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Review(
    store: LibraryStore,
    title: String,
    fileName: String,
    incoming: BookTable,
    target: Owner,
    onTarget: (Owner) -> Unit,
    targetTable: BookTable?,
    me: Owner?,
    sheetNames: List<String>,
    sheetName: String,
    onSheet: (String) -> Unit,
    onMapping: () -> Unit,
    onOtherFile: () -> Unit,
    onApplied: (MergePlan.Result) -> Unit,
    onClose: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val colors = DeskTheme.colors
    val type = DeskTheme.type
    val dimens = DeskTheme.dimens
    var plan by remember { mutableStateOf<MergePlan.Plan?>(null) }
    // the plan is worked out once per file, sheet and list; a list that changes meanwhile is
    // picked up when the user returns, not while they are ticking rows
    val tableReady = targetTable != null
    LaunchedEffect(incoming, target, tableReady) {
        plan = null
        val table = targetTable ?: return@LaunchedEffect
        val decisions = store.mergeDecisions
        val work = { MergePlan.plan(target, table.books, MergePlan.fitTo(incoming, table), decisions) }
        // small comparisons are done at once; large ones away from the screen's thread
        plan = if (incoming.books.size + table.books.size <= QUICK_ROWS) work() else withContext(Dispatchers.Default) { work() }
    }
    var addedOff by remember(plan) { mutableStateOf(emptySet<Int>()) }
    var changedOff by remember(plan) { mutableStateOf(emptySet<Int>()) }
    var missingOn by remember(plan) { mutableStateOf(emptySet<Int>()) }
    var sameBook by remember(plan) { mutableStateOf(emptyMap<String, Boolean>()) }
    var confirmOther by remember { mutableStateOf(false) }

    val current = plan
    val outcome = current?.let { MergePlan.changes(it, MergePlan.Choices(addedOff, changedOff, missingOn, sameBook)) }
    val applyPlan = applyPlan@{
        val (changes, counts) = outcome ?: return@applyPlan
        store.mergeDecisions = store.mergeDecisions + sameBook
        store.submitAll(changes, ImportTexts.result(resources, counts))
        onApplied(counts)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                PageHeader(title, subtitle = stringResource(R.string.import_from, fileName, LibraryTexts.listName(resources, target)))
                SettingsSection(stringResource(R.string.import_file_section)) {
                    SettingRow(stringResource(R.string.import_target), subtitle = stringResource(R.string.import_target_text)) {
                        SegmentedChoice(Owner.entries.map { it to LibraryTexts.ownerLabel(resources, it) }, target, onTarget)
                    }
                    if (sheetNames.isNotEmpty()) {
                        SettingRow(stringResource(R.string.setup_sheet)) {
                            DropdownChoice(sheetNames.map { it to it }, sheetName, onSheet, modifier = Modifier.widthIn(min = 200.dp))
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                        DeskButton(stringResource(R.string.import_map_columns), onMapping)
                        DeskButton(stringResource(R.string.import_other_file), onOtherFile, icon = desktopkit.components.DeskIcons.Folder)
                    }
                }
            }
            when {
                targetTable == null -> item { Message(LibraryTexts.status(resources, store.state(target))) }
                current == null -> item { Message(stringResource(R.string.import_comparing)) }
                current.isEmpty -> item { Message(stringResource(R.string.import_nothing_new)) }
                else -> {
                    item {
                        Message(ImportTexts.summary(resources, current))
                    }
                    if (current.ambiguous.isNotEmpty()) {
                        section(resources.getString(R.string.import_questions, current.ambiguous.size), resources.getString(R.string.import_questions_text))
                        items(current.ambiguous.size, key = { "q$it" }) { index ->
                            val pair = current.ambiguous[index]
                            Question(pair, sameBook[pair.key]) { same -> sameBook = sameBook + (pair.key to same) }
                        }
                    }
                    if (current.added.isNotEmpty()) {
                        section(resources.getString(R.string.import_added, current.added.size), null) {
                            SelectAll(current.added.size - addedOff.size, current.added.size) { on ->
                                addedOff = if (on) emptySet() else current.added.indices.toSet()
                            }
                        }
                        items(current.added.size, key = { "a$it" }) { index ->
                            val book = current.added[index]
                            CheckRow(index !in addedOff, { on -> addedOff = if (on) addedOff - index else addedOff + index }, Modifier.padding(horizontal = dimens.pagePadding - dimens.gapS)) {
                                BookLine(book)
                            }
                        }
                    }
                    if (current.changed.isNotEmpty()) {
                        section(resources.getString(R.string.import_changed, current.changed.size), resources.getString(R.string.import_changed_text)) {
                            SelectAll(current.changed.size - changedOff.size, current.changed.size) { on ->
                                changedOff = if (on) emptySet() else current.changed.indices.toSet()
                            }
                        }
                        items(current.changed.size, key = { "c$it" }) { index ->
                            val change = current.changed[index]
                            CheckRow(index !in changedOff, { on -> changedOff = if (on) changedOff - index else changedOff + index }, Modifier.padding(horizontal = dimens.pagePadding - dimens.gapS)) {
                                Column {
                                    BookLine(change.existing)
                                    for (field in change.fields) {
                                        Text(
                                            ImportTexts.field(resources, field),
                                            style = type.labelSmall,
                                            color = colors.textSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (current.missing.isNotEmpty()) {
                        section(resources.getString(R.string.import_missing, current.missing.size), resources.getString(R.string.import_missing_text)) {
                            SelectAll(missingOn.size, current.missing.size) { on ->
                                missingOn = if (on) current.missing.indices.toSet() else emptySet()
                            }
                        }
                        items(current.missing.size, key = { "m$it" }) { index ->
                            val book = current.missing[index]
                            CheckRow(index in missingOn, { on -> missingOn = if (on) missingOn + index else missingOn - index }, Modifier.padding(horizontal = dimens.pagePadding - dimens.gapS)) {
                                BookLine(book)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(dimens.gapL)) }
                }
            }
        }
        // the decision stays in sight however long the lists are
        Column(
            Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapS),
            verticalArrangement = Arrangement.spacedBy(dimens.gapS),
        ) {
            val counts = outcome?.second
            val line = when {
                current == null || current.isEmpty -> null
                counts == null -> stringResource(R.string.import_answer_first)
                else -> ImportTexts.preview(resources, counts)
            }
            if (line != null) Text(line, style = type.body, color = if (counts == null) colors.warning else colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalAlignment = Alignment.CenterVertically) {
                DeskButton(
                    stringResource(R.string.import_apply),
                    { if (me != null && target != me) confirmOther = true else applyPlan() },
                    primary = true,
                    enabled = outcome != null && outcome.first.isNotEmpty(),
                )
                DeskButton(stringResource(R.string.mapping_cancel), onClose)
            }
        }
    }
    if (confirmOther) {
        ConfirmDialog(
            title = stringResource(R.string.import_other_title),
            text = stringResource(R.string.import_other_text, LibraryTexts.listName(resources, target)),
            actions = listOf(
                DialogAction(stringResource(R.string.import_apply), primary = true) { applyPlan() },
                DialogAction(stringResource(R.string.mapping_cancel)) {},
            ),
            onDismiss = { confirmOther = false },
        )
    }
}

private fun LazyListScope.section(title: String, text: String?, trailing: (@Composable () -> Unit)? = null) {
    item(key = "s:$title") {
        val dimens = DeskTheme.dimens
        Column(Modifier.fillMaxWidth().padding(start = dimens.pagePadding, end = dimens.pagePadding, top = dimens.gapL, bottom = dimens.gapS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = DeskTheme.type.title, color = DeskTheme.colors.text, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            if (text != null) Text(text, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun SelectAll(selected: Int, total: Int, onSet: (Boolean) -> Unit) {
    val all = selected == total
    DeskButton(stringResource(if (all) R.string.import_select_none else R.string.import_select_all), { onSet(!all) })
}

@Composable
private fun BookLine(book: Book) {
    val colors = DeskTheme.colors
    val type = DeskTheme.type
    val resources = LocalContext.current.resources
    Column {
        Text(book.title, style = type.bodyStrong, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val details = ImportTexts.details(resources, book)
        if (details.isNotEmpty()) Text(details, style = type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Question(pair: MergePlan.Ambiguous, answer: Boolean?, onAnswer: (Boolean) -> Unit) {
    val colors = DeskTheme.colors
    val type = DeskTheme.type
    val dimens = DeskTheme.dimens
    val resources = LocalContext.current.resources
    Column(
        Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        verticalArrangement = Arrangement.spacedBy(dimens.gapXs),
    ) {
        Text(stringResource(R.string.import_in_file, ImportTexts.short(resources, pair.incoming)), style = type.body, color = colors.text)
        Text(stringResource(R.string.import_in_list, ImportTexts.short(resources, pair.existing)), style = type.body, color = colors.text)
        SegmentedChoice(
            options = listOf<Pair<Boolean?, String>>(true to stringResource(R.string.import_same_book), false to stringResource(R.string.import_different_book)),
            selected = answer,
            onSelect = { if (it != null) onAnswer(it) },
        )
    }
}

@Composable
private fun Done(title: String, result: MergePlan.Result, onClose: () -> Unit) {
    val resources = LocalContext.current.resources
    val dimens = DeskTheme.dimens
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        PageHeader(title)
        Column(Modifier.padding(horizontal = dimens.pagePadding), verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
            Text(ImportTexts.result(resources, result), style = DeskTheme.type.title, color = DeskTheme.colors.text)
            Text(stringResource(R.string.import_done_text), style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary)
            DeskButton(stringResource(R.string.import_close), onClose, modifier = Modifier.focusRequester(focus), primary = true)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = DeskTheme.type.body,
        color = DeskTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = DeskTheme.dimens.pagePadding, vertical = DeskTheme.dimens.gapM).widthIn(max = 760.dp),
    )
}

private const val QUICK_ROWS = 2_000
